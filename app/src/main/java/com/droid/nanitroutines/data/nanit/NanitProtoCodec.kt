package com.droid.nanitroutines.data.nanit

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DecodedCameraFrame(
    val messageType: Int,
    val requestId: Int? = null,
    val requestType: Int? = null,
    val statusCode: Int? = null,
    val statusMessage: String? = null,
    val sleepMode: Boolean? = null,
    val nightVision: Boolean? = null,
    val volume: Int? = null,
    val statusLightOn: Boolean? = null,
    val nightLightOn: Boolean? = null,
    val temperatureMilli: Int? = null,
    val humidityMilli: Int? = null
)

data class SoundLightSettingsPayload(
    val isOn: Boolean? = null,
    val noSound: Boolean? = null,
    val track: String? = null,
    val volume: Float? = null,
    val brightness: Float? = null,
    val noColor: Boolean? = null,
    val hue: Float? = null,
    val saturation: Float? = null
)

data class DecodedSoundLightFrame(
    val requestId: Int? = null,
    val statusCode: Int? = null,
    val statusMessage: String? = null,
    val backendDeviceConnected: Boolean? = null,
    val settings: SoundLightSettingsPayload? = null,
    val temperature: Float? = null,
    val humidity: Float? = null
)

/**
 * Pure-Kotlin binary Protobuf wire encoder/decoder for:
 * 1. Nanit Pro Camera (`websocket.proto`)
 * 2. Nanit Sound + Light Machine (`sound_light.proto`)
 */
object NanitProtoCodec {
    // Wire types
    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LEN = 2
    private const val WIRE_FIXED32 = 5

    // Camera Message.Type
    const val CAM_MSG_KEEPALIVE = 0
    const val CAM_MSG_REQUEST = 1
    const val CAM_MSG_RESPONSE = 2

    // Camera RequestType
    const val CAM_REQ_GET_SETTINGS = 4
    const val CAM_REQ_PUT_SETTINGS = 5
    const val CAM_REQ_GET_CONTROL = 6
    const val CAM_REQ_PUT_CONTROL = 7
    const val CAM_REQ_GET_STATUS = 8
    const val CAM_REQ_GET_SENSOR_DATA = 12

    // Camera SensorType
    private const val SENSOR_TEMPERATURE = 2
    private const val SENSOR_HUMIDITY = 3

    //Low-level Protobuf writers
    private fun ByteArrayOutputStream.writeVarint(value: Long) {
        var v = value
        while (true) {
            if ((v and 0x7FL.inv()) == 0L) {
                write(v.toInt())
                return
            } else {
                write(((v.toInt() and 0x7F) or 0x80))
                v = v ushr 7
            }
        }
    }

    private fun ByteArrayOutputStream.writeTag(fieldNumber: Int, wireType: Int) {
        writeVarint(((fieldNumber shl 3) or wireType).toLong())
    }

    private fun ByteArrayOutputStream.writeInt32Field(fieldNumber: Int, value: Int) {
        writeTag(fieldNumber, WIRE_VARINT)
        writeVarint(value.toLong())
    }

    private fun ByteArrayOutputStream.writeBoolField(fieldNumber: Int, value: Boolean) {
        writeTag(fieldNumber, WIRE_VARINT)
        writeVarint(if (value) 1L else 0L)
    }

    private fun ByteArrayOutputStream.writeFloatField(fieldNumber: Int, value: Float) {
        writeTag(fieldNumber, WIRE_FIXED32)
        val bits = java.lang.Float.floatToRawIntBits(value)
        write(bits and 0xFF)
        write((bits ushr 8) and 0xFF)
        write((bits ushr 16) and 0xFF)
        write((bits ushr 24) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeBytesField(fieldNumber: Int, payload: ByteArray) {
        writeTag(fieldNumber, WIRE_LEN)
        writeVarint(payload.size.toLong())
        write(payload)
    }

    private fun ByteArrayOutputStream.writeStringField(fieldNumber: Int, text: String) {
        writeBytesField(fieldNumber, text.encodeToByteArray())
    }

    // =========================================================================
    // 1. Nanit Pro Camera Encoders (`websocket.proto`)
    // =========================================================================

    fun encodeCameraKeepalive(): ByteArray {
        val msg = ByteArrayOutputStream()
        msg.writeInt32Field(1, CAM_MSG_KEEPALIVE)
        return msg.toByteArray()
    }

    fun encodeCameraPutSettings(
        requestId: Int,
        sleepMode: Boolean? = null,
        statusLightOn: Boolean? = null,
        volume: Int? = null,
        micMuteOn: Boolean? = null
    ): ByteArray {
        val settingsOut = ByteArrayOutputStream()
        if (volume != null) settingsOut.writeInt32Field(9, volume)
        if (sleepMode != null) settingsOut.writeBoolField(11, sleepMode)
        if (statusLightOn != null) settingsOut.writeBoolField(12, statusLightOn)
        if (micMuteOn != null) settingsOut.writeBoolField(20, micMuteOn)

        val reqOut = ByteArrayOutputStream()
        reqOut.writeInt32Field(1, requestId)
        reqOut.writeInt32Field(2, CAM_REQ_PUT_SETTINGS)
        reqOut.writeBytesField(5, settingsOut.toByteArray())

        val msgOut = ByteArrayOutputStream()
        msgOut.writeInt32Field(1, CAM_MSG_REQUEST)
        msgOut.writeBytesField(2, reqOut.toByteArray())
        return msgOut.toByteArray()
    }

    fun encodeCameraGetSettings(requestId: Int): ByteArray {
        val reqOut = ByteArrayOutputStream()
        reqOut.writeInt32Field(1, requestId)
        reqOut.writeInt32Field(2, CAM_REQ_GET_SETTINGS)

        val msgOut = ByteArrayOutputStream()
        msgOut.writeInt32Field(1, CAM_MSG_REQUEST)
        msgOut.writeBytesField(2, reqOut.toByteArray())
        return msgOut.toByteArray()
    }

    fun encodeCameraPutControl(
        requestId: Int,
        nightLightOn: Boolean,
        timeoutSeconds: Int? = null
    ): ByteArray {
        val ctrlOut = ByteArrayOutputStream()
        // Control.nightLight = 3 (0 = LIGHT_OFF, 1 = LIGHT_ON)
        ctrlOut.writeInt32Field(3, if (nightLightOn) 1 else 0)
        if (timeoutSeconds != null) {
            ctrlOut.writeInt32Field(6, timeoutSeconds)
        }

        val reqOut = ByteArrayOutputStream()
        reqOut.writeInt32Field(1, requestId)
        reqOut.writeInt32Field(2, CAM_REQ_PUT_CONTROL)
        reqOut.writeBytesField(15, ctrlOut.toByteArray())

        val msgOut = ByteArrayOutputStream()
        msgOut.writeInt32Field(1, CAM_MSG_REQUEST)
        msgOut.writeBytesField(2, reqOut.toByteArray())
        return msgOut.toByteArray()
    }

    fun encodeCameraGetSensorData(requestId: Int): ByteArray {
        val getSensorsOut = ByteArrayOutputStream()
        getSensorsOut.writeBoolField(1, true) // all = true

        val reqOut = ByteArrayOutputStream()
        reqOut.writeInt32Field(1, requestId)
        reqOut.writeInt32Field(2, CAM_REQ_GET_SENSOR_DATA)
        reqOut.writeBytesField(12, getSensorsOut.toByteArray())

        val msgOut = ByteArrayOutputStream()
        msgOut.writeInt32Field(1, CAM_MSG_REQUEST)
        msgOut.writeBytesField(2, reqOut.toByteArray())
        return msgOut.toByteArray()
    }

    fun decodeCameraFrame(bytes: ByteArray): DecodedCameraFrame {
        var msgType = CAM_MSG_KEEPALIVE
        var requestId: Int? = null
        var requestType: Int? = null
        var statusCode: Int? = null
        var statusMessage: String? = null
        var sleepMode: Boolean? = null
        var nightVision: Boolean? = null
        var volume: Int? = null
        var statusLightOn: Boolean? = null
        var nightLightOn: Boolean? = null
        var temperatureMilli: Int? = null
        var humidityMilli: Int? = null

        fun parseCameraSettings(buf: ByteArray) {
            forEachField(buf) { field, wire, value ->
                if (wire == WIRE_VARINT) {
                    val v = (value as Long).toInt()
                    when (field) {
                        2 -> nightVision = (v != 0)
                        9 -> volume = v
                        11 -> sleepMode = (v != 0)
                        12 -> statusLightOn = (v != 0)
                    }
                }
            }
        }

        fun parseCameraControl(buf: ByteArray) {
            forEachField(buf) { field, wire, value ->
                if (field == 3 && wire == WIRE_VARINT) {
                    nightLightOn = ((value as Long).toInt() == 1)
                }
            }
        }

        fun parseSensorData(buf: ByteArray) {
            var sensorType = -1
            var valueInt: Int? = null
            var valueMilli: Int? = null
            forEachField(buf) { field, wire, value ->
                if (wire == WIRE_VARINT) {
                    val v = (value as Long).toInt()
                    when (field) {
                        1 -> sensorType = v
                        3 -> valueInt = v
                        6 -> valueMilli = v
                    }
                }
            }
            val resolvedMilli = valueMilli ?: valueInt?.let { it * 1000 }
            if (sensorType == SENSOR_TEMPERATURE && resolvedMilli != null) {
                temperatureMilli = resolvedMilli
            } else if (sensorType == SENSOR_HUMIDITY && resolvedMilli != null) {
                humidityMilli = resolvedMilli
            }
        }

        forEachField(bytes) { field, wire, value ->
            when {
                field == 1 && wire == WIRE_VARINT -> {
                    msgType = (value as Long).toInt()
                }
                field == 2 && wire == WIRE_LEN -> {
                    // Request
                    val reqBytes = value as ByteArray
                    forEachField(reqBytes) { rf, rw, rv ->
                        when {
                            rf == 1 && rw == WIRE_VARINT -> requestId = (rv as Long).toInt()
                            rf == 2 && rw == WIRE_VARINT -> requestType = (rv as Long).toInt()
                            rf == 5 && rw == WIRE_LEN -> parseCameraSettings(rv as ByteArray)
                            rf == 15 && rw == WIRE_LEN -> parseCameraControl(rv as ByteArray)
                        }
                    }
                }
                field == 3 && wire == WIRE_LEN -> {
                    // Response
                    val respBytes = value as ByteArray
                    forEachField(respBytes) { rf, rw, rv ->
                        when {
                            rf == 1 && rw == WIRE_VARINT -> requestId = (rv as Long).toInt()
                            rf == 2 && rw == WIRE_VARINT -> requestType = (rv as Long).toInt()
                            rf == 3 && rw == WIRE_VARINT -> statusCode = (rv as Long).toInt()
                            rf == 4 && rw == WIRE_LEN -> statusMessage = (rv as ByteArray).decodeToString()
                            rf == 6 && rw == WIRE_LEN -> parseCameraSettings(rv as ByteArray)
                            rf == 9 && rw == WIRE_LEN -> parseSensorData(rv as ByteArray)
                            rf == 13 && rw == WIRE_LEN -> parseCameraControl(rv as ByteArray)
                        }
                    }
                }
            }
        }

        return DecodedCameraFrame(
            messageType = msgType,
            requestId = requestId,
            requestType = requestType,
            statusCode = statusCode,
            statusMessage = statusMessage,
            sleepMode = sleepMode,
            nightVision = nightVision,
            volume = volume,
            statusLightOn = statusLightOn,
            nightLightOn = nightLightOn,
            temperatureMilli = temperatureMilli,
            humidityMilli = humidityMilli
        )
    }

    // =========================================================================
    // 2. Nanit Sound + Light Machine Encoders/Decoders (`sound_light.proto`)
    // =========================================================================

    fun encodeSoundLightGetSettings(requestId: Int, sessionId: String? = null): ByteArray {
        val getSettingsOut = ByteArrayOutputStream()
        getSettingsOut.writeBoolField(1, true)  // all = true
        getSettingsOut.writeBoolField(7, true)  // savedSounds = true
        getSettingsOut.writeBoolField(8, true)  // temperature = true
        getSettingsOut.writeBoolField(9, true)  // humidity = true
        getSettingsOut.writeBoolField(12, true) // clock = true

        val reqOut = ByteArrayOutputStream()
        reqOut.writeInt32Field(1, requestId)
        reqOut.writeBytesField(5, getSettingsOut.toByteArray())
        if (!sessionId.isNullOrBlank()) {
            reqOut.writeStringField(200, sessionId)
        }

        val msgOut = ByteArrayOutputStream()
        msgOut.writeBytesField(1, reqOut.toByteArray())
        return msgOut.toByteArray()
    }

    fun encodeSoundLightPutSettings(
        requestId: Int,
        payload: SoundLightSettingsPayload,
        sessionId: String? = null
    ): ByteArray {
        val settingsOut = ByteArrayOutputStream()

        // 1: optional float brightness = 1
        if (payload.brightness != null) {
            settingsOut.writeFloatField(1, payload.brightness.coerceIn(0f, 1f))
        }

        // 2: optional Color color = 2
        if (payload.noColor != null || payload.hue != null || payload.saturation != null) {
            val colorOut = ByteArrayOutputStream()
            if (payload.noColor != null) colorOut.writeBoolField(1, payload.noColor)
            if (payload.hue != null) colorOut.writeFloatField(2, payload.hue.coerceIn(0f, 1f))
            if (payload.saturation != null) colorOut.writeFloatField(3, payload.saturation.coerceIn(0f, 1f))
            settingsOut.writeBytesField(2, colorOut.toByteArray())
        }

        // 3: optional float volume = 3
        if (payload.volume != null) {
            settingsOut.writeFloatField(3, payload.volume.coerceIn(0f, 1f))
        }

        // 4: optional Sound sound = 4
        if (payload.noSound != null || payload.track != null) {
            val soundOut = ByteArrayOutputStream()
            if (payload.noSound != null) soundOut.writeBoolField(1, payload.noSound)
            if (!payload.track.isNullOrBlank()) soundOut.writeStringField(2, payload.track)
            settingsOut.writeBytesField(4, soundOut.toByteArray())
        }

        // 5: optional bool isOn = 5
        if (payload.isOn != null) {
            settingsOut.writeBoolField(5, payload.isOn)
        }

        val reqOut = ByteArrayOutputStream()
        reqOut.writeInt32Field(1, requestId)
        reqOut.writeBytesField(6, settingsOut.toByteArray())
        if (!sessionId.isNullOrBlank()) {
            reqOut.writeStringField(200, sessionId)
        }

        val msgOut = ByteArrayOutputStream()
        msgOut.writeBytesField(1, reqOut.toByteArray())
        return msgOut.toByteArray()
    }

    fun decodeSoundLightFrame(bytes: ByteArray): DecodedSoundLightFrame {
        var requestId: Int? = null
        var statusCode: Int? = null
        var statusMessage: String? = null
        var backendDeviceConnected: Boolean? = null
        var parsedSettings: SoundLightSettingsPayload? = null
        var temperature: Float? = null
        var humidity: Float? = null

        fun parseSoundLightSettings(buf: ByteArray) {
            var isOn: Boolean? = null
            var noSound: Boolean? = null
            var track: String? = null
            var volume: Float? = null
            var brightness: Float? = null
            var noColor: Boolean? = null
            var hue: Float? = null
            var saturation: Float? = null

            forEachField(buf) { field, wire, value ->
                when {
                    field == 1 && wire == WIRE_FIXED32 -> brightness = value as Float
                    field == 2 && wire == WIRE_LEN -> {
                        forEachField(value as ByteArray) { cf, cw, cv ->
                            when {
                                cf == 1 && cw == WIRE_VARINT -> noColor = ((cv as Long) != 0L)
                                cf == 2 && cw == WIRE_FIXED32 -> hue = cv as Float
                                cf == 3 && cw == WIRE_FIXED32 -> saturation = cv as Float
                            }
                        }
                    }
                    field == 3 && wire == WIRE_FIXED32 -> volume = value as Float
                    field == 4 && wire == WIRE_LEN -> {
                        forEachField(value as ByteArray) { sf, sw, sv ->
                            when {
                                sf == 1 && sw == WIRE_VARINT -> noSound = ((sv as Long) != 0L)
                                sf == 2 && sw == WIRE_LEN -> track = (sv as ByteArray).decodeToString()
                            }
                        }
                    }
                    field == 5 && wire == WIRE_VARINT -> isOn = ((value as Long) != 0L)
                    field == 7 && wire == WIRE_FIXED32 -> temperature = value as Float
                    field == 8 && wire == WIRE_FIXED32 -> humidity = value as Float
                }
            }

            parsedSettings = SoundLightSettingsPayload(
                isOn = isOn,
                noSound = noSound,
                track = track,
                volume = volume,
                brightness = brightness,
                noColor = noColor,
                hue = hue,
                saturation = saturation
            )
        }

        forEachField(bytes) { field, wire, value ->
            when {
                field == 1 && wire == WIRE_LEN -> {
                    // Request
                    forEachField(value as ByteArray) { rf, rw, rv ->
                        when {
                            rf == 1 && rw == WIRE_VARINT -> requestId = (rv as Long).toInt()
                            rf == 6 && rw == WIRE_LEN -> parseSoundLightSettings(rv as ByteArray)
                        }
                    }
                }
                field == 2 && wire == WIRE_LEN -> {
                    // Response
                    forEachField(value as ByteArray) { rf, rw, rv ->
                        when {
                            rf == 1 && rw == WIRE_VARINT -> requestId = (rv as Long).toInt()
                            rf == 2 && rw == WIRE_VARINT -> statusCode = (rv as Long).toInt()
                            rf == 3 && rw == WIRE_LEN -> statusMessage = (rv as ByteArray).decodeToString()
                            rf == 4 && rw == WIRE_LEN -> parseSoundLightSettings(rv as ByteArray)
                        }
                    }
                }
                field == 3 && wire == WIRE_LEN -> {
                    // Backend
                    forEachField(value as ByteArray) { bf, bw, bv ->
                        if (bf == 1 && bw == WIRE_LEN) {
                            forEachField(bv as ByteArray) { df, dw, dv ->
                                if (df == 1 && dw == WIRE_VARINT) {
                                    backendDeviceConnected = ((dv as Long).toInt() == 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        return DecodedSoundLightFrame(
            requestId = requestId,
            statusCode = statusCode,
            statusMessage = statusMessage,
            backendDeviceConnected = backendDeviceConnected,
            settings = parsedSettings,
            temperature = temperature,
            humidity = humidity
        )
    }

    // =========================================================================
    // Low-level Protobuf Wire Reader
    // =========================================================================

    private fun forEachField(
        bytes: ByteArray,
        block: (fieldNumber: Int, wireType: Int, value: Any) -> Unit
    ) {
        var index = 0
        val size = bytes.size

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (index < size) {
                val b = bytes[index++].toInt()
                result = result or ((b and 0x7F).toLong() shl shift)
                if ((b and 0x80) == 0) return result
                shift += 7
            }
            return result
        }

        while (index < size) {
            val tag = readVarint().toInt()
            if (tag == 0) break
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07
            when (wireType) {
                WIRE_VARINT -> {
                    val v = readVarint()
                    block(fieldNumber, wireType, v)
                }
                WIRE_FIXED64 -> {
                    if (index + 8 > size) break
                    val v = ByteBuffer.wrap(bytes, index, 8).order(ByteOrder.LITTLE_ENDIAN).long
                    index += 8
                    block(fieldNumber, wireType, v)
                }
                WIRE_LEN -> {
                    val len = readVarint().toInt()
                    if (len < 0 || index + len > size) break
                    val sub = bytes.copyOfRange(index, index + len)
                    index += len
                    block(fieldNumber, wireType, sub)
                }
                WIRE_FIXED32 -> {
                    if (index + 4 > size) break
                    val f = ByteBuffer.wrap(bytes, index, 4).order(ByteOrder.LITTLE_ENDIAN).float
                    index += 4
                    block(fieldNumber, wireType, f)
                }
                else -> break
            }
        }
    }
}
