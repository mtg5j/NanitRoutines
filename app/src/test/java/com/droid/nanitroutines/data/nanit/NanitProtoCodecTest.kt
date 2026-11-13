package com.droid.nanitroutines.data.nanit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NanitProtoCodecTest {

    @Test
    fun `encodeCameraKeepalive encodes message type 0`() {
        val bytes = NanitProtoCodec.encodeCameraKeepalive()
        val decoded = NanitProtoCodec.decodeCameraFrame(bytes)
        assertEquals(NanitProtoCodec.CAM_MSG_KEEPALIVE, decoded.messageType)
    }

    @Test
    fun `encodeCameraPutSettings encodes sleepMode false for Naptime Camera ON`() {
        val bytes = NanitProtoCodec.encodeCameraPutSettings(
            requestId = 42,
            sleepMode = false,
            statusLightOn = false
        )
        val decoded = NanitProtoCodec.decodeCameraFrame(bytes)
        assertEquals(NanitProtoCodec.CAM_MSG_REQUEST, decoded.messageType)
        assertEquals(42, decoded.requestId)
        assertEquals(NanitProtoCodec.CAM_REQ_PUT_SETTINGS, decoded.requestType)
        assertEquals(false, decoded.sleepMode)
        assertEquals(false, decoded.statusLightOn)
    }

    @Test
    fun `encodeCameraPutSettings encodes sleepMode true for Wakeup Camera OFF`() {
        val bytes = NanitProtoCodec.encodeCameraPutSettings(
            requestId = 43,
            sleepMode = true
        )
        val decoded = NanitProtoCodec.decodeCameraFrame(bytes)
        assertEquals(NanitProtoCodec.CAM_MSG_REQUEST, decoded.messageType)
        assertEquals(43, decoded.requestId)
        assertEquals(NanitProtoCodec.CAM_REQ_PUT_SETTINGS, decoded.requestType)
        assertEquals(true, decoded.sleepMode)
    }

    @Test
    fun `encodeCameraPutControl encodes nightLight toggle`() {
        val bytesOn = NanitProtoCodec.encodeCameraPutControl(
            requestId = 55,
            nightLightOn = true,
            timeoutSeconds = 1800
        )
        val decodedOn = NanitProtoCodec.decodeCameraFrame(bytesOn)
        assertEquals(NanitProtoCodec.CAM_MSG_REQUEST, decodedOn.messageType)
        assertEquals(55, decodedOn.requestId)
        assertEquals(NanitProtoCodec.CAM_REQ_PUT_CONTROL, decodedOn.requestType)
        assertEquals(true, decodedOn.nightLightOn)
    }

    @Test
    fun `encodeSoundLightPutSettings encodes Naptime Rain sound and warm light`() {
        val payload = SoundLightSettingsPayload(
            isOn = true,
            noSound = false,
            track = "Rain",
            volume = 0.45f,
            brightness = 0.20f,
            noColor = false,
            hue = 0.08f,
            saturation = 0.75f
        )
        val bytes = NanitProtoCodec.encodeSoundLightPutSettings(
            requestId = 7,
            payload = payload,
            sessionId = "sess-nap-1"
        )
        val decoded = NanitProtoCodec.decodeSoundLightFrame(bytes)
        assertEquals(7, decoded.requestId)
        val s = decoded.settings
        assertNotNull(s)
        assertEquals(true, s!!.isOn)
        assertEquals(false, s.noSound)
        assertEquals("Rain", s.track)
        assertEquals(0.45f, s.volume!!, 0.001f)
        assertEquals(0.20f, s.brightness!!, 0.001f)
        assertEquals(false, s.noColor)
        assertEquals(0.08f, s.hue!!, 0.001f)
        assertEquals(0.75f, s.saturation!!, 0.001f)
    }

    @Test
    fun `encodeSoundLightPutSettings encodes Wakeup power off`() {
        val payload = SoundLightSettingsPayload(
            isOn = false,
            noSound = true,
            brightness = 0.0f
        )
        val bytes = NanitProtoCodec.encodeSoundLightPutSettings(
            requestId = 8,
            payload = payload
        )
        val decoded = NanitProtoCodec.decodeSoundLightFrame(bytes)
        assertEquals(8, decoded.requestId)
        val s = decoded.settings
        assertNotNull(s)
        assertFalse(s!!.isOn ?: true)
        assertTrue(s.noSound ?: false)
        assertEquals(0.0f, s.brightness ?: 1.0f, 0.001f)
    }
}
