package com.nexavpn.pro

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Réponse de activate_vpn_token : serveur, configuration, expiration et quotas. */
data class Session(val token: String, val raw: JSONObject, var selectedServerIndex: Int = 0) {
    val servers: JSONArray get() = raw.optJSONArray("servers") ?: JSONArray()

    private val currentServer: JSONObject
        get() = servers.optJSONObject(selectedServerIndex)
            ?: (if (servers.length() > 0) servers.getJSONObject(0) else raw.optJSONObject("server") ?: JSONObject())

    val serverName: String get() = currentServer.optString("name", "NEXA Node")
    val type: String get() = currentServer.optString("protocol", currentServer.optString("type", "ssh")).lowercase()
    val host: String get() = currentServer.optString("host", "127.0.0.1")
    val port: Int get() = currentServer.optInt("port", 443)
    val country: String get() = if (currentServer.isNull("country")) "" else currentServer.getString("country")
    val network: String get() = currentServer.optString("network", "")

    val config: JSONObject
        get() {
            val c = currentServer.opt("config")
            return when (c) {
                is JSONObject -> c
                is String -> try { JSONObject(c) } catch (_: Exception) { JSONObject() }
                else -> currentServer.optJSONObject("config") ?: JSONObject()
            }
        }

    val credentials: JSONObject
        get() {
            val obj = JSONObject()
            if (!currentServer.isNull("username")) obj.put("username", currentServer.getString("username"))
            if (!currentServer.isNull("password")) obj.put("password", currentServer.getString("password"))
            if (!currentServer.isNull("uuid")) obj.put("uuid", currentServer.getString("uuid"))
            val cred = currentServer.optJSONObject("credentials")
            if (cred != null) {
                val keys = cred.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    obj.put(k, cred.get(k))
                }
            }
            return obj
        }

    val limitBytes: Long?
        get() = if (raw.isNull("data_limit_bytes") || raw.optLong("data_limit_bytes", 0L) <= 0L) null
        else raw.getLong("data_limit_bytes")
    val usedBytes: Long get() = raw.optLong("data_used_bytes", 0L)
    val expiresAt: String get() = raw.optString("expires_at", "")

    /** Jours restants avant expiration */
    fun daysLeft(): Long? = try {
        if (expiresAt.isEmpty()) null
        else {
            val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            f.timeZone = TimeZone.getTimeZone("UTC")
            val d = f.parse(expiresAt.take(19))
            if (d == null) null else Math.ceil((d.time - System.currentTimeMillis()) / 86400000.0).toLong()
        }
    } catch (e: Exception) {
        null
    }
}
