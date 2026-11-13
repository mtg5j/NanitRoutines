package com.droid.nanitroutines.data.lights

import com.droid.nanitroutines.domain.model.LutronZoneInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.roundToInt

/**
 * Local Wi-Fi LEAP client for Lutron Caséta Smart Bridge (ports 8081 / 8083).
 * Provides silent (<50ms) background switching and real-time dimmer/switch status
 * for "Kips Room Main Lights" without popping up Google Assistant.
 */
class LutronLeapClient {

    private fun createLeapSslSocket(host: String, port: Int = 8081, timeoutMs: Int = 3500): SSLSocket {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAll, SecureRandom())
        val socket = sslContext.socketFactory.createSocket() as SSLSocket
        socket.connect(InetSocketAddress(host.trim(), port), timeoutMs)
        socket.soTimeout = timeoutMs
        socket.startHandshake()
        return socket
    }

    suspend fun setZoneLevel(
        bridgeIp: String,
        zoneHref: String,
        turnOn: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        if (bridgeIp.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Lutron Bridge IP not configured"))
        }
        val normalizedZone = if (zoneHref.startsWith("/zone/")) zoneHref else "/zone/${zoneHref.trim('/')}"
        val targetLevel = if (turnOn) 100 else 0

        runCatching {
            createLeapSslSocket(bridgeIp, 8081).use { socket ->
                val writer = OutputStreamWriter(socket.outputStream, Charsets.UTF_8)
                val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))

                val leapRequest = JSONObject().apply {
                    put("CommUniqueType", "CreateRequest")
                    put(
                        "Header",
                        JSONObject().put("Url", "$normalizedZone/commandprocessor")
                    )
                    put(
                        "Body",
                        JSONObject().put(
                            "Command",
                            JSONObject()
                                .put("CommandType", "GoToLevel")
                                .put(
                                    "Parameter",
                                    org.json.JSONArray().put(
                                        JSONObject()
                                            .put("Type", "Level")
                                            .put("Value", targetLevel)
                                    )
                                )
                        )
                    )
                }

                writer.write(leapRequest.toString() + "\r\n")
                writer.flush()
                val responseLine = reader.readLine().orEmpty()
                if (responseLine.isNotBlank()) {
                    "Kips Room Main Lights -> ${if (turnOn) "ON (100%)" else "OFF (0%)"}"
                } else {
                    "Kips Room Main Lights command sent (${if (turnOn) "ON" else "OFF"})"
                }
            }
        }
    }

    suspend fun readZoneStatus(
        bridgeIp: String,
        zoneHref: String
    ): Result<SmartLightStatus> = withContext(Dispatchers.IO) {
        if (bridgeIp.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Lutron Bridge IP not set"))
        }
        val normalizedZone = if (zoneHref.startsWith("/zone/")) zoneHref else "/zone/${zoneHref.trim('/')}"

        runCatching {
            createLeapSslSocket(bridgeIp, 8081).use { socket ->
                val writer = OutputStreamWriter(socket.outputStream, Charsets.UTF_8)
                val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))

                val leapRead = JSONObject().apply {
                    put("CommUniqueType", "ReadRequest")
                    put("Header", JSONObject().put("Url", "$normalizedZone/status"))
                }
                writer.write(leapRead.toString() + "\r\n")
                writer.flush()

                val line = reader.readLine().orEmpty()
                val root = JSONObject(line)
                val zoneStatus = root.optJSONObject("Body")?.optJSONObject("ZoneStatus")
                val level = zoneStatus?.optDouble("Level", -1.0) ?: -1.0
                val switched = zoneStatus?.optString("SwitchedLevel", "").orEmpty()

                val pct = if (level >= 0.0) {
                    level.roundToInt().coerceIn(0, 100)
                } else if (switched.equals("On", ignoreCase = true)) {
                    100
                } else {
                    0
                }
                SmartLightStatus(
                    isOn = pct > 0,
                    brightnessPercent = pct
                )
            }
        }
    }

    suspend fun discoverZones(bridgeIp: String): Result<List<LutronZoneInfo>> = withContext(Dispatchers.IO) {
        if (bridgeIp.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter your Lutron Smart Bridge IP first"))
        }
        runCatching {
            createLeapSslSocket(bridgeIp, 8081).use { socket ->
                val writer = OutputStreamWriter(socket.outputStream, Charsets.UTF_8)
                val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))

                val leapRead = JSONObject().apply {
                    put("CommUniqueType", "ReadRequest")
                    put("Header", JSONObject().put("Url", "/zone"))
                }
                writer.write(leapRead.toString() + "\r\n")
                writer.flush()

                val line = reader.readLine().orEmpty()
                val root = JSONObject(line)
                val zonesArr = root.optJSONObject("Body")?.optJSONArray("Zones")
                val list = mutableListOf<LutronZoneInfo>()
                if (zonesArr != null) {
                    for (i in 0 until zonesArr.length()) {
                        val z = zonesArr.getJSONObject(i)
                        list.add(
                            LutronZoneInfo(
                                zoneHref = z.optString("href", "/zone/${i + 1}"),
                                name = z.optString("Name", "Zone ${i + 1}")
                            )
                        )
                    }
                }
                list
            }
        }
    }
}
