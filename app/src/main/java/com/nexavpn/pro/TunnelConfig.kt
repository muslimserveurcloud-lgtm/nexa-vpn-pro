package com.nexavpn.pro

import org.json.JSONObject

/** Paramètres de connexion SSH lus dans la config du serveur et ses identifiants. */
data class TunnelConfig(
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
    val payload: String,
    val sni: String,
    val proxyHost: String,
    val proxyPort: Int,
    val tlsInsecure: Boolean
) {
    companion object {
        private fun str(o: JSONObject, k: String): String = if (o.isNull(k)) "" else o.getString(k)

        fun from(s: Session): TunnelConfig {
            val c = s.config
            val k = s.credentials
            val proxy = str(c, "proxy").trim()
            var ph = ""
            var pp = 0
            if (proxy.isNotEmpty()) {
                val i = proxy.lastIndexOf(':')
                if (i > 0) {
                    ph = proxy.substring(0, i)
                    pp = proxy.substring(i + 1).toIntOrNull() ?: 8080
                } else {
                    ph = proxy
                    pp = 8080
                }
            }
            return TunnelConfig(
                host = s.host,
                port = s.port,
                user = str(k, "username"),
                password = str(k, "password"),
                payload = str(c, "payload"),
                sni = str(c, "sni").trim(),
                proxyHost = ph,
                proxyPort = pp,
                tlsInsecure = c.optBoolean("tls_insecure", true)
            )
        }
    }
}
