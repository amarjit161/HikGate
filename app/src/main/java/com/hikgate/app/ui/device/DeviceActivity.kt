package com.hikgate.app.ui.device

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hikgate.app.data.Device
import com.hikgate.app.data.DeviceCapability
import com.hikgate.app.data.DeviceRepository
import com.hikgate.app.databinding.ActivityDeviceBinding
import com.hikgate.app.network.HikvisionProbe
import com.hikgate.app.network.ProbeResult
import com.hikgate.app.security.CredentialStore
import com.hikgate.app.ui.browser.BrowserActivity
import com.hikgate.app.ui.liveview.LiveViewActivity
import com.hikgate.app.util.NetworkGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceBinding
    private lateinit var repository: DeviceRepository
    private lateinit var credentials: CredentialStore
    private val probe = HikvisionProbe()
    private lateinit var device: Device

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        repository = DeviceRepository(this)
        credentials = CredentialStore(this)

        val id = intent.getStringExtra(EXTRA_DEVICE_ID) ?: run { finish(); return }
        val loaded = repository.get(id) ?: run { finish(); return }
        device = loaded
        binding.toolbar.title = device.name
        binding.deviceStatus.text = "${device.host}:${device.httpPort}${if (device.useHttps) " (https)" else ""}"

        binding.btnOpenWeb.setOnClickListener { openWeb() }
        binding.btnLiveView.setOnClickListener { openLiveView() }
        binding.btnTest.setOnClickListener { runProbe() }
        binding.btnDelete.setOnClickListener { confirmDelete() }

        runProbe()
    }

    private fun openWeb() {
        maybeWarnHttp {
            val i = Intent(this, BrowserActivity::class.java)
            i.putExtra(BrowserActivity.EXTRA_URL, device.httpBaseUrl)
            i.putExtra(BrowserActivity.EXTRA_DEVICE_ID, device.id)
            startActivity(i)
        }
    }

    private fun openLiveView() {
        val username = credentials.getUsername(device.id).orEmpty()
        val password = credentials.getPassword(device.id).orEmpty()
        if (username.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No credentials saved")
                .setMessage("Add a username/password for this device before starting Live View.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val i = Intent(this, LiveViewActivity::class.java)
        i.putExtra(LiveViewActivity.EXTRA_RTSP_URL, device.rtspUrl(username, password))
        i.putExtra(LiveViewActivity.EXTRA_TITLE, device.name)
        startActivity(i)
    }

    private fun maybeWarnHttp(onProceed: () -> Unit) {
        if (device.useHttps || NetworkGuard.isPrivateOrLocal(device.host)) {
            onProceed()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(com.hikgate.app.R.string.warn_http_title))
            .setMessage(getString(com.hikgate.app.R.string.warn_http_body))
            .setPositiveButton("Continue") { _, _ -> onProceed() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runProbe() {
        binding.capabilityNote.text = "Checking web interface..."
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { probe.probe(device.httpBaseUrl) }
            binding.capabilityNote.text = describe(result)
            if (result is ProbeResult.Success) {
                repository.save(device.copy(lastKnownCapability = result.capability))
                if (result.capability == DeviceCapability.ACTIVEX_OR_NPAPI) {
                    AlertDialog.Builder(this@DeviceActivity)
                        .setTitle(com.hikgate.app.R.string.activex_detected_title)
                        .setMessage(com.hikgate.app.R.string.activex_detected_body)
                        .setPositiveButton("Use Live View instead") { _, _ -> openLiveView() }
                        .setNegativeButton("Open web anyway", null)
                        .show()
                }
            }
        }
    }

    private fun describe(result: ProbeResult): String = when (result) {
        is ProbeResult.Success -> "${result.capability}: ${result.detail}"
        is ProbeResult.Failure -> "${result.reason}: ${result.message}"
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete ${device.name}?")
            .setMessage("This removes the saved device and its stored credentials.")
            .setPositiveButton("Delete") { _, _ ->
                credentials.clearCredentials(device.id)
                repository.delete(device.id)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        const val EXTRA_DEVICE_ID = "extra_device_id"
    }
}
