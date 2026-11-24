package com.furina.classtools.features.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadParserTest {

    private val ticket = "0123456789abcdef0123456789abcdef"

    @Test
    fun `generic login qr`() {
        val payload = QrPayloadParser.parse("https://tool.furina.fun/#qr=$ticket")
        assertEquals(QrPayload.Login(ticket, harmony = false), payload)
    }

    @Test
    fun `harmony login qr`() {
        val payload = QrPayloadParser.parse("https://tool.furina.fun/#qr=$ticket&p=harmony")
        assertEquals(QrPayload.Login(ticket, harmony = true), payload)
    }

    @Test
    fun `legacy oppo label is treated as generic`() {
        val payload = QrPayloadParser.parse("https://tool.furina.fun/?qr=$ticket&p=oppo")
        assertEquals(QrPayload.Login(ticket, harmony = false), payload)
    }

    @Test
    fun `bare ticket`() {
        assertEquals(QrPayload.Login(ticket, harmony = false), QrPayloadParser.parse("  $ticket  "))
    }

    @Test
    fun `draw verify hash url`() {
        assertEquals(
            QrPayload.DrawVerify("dabc123456"),
            QrPayloadParser.parse("https://tool.furina.fun/#verify=dabc123456"),
        )
    }

    @Test
    fun `draw verify query with extra params`() {
        assertEquals(
            QrPayload.DrawVerify("dabc123456"),
            QrPayloadParser.parse("https://tool.furina.fun/?verify=dABC123456&from=qr"),
        )
    }

    @Test
    fun `unknown content is not misclassified`() {
        assertTrue(QrPayloadParser.parse("https://example.com/hello") is QrPayload.Unknown)
        assertTrue(QrPayloadParser.parse("#qr=not-hex") is QrPayload.Unknown)
        assertTrue(QrPayloadParser.parse("#verify=d-short") is QrPayload.Unknown)
        assertTrue(QrPayloadParser.parse("") is QrPayload.Unknown)
    }
}
