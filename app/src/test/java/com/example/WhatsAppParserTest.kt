package com.example

import android.app.Notification
import com.example.data.repository.MessageTypes
import com.example.service.RawNotificationPayload
import com.example.service.WhatsAppNotificationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppParserTest {

    @Test
    fun `ignores non-WhatsApp packages`() {
        val result = WhatsAppNotificationParser.parse(
            RawNotificationPayload(
                packageName = "com.android.messaging",
                notificationKey = "key1",
                postTime = 1000L,
                flags = 0,
                title = "Alice",
                text = "Hello",
                bigText = null,
                conversationTitle = null,
                isGroupConversation = false,
                styleMessages = emptyList(),
            )
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `ignores group summary and background sync notifications`() {
        val summary = WhatsAppNotificationParser.parse(
            RawNotificationPayload(
                packageName = "com.whatsapp",
                notificationKey = "summary",
                postTime = 1000L,
                flags = Notification.FLAG_GROUP_SUMMARY,
                title = "WhatsApp",
                text = "4 messages from 2 chats",
                bigText = null,
                conversationTitle = null,
                isGroupConversation = false,
                styleMessages = emptyList(),
            )
        )
        val checking = WhatsAppNotificationParser.parse(
            RawNotificationPayload(
                packageName = "com.whatsapp",
                notificationKey = "sync",
                postTime = 1000L,
                flags = 0,
                title = "WhatsApp",
                text = "Checking for new messages",
                bigText = null,
                conversationTitle = null,
                isGroupConversation = false,
                styleMessages = emptyList(),
            )
        )
        assertTrue(summary.isEmpty())
        assertTrue(checking.isEmpty())
    }

    @Test
    fun `extracts MessagingStyle group messages for WhatsApp Business`() {
        val result = WhatsAppNotificationParser.parse(
            RawNotificationPayload(
                packageName = "com.whatsapp.w4b",
                notificationKey = "w4b_group",
                postTime = 2000L,
                flags = 0,
                title = "Project Team",
                text = null,
                bigText = null,
                conversationTitle = "Project Team (2 messages)",
                isGroupConversation = true,
                styleMessages = listOf(
                    RawNotificationPayload.StyleMessageItem("Rohan", "Build passed", 1900L),
                    RawNotificationPayload.StyleMessageItem("Zara", "Deploying now", 2000L),
                ),
            )
        )

        assertEquals(2, result.size)
        assertEquals("Project Team", result[0].chatTitle)
        assertEquals("WA Business", result[0].appLabel)
        assertTrue(result[0].isGroup)
        assertEquals("Rohan", result[0].senderName)
        assertEquals("Zara", result[1].senderName)
    }

    @Test
    fun `extracts WhatsApp Business InboxStyle textLines and voice messages`() {
        val result = WhatsAppNotificationParser.parse(
            RawNotificationPayload(
                packageName = "com.whatsapp.w4b",
                notificationKey = "w4b_summary",
                postTime = 4000L,
                flags = Notification.FLAG_GROUP_SUMMARY,
                title = "WhatsApp Business",
                text = "2 new messages",
                bigText = null,
                conversationTitle = null,
                isGroupConversation = false,
                styleMessages = emptyList(),
                textLines = listOf(
                    "Bilal: Salam, order ready?",
                    "Bilal: 🎤 Voice message (0:14)",
                ),
            )
        )

        assertEquals(2, result.size)
        assertEquals("WA Business", result[0].appLabel)
        assertEquals("Bilal", result[0].chatTitle)
        assertEquals("Salam, order ready?", result[0].body)
        assertEquals(MessageTypes.VOICE, result[1].messageType)
        assertEquals("0:14", result[1].mediaDuration)
    }

    @Test
    fun `classifies audio mime attachment with empty text as voice message`() {
        val result = WhatsAppNotificationParser.parse(
            RawNotificationPayload(
                packageName = "com.whatsapp",
                notificationKey = "voice_mime",
                postTime = 5000L,
                flags = 0,
                title = "Hina",
                text = null,
                bigText = null,
                conversationTitle = null,
                isGroupConversation = false,
                styleMessages = listOf(
                    RawNotificationPayload.StyleMessageItem(
                        sender = "Hina",
                        text = "Voice message",
                        timestamp = 5000L,
                        dataMimeType = "audio/ogg; codecs=opus",
                    )
                ),
            )
        )

        assertEquals(1, result.size)
        assertEquals(MessageTypes.VOICE, result.first().messageType)
    }

    @Test
    fun `detects deleted WhatsApp notification without overwriting messages`() {
        val events = WhatsAppNotificationParser.parseEvents(
            RawNotificationPayload(
                packageName = "com.whatsapp",
                notificationKey = "0|com.whatsapp|1|null|1001",
                postTime = 3000L,
                flags = 0,
                title = "Aarav Sharma",
                text = "This message was deleted",
                bigText = null,
                conversationTitle = null,
                isGroupConversation = false,
                styleMessages = emptyList(),
            )
        )

        assertTrue(events.messages.isEmpty())
        assertEquals(1, events.deletedSignals.size)
        assertEquals("com.whatsapp|aarav sharma", events.deletedSignals.first().chatId)
    }

    @Test
    fun `detects View Once photo and video markers accurately`() {
        val photoResult = WhatsAppNotificationParser.parse(
            RawNotificationPayload(
                packageName = "com.whatsapp",
                notificationKey = "view_once_photo",
                postTime = 6000L,
                flags = 0,
                title = "Sana",
                text = "📷 View once photo",
                bigText = null,
                conversationTitle = null,
                isGroupConversation = false,
                styleMessages = emptyList(),
            )
        )
        assertEquals(1, photoResult.size)
        assertEquals(MessageTypes.IMAGE, photoResult.first().messageType)
        assertTrue(photoResult.first().isViewOnce)

        val videoResult = WhatsAppNotificationParser.parse(
            RawNotificationPayload(
                packageName = "com.whatsapp.w4b",
                notificationKey = "view_once_video",
                postTime = 7000L,
                flags = 0,
                title = "Sana",
                text = "1️⃣ Video (0:08)",
                bigText = null,
                conversationTitle = null,
                isGroupConversation = false,
                styleMessages = emptyList(),
            )
        )
        assertEquals(1, videoResult.size)
        assertEquals(MessageTypes.VIDEO, videoResult.first().messageType)
        assertEquals("0:08", videoResult.first().mediaDuration)
        assertTrue(videoResult.first().isViewOnce)
    }
}
