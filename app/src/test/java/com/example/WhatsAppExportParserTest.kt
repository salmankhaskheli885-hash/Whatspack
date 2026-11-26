package com.example

import com.example.data.repository.MessageTypes
import com.example.service.WhatsAppExportParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppExportParserTest {

    @Test
    fun `parses Android and iOS exported WhatsApp chats with attachments and multi-line text`() {
        val sampleExport = """
            26/11/26, 12:30 pm - Messages and calls are end-to-end encrypted. No one outside of this chat, not even WhatsApp, can read or listen to them.
            26/11/26, 12:31 pm - Salman: Salam bhai
            26/11/26, 12:31 pm - Salman: Ok
            26/11/26, 12:31 pm - Salman: Ok
            26/11/26, 12:32 pm - Ayesha: Line one
            Line two continuation
            [26/11/26, 12:33:10 PM] Ayesha: <attached: PTT-20261126-WA0001.opus>
            26/11/26, 12:34 pm - Salman: IMG-20261126-WA0002.jpg (file attached)
            26/11/26, 12:35 pm - Ayesha: This message was deleted
        """.trimIndent()

        val parsed = WhatsAppExportParser.parseExportText(
            rawText = sampleExport,
            chatTitle = WhatsAppExportParser.inferChatTitleFromFileName("WhatsApp Chat with Ayesha.txt"),
            attachedMediaByName = mapOf(
                "ptt-20261126-wa0001.opus" to "/tmp/ptt.opus",
                "img-20261126-wa0002.jpg" to "/tmp/img.jpg",
            ),
        )

        assertEquals(7, parsed.size)
        assertEquals("Ayesha", parsed.first().chatTitle)
        assertEquals("Salam bhai", parsed[0].body)
        assertEquals("Ok", parsed[1].body)
        assertEquals("Ok", parsed[2].body)
        assertTrue(parsed[3].body.contains("Line two continuation"))

        assertEquals(MessageTypes.VOICE, parsed[4].messageType)
        assertEquals("/tmp/ptt.opus", parsed[4].mediaFilePath)

        assertEquals(MessageTypes.IMAGE, parsed[5].messageType)
        assertEquals("/tmp/img.jpg", parsed[5].mediaFilePath)

        assertEquals("Deleted message recorded in export", parsed[6].body)
    }
}
