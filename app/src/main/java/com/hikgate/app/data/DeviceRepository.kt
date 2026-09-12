package com.hikgate.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Persists device metadata (name/host/port/etc — nothing sensitive) as JSON in
 * regular SharedPreferences. Credentials live separately in [com.hikgate.app.security.CredentialStore].
 * Splitting these means a bug pretty-printing "devices" for debugging can never
 * leak a password.
 */
class DeviceRepository(context: Context) {

    private val prefs = context.getSharedPreferences("hikgate_devices", Context.MODE_PRIVATE)

    fun getAll(): List<Device> {
        val raw = prefs.getString(KEY_DEVICES, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i -> fromJson(arr.getJSONObject(i)) }
    }

    fun get(id: String): Device? = getAll().firstOrNull { it.id == id }

    fun save(device: Device): Device {
        val list = getAll().toMutableList()
        val existingIndex = list.indexOfFirst { it.id == device.id }
        val toSave = if (device.id.isBlank()) device.copy(id = UUID.randomUUID().toString()) else device
        if (existingIndex >= 0) list[existingIndex] = toSave else list.add(toSave)
        persist(list)
        return toSave
    }

    fun delete(id: String) {
        persist(getAll().filterNot { it.id == id })
    }

    private fun persist(list: List<Device>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY_DEVICES, arr.toString()).apply()
    }

    private fun toJson(d: Device): JSONObject = JSONObject().apply {
        put("id", d.id)
        put("name", d.name)
        put("host", d.host)
        put("httpPort", d.httpPort)
        put("useHttps", d.useHttps)
        put("rtspPort", d.rtspPort)
        put("defaultChannel", d.defaultChannel)
        put("lastKnownCapability", d.lastKnownCapability?.name)
    }

    private fun fromJson(o: JSONObject): Device = Device(
        id = o.getString("id"),
        name = o.getString("name"),
        host = o.getString("host"),
        httpPort = o.optInt("httpPort", 80),
        useHttps = o.optBoolean("useHttps", false),
        rtspPort = o.optInt("rtspPort", 554),
        defaultChannel = o.optString("defaultChannel", "101"),
        lastKnownCapability = o.optString("lastKnownCapability", null)
            ?.let { runCatching { DeviceCapability.valueOf(it) }.getOrNull() }
    )

    companion object {
        private const val KEY_DEVICES = "devices_json"
    }
}
