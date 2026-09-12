package com.hikgate.app.network

import com.hikgate.app.data.DeviceCapability
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed class ProbeResult {
    data class Success(
        val capability: DeviceCapability,
        val detail: String,
        val isapiReachable: Boolean
    ) : ProbeResult()

    data class Failure(val reason: FailureReason, val message: String) : ProbeResult()
}

enum class FailureReason { CONNECTION_REFUSED, TIMEOUT, AUTH_FAILED, INVALID_CERT, UNREACHABLE, UNKNOWN }

/**
 * Fetches the device's login/index page over plain HTTP(S) and inspects the
 * markup for known fingerprints, so the rest of the app can tell the user the
 * truth about what will and won't work — instead of the app just trying a
 * WebView and silently failing, or claiming "IE support" it doesn't have.
 *
 * This is a best-effort heuristic classifier, not a guarantee: firmware
 * varies a lot across Hikvision generations and OEM rebrands (Hikvision,
 * Hiwatch, Annke, etc. all reuse pieces of this UI).
 */
class HikvisionProbe(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()
) {

    fun probe(baseUrl: String): ProbeResult {
        return try {
            val request = Request.Builder()
                .url(baseUrl)
                .header("User-Agent", UserAgents.INTERNET_EXPLORER_11)
                .build()

            client.newCall(request).execute().use { response ->
                when (response.code) {
                    401 -> ProbeResult.Failure(
                        FailureReason.AUTH_FAILED,
                        "Device requires authentication (401) — this is expected before login; " +
                            "capability detection will retry after credentials are supplied."
                    )
                    else -> {
                        val body = response.body?.string().orEmpty()
                        classify(body, isapiReachable = probeIsapi(baseUrl))
                    }
                }
            }
        } catch (e: java.net.ConnectException) {
            ProbeResult.Failure(FailureReason.CONNECTION_REFUSED, "Connection refused by $baseUrl")
        } catch (e: java.net.SocketTimeoutException) {
            ProbeResult.Failure(FailureReason.TIMEOUT, "Timed out reaching $baseUrl")
        } catch (e: javax.net.ssl.SSLHandshakeException) {
            ProbeResult.Failure(FailureReason.INVALID_CERT, "Certificate not trusted: ${e.message}")
        } catch (e: java.net.UnknownHostException) {
            ProbeResult.Failure(FailureReason.UNREACHABLE, "Host not found: ${e.message}")
        } catch (e: Exception) {
            ProbeResult.Failure(FailureReason.UNKNOWN, e.message ?: "Unknown error")
        }
    }

    private fun classify(html: String, isapiReachable: Boolean): ProbeResult.Success {
        val lower = html.lowercase()
        return when {
            // Old Hikvision ActiveX login pages load OCX controls and reference
            // clsid GUIDs / WebComponents.exe installers.
            lower.contains("clsid:") || lower.contains("activex") ||
                lower.contains("ocx") || lower.contains("webcomponents.exe") ->
                ProbeResult.Success(
                    DeviceCapability.ACTIVEX_OR_NPAPI,
                    "Page references an ActiveX control / OCX plugin. No Android browser can run this.",
                    isapiReachable
                )

            lower.contains("npapi") || lower.contains("application/x-") && lower.contains("plugin") ->
                ProbeResult.Success(
                    DeviceCapability.ACTIVEX_OR_NPAPI,
                    "Page references an NPAPI plugin. Unsupported on any modern browser, including this app.",
                    isapiReachable
                )

            lower.contains("hikvision webcomponents") || lower.contains("webvideoctrl") ->
                ProbeResult.Success(
                    DeviceCapability.HIKVISION_WEB_COMPONENTS,
                    "Page uses Hikvision's WebComponents/WebVideoCtrl JS plugin. Some builds fall back " +
                        "to HTML5 automatically in modern browsers; others silently fail. Test live view first.",
                    isapiReachable
                )

            lower.contains("<video") || lower.contains("mediasource") || lower.contains("hls.js") ->
                ProbeResult.Success(
                    DeviceCapability.HTML5_VIDEO,
                    "Page uses an HTML5 <video> element or MSE-based player. Should work directly in the WebView.",
                    isapiReachable
                )

            lower.contains("websocket") ->
                ProbeResult.Success(
                    DeviceCapability.JS_PLAYER,
                    "Page opens a WebSocket video stream via JavaScript. Usually WebView-compatible.",
                    isapiReachable
                )

            else ->
                ProbeResult.Success(
                    DeviceCapability.UNKNOWN,
                    "Could not confidently classify the interface from the login page alone. " +
                        "Recommend trying Live View (RTSP) directly, since it bypasses the web UI entirely.",
                    isapiReachable
                )
        }
    }

    /** ISAPI is Hikvision's documented HTTP(S) REST API — a real, supported alternative to scraping the web UI. */
    private fun probeIsapi(baseUrl: String): Boolean {
        return try {
            val req = Request.Builder().url("$baseUrl/ISAPI/System/deviceInfo").build()
            client.newCall(req).execute().use { it.code == 200 || it.code == 401 }
        } catch (e: Exception) {
            false
        }
    }
}
