package com.hikgate.app.network

/**
 * User-Agent presets for the built-in browser.
 *
 * IMPORTANT / HONESTY NOTE: these strings only change the User-Agent HTTP
 * header and the navigator.userAgent JS property. They make a Hikvision
 * server *think* it's talking to Internet Explorer, which is sometimes
 * enough to get past a UA sniff that would otherwise show a "please use IE"
 * error page and reveal HTML5 content that was there all along. They do
 * NOT add IE's Trident/MSHTML rendering engine, and they absolutely do NOT
 * add ActiveX/NPAPI plugin support — no app on Android can, because Android
 * has never had an ActiveX or NPAPI host process. If a page's actual video
 * element is an <object classid="clsid:...">, changing the UA changes
 * nothing about whether it plays.
 */
object UserAgents {
    const val MODERN_CHROME =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"

    // Genuine IE11 desktop UA string. Spoofing this is a common, mostly-harmless
    // trick to get past legacy device UA checks — it does not grant IE's engine.
    const val INTERNET_EXPLORER_11 =
        "Mozilla/5.0 (Windows NT 10.0; WOW64; Trident/7.0; rv:11.0) like Gecko"

    // Edge's "IE Mode" still reports a Trident/MSIE-flavored UA to the site;
    // included as a second UA-sniff bypass some Hikvision firmware checks for.
    const val EDGE_IE_MODE =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; Trident/7.0; rv:11.0) like Gecko"

    val PRESETS = listOf(
        "Modern Chrome" to MODERN_CHROME,
        "Internet Explorer 11 (UA emulation only)" to INTERNET_EXPLORER_11,
        "Edge IE Mode (UA emulation only)" to EDGE_IE_MODE
    )
}
