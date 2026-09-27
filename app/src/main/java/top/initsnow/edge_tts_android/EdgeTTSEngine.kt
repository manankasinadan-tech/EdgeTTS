package top.initsnow.edge_tts_android

/**
 * Core constants and engine status for Edge TTS.
 */
object EdgeTTSEngine {
    const val SERVICE_INTERFACE = "android.intent.action.TTS_SERVICE"
    const val DEFAULT_VOICE = "en-US-EmmaMultilingualNeural"
    const val SAMPLE_RATE_HZ = 24_000

    fun isNativeReady(): Boolean = EdgeTtsNative.isReady()
    fun getLoadError(): String = EdgeTtsNative.getLoadError()
}
