package com.example

import android.app.Notification
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
}
