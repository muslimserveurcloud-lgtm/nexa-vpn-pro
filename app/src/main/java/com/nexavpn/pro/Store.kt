package com.nexavpn.pro

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/** Stockage chiffré (Android Keystore) du token et de la configuration reçue. */
class Store(ctx: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        ctx,
        "mvp_secure",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(): Session? {
        val token = prefs.getString("token", null) ?: return null
        val raw = prefs.getString("raw", null) ?: return null
        return try {
            Session(token, JSONObject(raw))
        } catch (e: Exception) {
            null
        }
    }

    fun save(s: Session) {
        prefs.edit().putString("token", s.token).putString("raw", s.raw.toString()).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
