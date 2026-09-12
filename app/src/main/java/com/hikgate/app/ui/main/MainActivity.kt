package com.hikgate.app.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.hikgate.app.data.DeviceRepository
import com.hikgate.app.databinding.ActivityMainBinding
import com.hikgate.app.ui.adddevice.AddDeviceActivity
import com.hikgate.app.ui.device.DeviceActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: DeviceRepository
    private lateinit var adapter: DeviceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        repository = DeviceRepository(this)

        adapter = DeviceAdapter(emptyList()) { device ->
            val intent = Intent(this, DeviceActivity::class.java)
            intent.putExtra(DeviceActivity.EXTRA_DEVICE_ID, device.id)
            startActivity(intent)
        }
        binding.deviceList.layoutManager = LinearLayoutManager(this)
        binding.deviceList.adapter = adapter

        binding.fabAddDevice.setOnClickListener {
            startActivity(Intent(this, AddDeviceActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val devices = repository.getAll()
        adapter.submitList(devices)
        binding.emptyState.visibility = if (devices.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.deviceList.visibility = if (devices.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }
}
