package com.hikgate.app.ui.browser

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import com.hikgate.app.databinding.ActivityBrowserBinding
import com.hikgate.app.network.UserAgents
import com.hikgate.app.security.CredentialStore

/**
 * A real browser, built on Android's Chromium WebView, tuned specifically for
 * old Hikvision device UIs: cookies, DOM storage, mixed content, popups,
 * downloads, HTTP basic/digest auth, and a User-Agent switcher.
 *
 * WHAT THIS CANNOT DO, ON PURPOSE, WITHOUT LYING ABOUT IT:
 * Android's WebView is Chromium. It has never hosted ActiveX or NPAPI, on any
 * Android version, in any app — that's not a HikGate limitation, it's true of
 * every Android browser including Chrome itself. Switching the User-Agent to
 * "Internet Explorer 11" only changes the UA string; it does not add Trident/
 * MSHTML rendering or an ActiveX host. This activity actively looks for
 * ActiveX/OCX/NPAPI markers in the loaded page and tells the user plainly
 * when that's what they're looking at, instead of showing a blank video box
 * and letting them assume the app is broken (or worse, that it "worked").
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    private lateinit var credentials: CredentialStore
    private var currentUserAgent: String = UserAgents.INTERNET_EXPLORER_11
    private var isFullscreen = false
    private var deviceId: String? = null
    private var homeUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        credentials = CredentialStore(this)

        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
        homeUrl = intent.getStringExtra(EXTRA_URL) ?: "http://192.168.1.100"
        binding.addressBar.setText(homeUrl)

        setupWebView()
        setupControls()

        loadUrl(homeUrl)
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.allowContentAccess = true
        settings.allowFileAccess = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.userAgentString = currentUserAgent

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {

            override fun onReceivedHttpAuthRequest(
                view: WebView, handler: HttpAuthHandler, host: String, realm: String
            ) {
                // Legacy Hikvision devices frequently gate the whole UI behind
                // HTTP Basic or Digest auth before any page JS runs at all.
                val user = deviceId?.let { credentials.getUsername(it) }
                val pass = deviceId?.let { credentials.getPassword(it) }
                if (!user.isNullOrEmpty()) {
                    handler.proceed(user, pass)
                } else {
                    promptForHttpAuth(handler)
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                // Self-signed certs are the norm on this hardware. Never auto-trust;
                // always make the user explicitly confirm.
                AlertDialog.Builder(this@BrowserActivity)
                    .setTitle(com.hikgate.app.R.string.warn_cert_title)
                    .setMessage(com.hikgate.app.R.string.warn_cert_body)
                    .setPositiveButton("Trust and continue") { _, _ -> handler.proceed() }
                    .setNegativeButton("Cancel") { _, _ -> handler.cancel() }
                    .setCancelable(false)
                    .show()
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                binding.addressBar.setText(url)
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                binding.progressBar.visibility = View.GONE
                checkForActiveXOrNpapi(view)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Keep navigation inside this WebView, including popups the
                // Hikvision UI opens for channel selection / PTZ controls.
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.progressBar.progress = newProgress
            }

            override fun onCreateWindow(
                view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message
            ): Boolean {
                // Hikvision's older UIs open channel/PTZ control popups via window.open().
                val newWebView = WebView(this@BrowserActivity)
                newWebView.settings.javaScriptEnabled = true
                newWebView.settings.domStorageEnabled = true
                newWebView.settings.userAgentString = currentUserAgent
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = newWebView
                resultMsg.sendToTarget()

                val dialog = android.app.Dialog(this@BrowserActivity)
                dialog.setContentView(newWebView)
                newWebView.webViewClient = WebViewClient()
                dialog.show()
                return true
            }
        }

        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            downloadFile(url, contentDisposition, mimeType)
        }
    }

    private fun downloadFile(url: String, contentDisposition: String?, mimeType: String?) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
            request.setMimeType(mimeType)
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Download failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun promptForHttpAuth(handler: HttpAuthHandler) {
        val container = android.widget.LinearLayout(this)
        container.orientation = android.widget.LinearLayout.VERTICAL
        container.setPadding(48, 24, 48, 0)
        val userInput = android.widget.EditText(this).apply { hint = "Username" }
        val passInput = android.widget.EditText(this).apply {
            hint = "Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        container.addView(userInput)
        container.addView(passInput)

        AlertDialog.Builder(this)
            .setTitle("Sign in")
            .setView(container)
            .setPositiveButton("Sign in") { _, _ ->
                handler.proceed(userInput.text.toString(), passInput.text.toString())
            }
            .setNegativeButton("Cancel") { _, _ -> handler.cancel() }
            .setCancelable(false)
            .show()
    }

    /**
     * Looks at the loaded DOM for the specific markers that mean "this page
     * needs a plugin no Android browser can run." Told to the user immediately
     * and plainly — no pretending a UA switch fixed it.
     */
    private fun checkForActiveXOrNpapi(webView: WebView) {
        val probeJs = """
            (function() {
                var html = document.documentElement.innerHTML.toLowerCase();
                var hasActiveX = html.indexOf('clsid:') !== -1 || html.indexOf('classid') !== -1 ||
                                  html.indexOf('.ocx') !== -1 || html.indexOf('webcomponents.exe') !== -1;
                var hasNpapi = html.indexOf('application/x-') !== -1 && html.indexOf('embed') !== -1;
                return hasActiveX || hasNpapi;
            })();
        """.trimIndent()

        webView.evaluateJavascript(probeJs) { result ->
            if (result == "true") {
                showActiveXWarning()
            }
        }
    }

    private fun showActiveXWarning() {
        AlertDialog.Builder(this)
            .setTitle(com.hikgate.app.R.string.activex_detected_title)
            .setMessage(com.hikgate.app.R.string.activex_detected_body)
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun setupControls() {
        binding.btnBack.setOnClickListener { if (binding.webView.canGoBack()) binding.webView.goBack() }
        binding.btnForward.setOnClickListener { if (binding.webView.canGoForward()) binding.webView.goForward() }
        binding.btnRefresh.setOnClickListener { binding.webView.reload() }
        binding.btnHome.setOnClickListener { loadUrl(homeUrl) }
        binding.btnGo.setOnClickListener { loadUrl(binding.addressBar.text.toString()) }
        binding.addressBar.setOnEditorActionListener { _, _, _ ->
            loadUrl(binding.addressBar.text.toString()); true
        }
        binding.btnFullscreen.setOnClickListener { toggleFullscreen() }
        binding.btnUserAgent.setOnClickListener { showUserAgentPicker() }
    }

    private fun loadUrl(rawUrl: String) {
        var url = rawUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        binding.webView.loadUrl(url)
    }

    private fun showUserAgentPicker() {
        val labels = UserAgents.PRESETS.map { it.first }.toMutableList().apply { add("Custom...") }
        AlertDialog.Builder(this)
            .setTitle("User-Agent")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < UserAgents.PRESETS.size) {
                    applyUserAgent(UserAgents.PRESETS[which].second)
                } else {
                    promptCustomUserAgent()
                }
            }
            .show()
    }

    private fun promptCustomUserAgent() {
        val input = android.widget.EditText(this)
        input.setText(currentUserAgent)
        AlertDialog.Builder(this)
            .setTitle("Custom User-Agent")
            .setView(input)
            .setPositiveButton("Apply") { _, _ -> applyUserAgent(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyUserAgent(ua: String) {
        currentUserAgent = ua
        binding.webView.settings.userAgentString = ua
        binding.webView.reload()
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        binding.toolbarContainer.visibility = if (isFullscreen) View.GONE else View.VISIBLE
        if (isFullscreen) {
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) binding.webView.goBack() else super.onBackPressed()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_DEVICE_ID = "extra_device_id"
    }
}
