package com.nexavpn.pro

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream

data class NexaConfig(val token: String, val name: String, val device: String?)

object NexaFile {
    /** Format : « NEXA1: » + base64 d'un JSON {"app":"nexa","v":1,"token":"...","name":"...","device":"NEXA-..."}. */
    fun parse(raw: String?): NexaConfig? {
        var s = raw?.trim() ?: return null
        if (s.isEmpty()) return null
        if (s.startsWith("NEXA1:") || s.startsWith("MVPN1:")) {
            s = try {
                val prefix = if (s.startsWith("NEXA1:")) "NEXA1:" else "MVPN1:"
                String(Base64.decode(s.removePrefix(prefix).trim(), Base64.DEFAULT), Charsets.UTF_8)
            } catch (_: Exception) {
                return null
            }
        }
        return try {
            val j = JSONObject(s)
            val token = if (j.isNull("token")) "" else j.getString("token").trim()
            if (token.isEmpty()) return null
            val name = if (j.isNull("name")) "" else j.getString("name")
            val device = if (j.isNull("device")) null else j.getString("device").trim().ifEmpty { null }
            NexaConfig(token, name, device)
        } catch (_: Exception) {
            null
        }
    }

    fun readUri(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(2048)
            var total = 0
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                total += n
                if (total > 16384) return@use null
                out.write(buf, 0, n)
            }
            String(out.toByteArray(), Charsets.UTF_8)
        }
    } catch (_: Exception) {
        null
    }
}
