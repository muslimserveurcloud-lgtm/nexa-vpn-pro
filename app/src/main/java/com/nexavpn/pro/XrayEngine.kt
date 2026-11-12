package com.nexavpn.pro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * VMess / VLESS / Trojan via le noyau Xray (binaire embarqué sous le nom libxray.so).
 * Xray expose un SOCKS5 sur 127.0.0.1:1080, comme le moteur SSH.
 */
class XrayEngine(private val ctx: Context, private val s: Session) : Engine {

    override val socksPort = 1080
    override val supportsUdp = true

    private var proc: Process? = null
    private val logBuf = StringBuilder()

    override fun connect() {
        val bin = File(ctx.applicationInfo.nativeLibraryDir, "libxray.so")
        if (!bin.exists()) throw IOException("Noyau Xray absent de l'application.")
        val cfg = File(ctx.cacheDir, "xray.json")
        cfg.writeText(buildConfig().toString())

        val p = ProcessBuilder(bin.absolutePath, "run", "-c", cfg.absolutePath)
            .redirectErrorStream(true)
            .start()
        proc = p
        Thread {
            try {
                p.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(logBuf) {
                        logBuf.append(line).append('\n')
                        if (logBuf.length > 2000) logBuf.delete(0, logBuf.length - 2000)
                    }
                }
            } catch (_: Exception) {
            }
        }.start()

        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (!alive(p)) throw IOException("Xray s'est arrêté. " + lastLog())
            try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", socksPort), 300) }
                return
            } catch (_: Exception) {
                Thread.sleep(250)
            }
        }
        stop()
        throw IOException("Xray ne démarre pas. " + lastLog())
    }

    override fun isAlive(): Boolean = proc?.let { alive(it) } ?: false

    override fun stop() {
        try { proc?.destroy() } catch (_: Exception) {}
        proc = null
    }

    private fun alive(p: Process): Boolean = try {
        p.exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }

    private fun lastLog(): String = synchronized(logBuf) { logBuf.toString().trim().takeLast(300) }

    // ------------------------------------------------------------------ génération de la config Xray

    private fun str(o: JSONObject, k: String, def: String = ""): String =
        if (o.isNull(k)) def else o.getString(k).ifEmpty { def }

    private fun resolve(host: String): String = try {
        InetAddress.getByName(host).hostAddress ?: host
    } catch (_: Exception) {
        host
    }

    private fun csv(v: String): JSONArray {
        val a = JSONArray()
        v.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { a.put(it) }
        return a
    }

    private fun buildConfig(): JSONObject {
        val c = s.config
        val k = s.credentials
        val host = s.host
        // On résout l'adresse du serveur ici (l'app est exclue du VPN) pour que Xray n'ait pas à le faire.
        val address = resolve(host)

        val out = JSONObject().put("tag", "proxy").put("protocol", s.type)
        when (s.type) {
            "vmess" -> out.put(
                "settings",
                JSONObject().put(
                    "vnext",
                    JSONArray().put(
                        JSONObject().put("address", address).put("port", s.port).put(
                            "users",
                            JSONArray().put(
                                JSONObject()
                                    .put("id", str(k, "uuid"))
                                    .put("alterId", c.optInt("alterId", 0))
                                    .put("security", str(c, "cipher", "auto"))
                            )
                        )
                    )
                )
            )
            "vless" -> {
                val user = JSONObject().put("id", str(k, "uuid")).put("encryption", "none")
                val flow = str(c, "flow")
                if (flow.isNotEmpty()) user.put("flow", flow)
                out.put(
                    "settings",
                    JSONObject().put(
                        "vnext",
                        JSONArray().put(
                            JSONObject().put("address", address).put("port", s.port)
                                .put("users", JSONArray().put(user))
                        )
                    )
                )
            }
            "trojan" -> out.put(
                "settings",
                JSONObject().put(
                    "servers",
                    JSONArray().put(
                        JSONObject().put("address", address).put("port", s.port)
                            .put("password", str(k, "password"))
                    )
                )
            )
            else -> throw IOException("Type non pris en charge : ${s.type}")
        }

        val network = str(c, "network", "tcp")
        val security = str(c, "security", if (s.type == "trojan") "tls" else "none")
        val sni = str(c, "sni", host)
        val hostHeader = str(c, "host", sni)
        val path = str(c, "path", "/")

        val stream = JSONObject().put("network", network).put("security", security)

        if (security == "tls") {
            val tls = JSONObject().put("serverName", sni).put("allowInsecure", c.optBoolean("allowInsecure", false))
            val alpn = csv(str(c, "alpn"))
            if (alpn.length() > 0) tls.put("alpn", alpn)
            val fp = str(c, "fp")
            if (fp.isNotEmpty()) tls.put("fingerprint", fp)
            stream.put("tlsSettings", tls)
        } else if (security == "reality") {
            stream.put(
                "realitySettings",
                JSONObject()
                    .put("serverName", sni)
                    .put("fingerprint", str(c, "fp", "chrome"))
                    .put("publicKey", str(c, "pbk"))
                    .put("shortId", str(c, "sid"))
                    .put("spiderX", str(c, "spx"))
            )
        }

        when (network) {
            "ws" -> stream.put(
                "wsSettings",
                JSONObject().put("path", path).put("headers", JSONObject().put("Host", hostHeader))
            )
            "grpc" -> stream.put(
                "grpcSettings",
                JSONObject().put("serviceName", str(c, "serviceName", str(c, "path")))
            )
            "httpupgrade" -> stream.put(
                "httpupgradeSettings",
                JSONObject().put("path", path).put("host", hostHeader)
            )
            "h2", "http" -> stream.put(
                "httpSettings",
                JSONObject().put("host", csv(hostHeader)).put("path", path)
            )
            "tcp" -> if (str(c, "headerType") == "http") {
                val req = JSONObject()
                    .put("version", "1.1")
                    .put("method", "GET")
                    .put("path", JSONArray().put(path))
                    .put(
                        "headers",
                        JSONObject()
                            .put("Host", csv(hostHeader))
                            .put("User-Agent", JSONArray().put("Mozilla/5.0 (Linux; Android 13)"))
                            .put("Connection", JSONArray().put("keep-alive"))
                    )
                stream.put(
                    "tcpSettings",
                    JSONObject().put("header", JSONObject().put("type", "http").put("request", req))
                )
            }
        }
        out.put("streamSettings", stream)

        val inbound = JSONObject()
            .put("tag", "socks-in")
            .put("listen", "127.0.0.1")
            .put("port", socksPort)
            .put("protocol", "socks")
            .put("settings", JSONObject().put("auth", "noauth").put("udp", true).put("ip", "127.0.0.1"))

        return JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("inbounds", JSONArray().put(inbound))
            .put(
                "outbounds",
                JSONArray().put(out).put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
            )
    }
}
