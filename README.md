# HikGate

An Android app for reaching legacy Hikvision (and Hikvision-derived: HiWatch, Annke,
etc.) camera/DVR/NVR web interfaces, plus a native RTSP live-view screen for when
the web interface can't play video on Android at all.

## Read this first: what "IE compatibility" actually means here

Android's WebView is Chromium. **No app on Android — this one included — can run
ActiveX controls or NPAPI plugins.** That capability was removed from every modern
browser (including desktop Chrome, Firefox, and Edge) years ago, and Android never
had it in the first place. If a Hikvision page's video box is an
`<object classid="clsid:...">` element expecting an OCX control, no User-Agent
string, no "IE mode," no polyfill changes that.

What this app *does* do, honestly:

| Feature | What it really is |
|---|---|
| User-Agent presets (Chrome / "IE11" / "Edge IE Mode") | Changes the `User-Agent` HTTP header and `navigator.userAgent`. Useful for getting past a device's UA-sniffing "please use Internet Explorer" gate page, which sometimes reveals an HTML5 fallback that was there all along. Does **not** add Trident/MSHTML rendering. |
| Legacy JS / DOM storage / cookies / mixed content | Real WebView settings (`domStorageEnabled`, `databaseEnabled`, `MIXED_CONTENT_ALWAYS_ALLOW`, cookie manager) that let old device UIs actually run and remember session/login state. |
| ActiveX/NPAPI detection | The browser screen inspects the loaded page for `clsid:`, `.ocx`, `WebComponents.exe`, NPAPI `<embed>` markers, etc., and tells you plainly when that's what you're looking at — instead of showing a dead video box. |
| Native Live View | A completely separate screen that bypasses the web UI entirely and pulls video directly from the camera's **RTSP** stream using Media3/ExoPlayer (Hikvision's standard `rtsp://.../Streaming/Channels/101` path). This is the real fix for ActiveX-only interfaces, not a browser trick. |

`HikvisionProbe` fetches the device's login page before you ever open the browser
and classifies it (`HTML5_VIDEO`, `JS_PLAYER`, `HIKVISION_WEB_COMPONENTS`,
`ACTIVEX_OR_NPAPI`, or `UNKNOWN`), and separately checks whether the device's
**ISAPI** REST endpoint (`/ISAPI/System/deviceInfo`) responds — ISAPI is
Hikvision's own documented HTTP API and a legitimate supported alternative to
scraping the web UI, not a workaround.

## Features

- Full browser chrome: address bar, back/forward/refresh/home, fullscreen, private
  IP ranges (`192.168.x.x`, `10.x.x.x`, `172.16–31.x.x`, `localhost`) allowed via
  a scoped `network_security_config.xml`.
- Cookies, DOM storage, database storage, mixed HTTP/HTTPS content, JS popups
  (channel/PTZ control windows), file downloads via `DownloadManager`.
- HTTP Basic/Digest auth handling — auto-fills saved credentials, prompts otherwise.
- Self-signed certificate confirmation flow (never silently trusts, never silently
  blocks — the norm on this hardware is a self-signed cert).
- Configurable User-Agent with Chrome / "IE11" / "Edge IE Mode" / custom presets.
- Native RTSP Live View (Media3/ExoPlayer), full-screen landscape, screen-stays-awake,
  auto-built from saved host + RTSP port + channel + credentials, or a raw RTSP URL.
- Saved devices list, each with its own credentials.
- Credentials stored in `EncryptedSharedPreferences`, backed by Android Keystore
  (AES-256-GCM/SIV). Passwords are never logged and never sent anywhere except the
  device's own login/RTSP endpoints — see `security/CredentialStore.kt`.
- Clear, specific error states for connection-refused, timeout, auth failure,
  invalid certificate, unreachable device, and detected-unsupported-plugin.

## Project layout

```
app/src/main/java/com/hikgate/app/
├── HikGateApplication.kt
├── data/
│   ├── Device.kt              # device model + DeviceCapability enum
│   └── DeviceRepository.kt    # non-sensitive metadata persistence (JSON in SharedPreferences)
├── security/
│   └── CredentialStore.kt     # Keystore-backed EncryptedSharedPreferences for user/pass
├── network/
│   ├── UserAgents.kt          # UA presets, with honest doc-comments on what they do/don't do
│   └── HikvisionProbe.kt      # fetches + classifies the device's web interface, checks ISAPI
├── util/
│   └── NetworkGuard.kt        # private/LAN IP detection, used for the HTTP warning flow
└── ui/
    ├── main/                  # MainActivity + DeviceAdapter: saved devices list
    ├── adddevice/             # AddDeviceActivity: form + "Test Connection" probe
    ├── device/                # DeviceActivity: per-device hub (Web / Live View / Test / Delete)
    ├── browser/                # BrowserActivity: the WebView browser
    └── liveview/               # LiveViewActivity: native ExoPlayer RTSP screen
```

Architecture is a light MVVM/clean split: `data` (models + persistence), `network`
(device communication + classification), `security` (credentials), `ui` (one
package per screen, each activity thin and delegating to the layers above). The
browser engine (`ui/browser`) and native camera player (`ui/liveview`) are fully
separate — the app never tries to make the WebView do the video player's job.

## Building

This is a standard Gradle-based Android Studio project.

1. **Open in Android Studio** (Koala/2024.1 or newer recommended). Android Studio
   will generate the Gradle wrapper JAR and `gradlew`/`gradlew.bat` scripts
   automatically on first sync — they aren't checked into this bundle. If you'd
   rather do it from the command line, run `gradle wrapper --gradle-version 8.7`
   once inside the project root (requires a local Gradle install), which will
   create `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` to
   match the `gradle-wrapper.properties` already included.
2. Let Gradle sync — it will pull AndroidX, Material Components, Media3/ExoPlayer
   (including the RTSP extension), `androidx.security:security-crypto`, and
   OkHttp from Google's and Maven Central's repositories.
3. Build → **Build Bundle(s)/APK(s) → Build APK(s)**, or `./gradlew assembleDebug`
   from the command line once the wrapper is generated. The debug APK lands in
   `app/build/outputs/apk/debug/app-debug.apk`, installable via
   `adb install app-debug.apk` or by copying it to a device.
4. Minimum SDK 24 (Android 7.0), target/compile SDK 34.

No signing config is included for release builds — add your own `signingConfig`
in `app/build.gradle.kts` before shipping a release APK.

## Using it

1. Tap **+** → enter the camera/NVR's IP, HTTP port, RTSP port, channel, and
   credentials → **Test Connection** to see what `HikvisionProbe` found before
   you commit to anything → **Save Device**.
2. From the device screen: **Open Hikvision Web Interface** for the normal
   browser experience (with an automatic ActiveX/NPAPI check after the page
   loads), or **Live View** to jump straight to native RTSP video.
3. If the app detects ActiveX/NPAPI markers, it says so immediately and offers
   to switch straight to Live View instead of leaving you looking at a blank
   video panel.

## Troubleshooting / compatibility matrix

| Device type | Web Interface (WebView) | Live View (RTSP) |
|---|---|---|
| **New Hikvision firmware** (recent DS-7600/7700 NVR series, most 2020+ IP cameras) | Usually works — modern builds ship HTML5 `<video>`/MSE players. | Works. RTSP is enabled by default on almost all current models. |
| **Old Hikvision firmware, non-ActiveX** (older HTML/JS UI, no OCX) | Usually works with the IE11 UA preset to get past UA-sniffing; cookies/DOM storage settings matter here. | Works if RTSP is enabled in the device's network settings (it is by default on most DVR/NVR firmware). |
| **IE-only Hikvision interfaces** (login page explicitly says "use Internet Explorer") | Sometimes works — many of these only check the UA string and serve HTML5 content anyway once it thinks it's IE. Worth trying before assuming it's truly ActiveX-gated. | Works independently of the web UI. |
| **ActiveX-dependent interfaces** (`WebComponents`, `.ocx`, `clsid:` in the page source — classic for DS-7204/7208 DVR-era UIs) | **Will not work, on this or any Android browser.** The app detects this and tells you directly. | This is the intended path — use Live View. |
| **RTSP-compatible devices generally** | N/A | Works for the vast majority of Hikvision DVR/NVR/camera firmware, since RTSP over `/Streaming/Channels/<channel>` has been Hikvision's standard streaming path for over a decade. Older/rebranded (OEM) firmware occasionally uses non-standard channel numbering or requires TCP-interleaved RTSP — the Live View screen forces RTP-over-TCP for exactly this reason. |

Specific error meanings, all surfaced directly rather than as generic failures:

- **Connection refused** — nothing is listening on that IP/port; check the port
  number and that the device is actually reachable from this network (not on a
  different VLAN/subnet).
- **Timeout** — device isn't responding at all; check Wi-Fi/LAN connectivity,
  or that you're not trying to reach a LAN-only camera from outside the network.
- **Authentication failure** — wrong username/password, or the account is locked
  out after repeated failed attempts (Hikvision devices do this aggressively).
- **Invalid certificate** — self-signed HTTPS cert, which is normal for this
  hardware; the app asks you to explicitly trust it rather than doing so silently.
- **Unsupported legacy plugin detected** — the page needs ActiveX/NPAPI; switch
  to Live View.
- **Device unreachable** — hostname doesn't resolve, or the IP is simply wrong.

## Security notes

- Credentials are stored via `androidx.security:security-crypto`
  (`EncryptedSharedPreferences`), keyed by Android Keystore — not in plain
  SharedPreferences, not in the `Device` model that gets serialized to JSON.
- No camera credentials or video are ever sent to any server other than the
  camera/NVR itself. There is no analytics, telemetry, or remote logging in
  this codebase.
- `usesCleartextTraffic="true"` is required because most of this hardware
  generation only speaks HTTP (or HTTPS with a self-signed cert) on the LAN;
  the app warns before connecting over HTTP to anything that isn't a
  recognized private/local address (`NetworkGuard.isPrivateOrLocal`).
- Android's global security posture is not weakened — TLS validation still
  runs normally for everything outside the explicit self-signed-cert
  confirmation flow, which requires an explicit user tap every time a new
  untrusted cert is seen.
