package com.nexavpn.pro

import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.Locale

/** Logique des utilitaires (IP Finder, Speedtest, Short URL, Response checker). Bloquant : à appeler hors du thread UI. */
object Tools {

    fun ipInfo(): String {
        val j = JSONObject(Net.getText("https://ipwho.is/"))
        if (!j.optBoolean("success", true)) return "Service indisponible : " + j.optString("message")
        val conn = j.optJSONObject("connection")
        return buildString {
            append("IP : ").append(j.optString("ip")).append('\n')
            append("Pays : ").append(j.optString("country")).append('\n')
            append("Ville : ").append(j.optString("city")).append('\n')
            append("Opérateur : ").append(conn?.optString("isp") ?: "inconnu")
        }
    }

    fun shortUrl(url: String): String {
        val r = Net.getText("https://is.gd/create.php?format=simple&url=" + URLEncoder.encode(url, "UTF-8")).trim()
        return r
    }

    private fun mbps(bytes: Long, nanos: Long): String {
        if (nanos <= 0) return "n/a"
        val v = bytes * 8.0 / (nanos / 1e9) / 1e6
        return String.format(Locale.FRANCE, "%.1f Mb/s", v)
    }

    fun speedtest(update: (String) -> Unit): String {
        update("Mesure du ping…")
        val ping = try { "${Net.connectMs("speed.cloudflare.com", 443)} ms" } catch (e: Exception) { "échec" }

        update("Test de téléchargement…")
        val down = try {
            val c = Net.open("https://speed.cloudflare.com/__down?bytes=15000000")
            val t0 = System.nanoTime()
            var n = 0L
            try {
                val buf = ByteArray(65536)
                c.inputStream.use { ins ->
                    while (true) {
                        val r = ins.read(buf)
                        if (r < 0) break
                        n += r
                        if (System.nanoTime() - t0 > 10_000_000_000L) break
                    }
                }
            } finally {
                c.disconnect()
            }
            mbps(n, System.nanoTime() - t0)
        } catch (e: Exception) {
            "échec (" + (e.message ?: "réseau") + ")"
        }

        update("Test d'envoi…")
        val up = try {
            val data = ByteArray(3_000_000)
            val c = Net.open("https://speed.cloudflare.com/__up")
            c.requestMethod = "POST"
            c.doOutput = true
            c.setFixedLengthStreamingMode(data.size)
            val t0 = System.nanoTime()
            try {
                c.outputStream.use { it.write(data) }
                c.responseCode
            } finally {
                c.disconnect()
            }
            mbps(data.size.toLong(), System.nanoTime() - t0)
        } catch (e: Exception) {
            "échec (" + (e.message ?: "réseau") + ")"
        }

        val via = if (VpnState.connected) "via le VPN" else "sans VPN"
        return "Ping : $ping\nTéléchargement : $down\nEnvoi : $up\n($via)"
    }

    /** Envoie un payload brut (avec TLS/SNI optionnel) et affiche le début de la réponse du serveur. */
    fun checkResponse(host: String, port: Int, sni: String, payload: String): String {
        val raw = Socket()
        try {
            raw.connect(InetSocketAddress(host, port), 8000)
            raw.soTimeout = 8000
            var s: Socket = raw
            if (sni.isNotEmpty()) s = Net.tls(raw, sni, port, true)
            val out = s.getOutputStream()
            val text = expandPayload(payload, host, port)
            out.write(text.toByteArray(Charsets.ISO_8859_1))
            out.flush()
            val buf = ByteArray(2048)
            val n = try { s.getInputStream().read(buf) } catch (e: SocketTimeoutException) { -1 }
            return if (n <= 0) "Aucune réponse (délai dépassé ou connexion fermée)."
            else String(buf, 0, n, Charsets.ISO_8859_1).take(1500)
        } finally {
            try { raw.close() } catch (_: Exception) {}
        }
    }
}
