package com.hikgate.app.data

/**
 * A saved Hikvision camera / DVR / NVR endpoint.
 *
 * Credentials are NOT stored on this object once persisted — [DeviceRepository]
 * keeps username/password in EncryptedSharedPreferences keyed by [id], separate
 * from the rest of the (non-sensitive) device metadata. This object is safe to
 * log, display, and pass around in memory; do not add a plaintext password field
 * back onto it.
 */
data class Device(
    val id: String,
    val name: String,
    val host: String,
    val httpPort: Int = 80,
    val useHttps: Boolean = false,
    val rtspPort: Int = 554,
    val defaultChannel: String = "101",
    /** Result of the last capability probe, if one has been run. */
    val lastKnownCapability: DeviceCapability? = null
) {
    val httpBaseUrl: String
        get() {
            val scheme = if (useHttps) "https" else "http"
            return "$scheme://$host:$httpPort"
        }

    fun rtspUrl(username: String, password: String, channel: String = defaultChannel): String {
        val encUser = java.net.URLEncoder.encode(username, "UTF-8")
        val encPass = java.net.URLEncoder.encode(password, "UTF-8")
        return "rtsp://$encUser:$encPass@$host:$rtspPort/Streaming/Channels/$channel"
    }
}

/**
 * What HikvisionProbe determined the device's web interface actually needs.
 * This drives whether we offer the WebView browser, native Live View, or both,
 * and lets the UI tell the user the truth instead of a hopeful guess.
 */
enum class DeviceCapability {
    HTML5_VIDEO,          // Modern interface, plays fine in a normal WebView
    JS_PLAYER,            // JS-based player (e.g. websocket/MSE), usually WebView-compatible
    HIKVISION_WEB_COMPONENTS, // "WebComponents"/"Hik-Connect" style plugin, partial WebView support
    ACTIVEX_OR_NPAPI,     // Cannot run in any Android browser, ever
    RTSP_ONLY,            // No usable web video; use native RTSP live view
    UNKNOWN
}
