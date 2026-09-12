package com.hikgate.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores camera usernames/passwords using EncryptedSharedPreferences, which is
 * backed by a key generated and held inside Android Keystore (AES-256-GCM for
 * values, AES-256-SIV for keys). The plaintext password never touches disk and
 * never leaves the device.
 *
 * IMPORTANT: nothing in this class or its callers should ever pass a password
 * to Log.*, an analytics call, or a network request other than the camera's
 * own login/RTSP endpoint on the local network.
 */
class CredentialStore(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            "hikgate_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveCredentials(deviceId: String, username: String, password: String) {
        prefs.edit()
            .putString(usernameKey(deviceId), username)
            .putString(passwordKey(deviceId), password)
            .apply()
    }

    fun getUsername(deviceId: String): String? = prefs.getString(usernameKey(deviceId), null)

    fun getPassword(deviceId: String): String? = prefs.getString(passwordKey(deviceId), null)

    fun clearCredentials(deviceId: String) {
        prefs.edit()
            .remove(usernameKey(deviceId))
            .remove(passwordKey(deviceId))
            .apply()
    }

    private fun usernameKey(deviceId: String) = "user_$deviceId"
    private fun passwordKey(deviceId: String) = "pass_$deviceId"
}
