package com.nexavpn.pro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed class Act {
    data class Ok(val session: Session) : Act()
    data class Fail(val status: String) : Act()
    data class NetErr(val msg: String) : Act()
}

object Api {
    private suspend fun rpc(fn: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val c = URL("${Config.SUPABASE_URL}/rest/v1/rpc/$fn").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("apikey", Config.SUPABASE_KEY)
            c.setRequestProperty("Authorization", "Bearer ${Config.SUPABASE_KEY}")
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("HTTP $code")
            JSONObject(text)
        } finally {
            c.disconnect()
        }
    }

    suspend fun activate(token: String, deviceId: String): Act = try {
        val r = rpc(
            "activate_vpn_token",
            JSONObject().put("p_code", token.trim()).put("p_device_id", deviceId.trim())
        )
        if (r.optBoolean("ok", false)) {
            Act.Ok(Session(token.trim(), r))
        } else {
            val err = r.optString("error", "Token invalide")
            Act.Fail(err)
        }
    } catch (e: Exception) {
        Act.NetErr(e.message ?: "Erreur de connexion réseau")
    }

    suspend fun reportUsage(token: String, deviceId: String, bytes: Long): JSONObject = rpc(
        "report_usage",
        JSONObject().put("p_code", token.trim()).put("p_device_id", deviceId.trim()).put("p_bytes", bytes)
    )
}
