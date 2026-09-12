package com.hikgate.app.ui.adddevice

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hikgate.app.data.Device
import com.hikgate.app.data.DeviceRepository
import com.hikgate.app.databinding.ActivityAddDeviceBinding
import com.hikgate.app.network.HikvisionProbe
import com.hikgate.app.network.ProbeResult
import com.hikgate.app.security.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddDeviceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddDeviceBinding
    private lateinit var repository: DeviceRepository
    private lateinit var credentials: CredentialStore
    private val probe = HikvisionProbe()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        repository = DeviceRepository(this)
        credentials = CredentialStore(this)

        binding.btnTestConnection.setOnClickListener { testConnection() }
        binding.btnSave.setOnClickListener { saveDevice() }
    }

    private fun buildDraftDevice(): Device? {
        val host = binding.inputHost.text?.toString()?.trim().orEmpty()
        if (host.isEmpty()) {
            binding.inputHost.error = "Required"
            return null
        }
        val port = binding.inputPort.text?.toString()?.toIntOrNull() ?: 80
        val rtspPort = binding.inputRtspPort.text?.toString()?.toIntOrNull() ?: 554
        val channel = binding.inputChannel.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: "101"
        val name = binding.inputName.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: host

        return Device(
            id = "",
            name = name,
            host = host,
            httpPort = port,
            useHttps = binding.switchHttps.isChecked,
            rtspPort = rtspPort,
            defaultChannel = channel
        )
    }

    private fun testConnection() {
        val draft = buildDraftDevice() ?: return
        binding.probeResult.visibility = android.view.View.VISIBLE
        binding.probeResult.text = "Probing ${draft.httpBaseUrl} ..."

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { probe.probe(draft.httpBaseUrl) }
            binding.probeResult.text = describe(result)
        }
    }

    private fun describe(result: ProbeResult): String = when (result) {
        is ProbeResult.Success -> {
            val isapi = if (result.isapiReachable) "ISAPI reachable — native API control is available."
                        else "ISAPI not reachable at the standard path."
            "Detected: ${result.capability}\n${result.detail}\n$isapi"
        }
        is ProbeResult.Failure -> "Could not classify interface (${result.reason}): ${result.message}\n" +
            "You can still save this device and try Live View directly via RTSP."
    }

    private fun saveDevice() {
        val draft = buildDraftDevice() ?: return
        val username = binding.inputUsername.text?.toString().orEmpty()
        val password = binding.inputPassword.text?.toString().orEmpty()

        val saved = repository.save(draft)
        if (username.isNotEmpty() || password.isNotEmpty()) {
            // Credentials are written straight to Keystore-backed encrypted storage,
            // never logged, never included in the Device object, never sent anywhere
            // except this device's own login/RTSP endpoints.
            credentials.saveCredentials(saved.id, username, password)
        }
        finish()
    }
}
