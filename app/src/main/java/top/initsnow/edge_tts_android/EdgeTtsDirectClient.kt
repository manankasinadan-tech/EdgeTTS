package top.initsnow.edge_tts_android

import android.os.SystemClock
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Direct client implementing the exact Edge TTS WebSocket protocol and Sec-MS-GEC token generation.
 * Used as a zero-dependency high-speed fallback and companion to the Rust native bridge.
 */
object EdgeTtsDirectClient {
    private const val TAG = "EdgeTtsDirectClient"
    private const val BASE_URL = "speech.platform.bing.com/consumer/speech/synthesize/readaloud"
    private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val SEC_MS_GEC_VERSION = "1-143.0.3650.75"
    private const val CHROMIUM_MAJOR_VERSION = "143"
    private const val WINDOWS_EPOCH_OFFSET_SECONDS = 11644473600L

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun generateSecMsGec(timeMillis: Long = System.currentTimeMillis()): String {
        val unixSeconds = timeMillis / 1000L
        val rounded = ((unixSeconds + WINDOWS_EPOCH_OFFSET_SECONDS) / 300L) * 300L
        val windowsTicks = rounded * 10000000L
        val input = "${windowsTicks}${TRUSTED_CLIENT_TOKEN}"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02X".format(it) }
    }

    fun generateMuid(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02X".format(it) }
    }

    private fun userAgent(): String {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/$CHROMIUM_MAJOR_VERSION.0.0.0 Safari/537.36 " +
                "Edg/$CHROMIUM_MAJOR_VERSION.0.0.0"
    }

    private fun javascriptTimestamp(): String {
        val sdf = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    fun normalizeVoice(voice: String): String {
        if (voice.startsWith("Microsoft Server Speech")) {
            return voice
        }
        val lastDashIndex = voice.lastIndexOf('-')
        return if (lastDashIndex > 0) {
            val lang = voice.substring(0, lastDashIndex)
            val name = voice.substring(lastDashIndex + 1)
            "Microsoft Server Speech Text to Speech Voice ($lang, $name)"
        } else {
            voice
        }
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    fun listVoicesJson(): String {
        val url = "https://$BASE_URL/voices/list?trustedclienttoken=$TRUSTED_CLIENT_TOKEN"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent())
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Voice list request failed: HTTP ${response.code}")
            }
            return response.body?.string() ?: "[]"
        }
    }

    /**
     * Synthesizes text with the specified voice and prosody options into raw MP3 bytes.
     */
    fun synthesizeMp3(
        text: String,
        voice: String,
        rate: String = "+0%",
        volume: String = "+0%",
        pitch: String = "+0Hz"
    ): ByteArray {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return ByteArray(0)
        }

        val secMsGec = generateSecMsGec()
        val connectionId = UUID.randomUUID().toString().replace("-", "")
        val muid = generateMuid()

        val wsUrl = "wss://$BASE_URL/edge/v1" +
                "?TrustedClientToken=$TRUSTED_CLIENT_TOKEN" +
                "&ConnectionId=$connectionId" +
                "&Sec-MS-GEC=$secMsGec" +
                "&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"

        val request = Request.Builder()
            .url(wsUrl)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("Sec-WebSocket-Version", "13")
            .header("User-Agent", userAgent())
            .header("Accept-Encoding", "gzip, deflate, br, zstd")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", "muid=$muid;")
            .build()

        val output = ByteArrayOutputStream()
        val latch = CountDownLatch(1)
        val errorRef = AtomicReference<Throwable?>(null)

        val normalizedVoice = normalizeVoice(voice)
        val lang = if (normalizedVoice.contains("(") && normalizedVoice.contains(",")) {
            normalizedVoice.substringAfter("(").substringBefore(",").trim()
        } else {
            "en-US"
        }

        val timestamp = javascriptTimestamp()
        val speechConfigPayload = "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":false,\"wordBoundaryEnabled\":false},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
        val speechConfigMsg = "X-Timestamp:$timestamp\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n$speechConfigPayload\r\n"

        val requestId = UUID.randomUUID().toString().replace("-", "")
        val ssmlBody = "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"$lang\"><voice name=\"$normalizedVoice\"><prosody pitch=\"$pitch\" rate=\"$rate\" volume=\"$volume\">${escapeXml(trimmed)}</prosody></voice></speak>"
        val ssmlMsg = "X-RequestId:$requestId\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:${timestamp}Z\r\nPath:ssml\r\n\r\n$ssmlBody"

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(speechConfigMsg)
                webSocket.send(ssmlMsg)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                if (data.size > 2) {
                    val headerLength = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    val payloadOffset = 2 + headerLength
                    if (payloadOffset < data.size) {
                        var audioStart = payloadOffset
                        // Strip leading \r\n if present
                        if (audioStart + 1 < data.size && data[audioStart] == '\r'.code.toByte() && data[audioStart + 1] == '\n'.code.toByte()) {
                            audioStart += 2
                        }
                        if (audioStart < data.size) {
                            synchronized(output) {
                                output.write(data, audioStart, data.size - audioStart)
                            }
                        }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end")) {
                    webSocket.close(1000, "Done")
                    latch.countDown()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                errorRef.set(t)
                latch.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                latch.countDown()
            }
        }

        val ws = httpClient.newWebSocket(request, listener)
        val completed = latch.await(25, TimeUnit.SECONDS)
        if (!completed) {
            ws.cancel()
            throw IOException("Edge TTS synthesis timed out after 25s")
        }

        val err = errorRef.get()
        if (err != null) {
            throw IOException("Edge TTS synthesis failed: ${err.message}", err)
        }

        return output.toByteArray()
    }

    /**
     * Pre-synthesized or crafted silence MP3 buffer for 24kHz 48kbps mono.
     * 15 MPEG-2 Layer 3 frames of 144 bytes = 2160 bytes (~360ms).
     */
    val SILENCE_350MS_MP3: ByteArray by lazy {
        generateSilenceFrames(15)
    }

    fun getSilenceMp3(durationMs: Int): ByteArray {
        val frameCount = (durationMs / 24).coerceIn(4, 45)
        return generateSilenceFrames(frameCount)
    }

    private fun generateSilenceFrames(frameCount: Int): ByteArray {
        // MPEG-2 Layer III, 24kHz, 48kbps mono frame is 144 bytes:
        // Header: 0xFF, 0xF3, 0x48, 0x00
        val frame = ByteArray(144)
        frame[0] = 0xFF.toByte()
        frame[1] = 0xF3.toByte()
        frame[2] = 0x48.toByte()
        frame[3] = 0x00.toByte()
        // remaining 140 bytes are zeroes (silent granule/side info/huffman data)

        val out = ByteArray(frameCount * 144)
        for (i in 0 until frameCount) {
            System.arraycopy(frame, 0, out, i * 144, 144)
        }
        return out
    }
}
