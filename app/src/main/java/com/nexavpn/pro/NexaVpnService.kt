package com.nexavpn.pro

import android.content.Intent
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import hev.htproxy.TProxyService
import kotlinx.coroutines.runBlocking
import java.io.File

class NexaVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.muslim.vpnpro.START"
        const val ACTION_STOP = "com.muslim.vpnpro.STOP"
        private const val DNS_ADDR = "198.18.0.2"
        private const val REPORT_EVERY_MS = 30_000L
    }

    private var tunnel: Engine? = null
    private var pfd: ParcelFileDescriptor? = null

    @Volatile private var stopping = false
    @Volatile private var nativeStarted = false
    @Volatile private var proxyMode = false
    @Volatile private var unsentBytes = 0L
    @Volatile private var token: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn("Déconnecté")
        } else if (!VpnState.connecting && !VpnState.connected) {
            startVpn()
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        stopVpn("Permission VPN retirée")
    }

    override fun onDestroy() {
        stopVpn("Déconnecté")
        super.onDestroy()
    }

    // ------------------------------------------------------------------ démarrage

    private fun startVpn() {
        val session = Store(this).load()
        if (session == null) {
            VpnState.status = "Aucun token activé."
            stopSelf()
            return
        }
        token = session.token
        stopping = false
        unsentBytes = 0L
        VpnState.sessionBytes = 0L
        VpnState.downBps = 0L
        VpnState.upBps = 0L
        VpnState.connecting = true
        VpnState.status = "Connexion…"
        CrashLog.resetTrail()
        CrashLog.setRunning(true)
        VpnState.addLog("Connexion à ${session.serverName} (${session.type})…")

        Thread {
            try {
                val t: Engine = when (session.type) {
                "ssh" -> SshTunnel(TunnelConfig.from(session))
                "vmess", "vless", "trojan", "vitora" -> XrayEngine(this, session)
                "udp" -> UdpCustomEngine(session)
                "dnst", "slowdns" -> DnstEngine(session)
                else -> SshTunnel(TunnelConfig.from(session))
            }")
                }
                t.connect()
                tunnel = t
                VpnState.addLog("Tunnel ${session.type} établi")
                VpnState.addLog("Test du tunnel : " + checkTunnel(t.socksPort))

                // Le composant natif est d'abord testé dans un processus séparé : s'il plante, l'application survit.
                val nativeProblem = probeNative()
                val useNative = nativeProblem == null
                proxyMode = !useNative
                if (useNative) {
                    VpnState.addLog("Composant tun2socks : chargement testé, OK")
                } else {
                    VpnState.addLog("Composant tun2socks inutilisable : $nativeProblem")
                    CrashLog.recentSystemLog().takeLast(4).forEach { VpnState.addLog(it.takeLast(170)) }
                    if (Build.VERSION.SDK_INT < 29) {
                        throw IllegalStateException("Composant tun2socks inutilisable sur cet appareil : $nativeProblem")
                    }
                    VpnState.addLog("Mode proxy : seules les applications qui suivent le proxy du système passent par le tunnel.")
                }

                val prefs = Prefs(this)
                val mtu = prefs.mtu
                val builder = Builder()
                    .setSession("NEXA VPN PRO")
                    .setMtu(mtu)
                    .addAddress("198.18.0.1", 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer(DNS_ADDR)
                // Capture aussi l'IPv6 pour qu'elle ne fuie pas hors du VPN (elle sera simplement abandonnée).
                try {
                    builder.addAddress("fd00:1:fd00:1::1", 128)
                    builder.addRoute("::", 0)
                } catch (_: Exception) {
                }
                if (proxyMode && Build.VERSION.SDK_INT >= 29) {
                    ShareNet.start(t.socksPort, loopbackOnly = true)
                    builder.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", ShareNet.PORT))
                }
                // Le trafic de l'app elle-même (le tunnel) ne doit pas repasser dans le VPN.
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (_: Exception) {
                }
                // Applications choisies par l'utilisateur pour contourner le VPN.
                for (pkg in prefs.excluded) {
                    try {
                        builder.addDisallowedApplication(pkg)
                    } catch (_: Exception) {
                    }
                }

                VpnState.addLog("Création de l'interface VPN…")
                val fd = builder.establish() ?: throw IllegalStateException("Permission VPN refusée")
                pfd = fd

                val conf = File(cacheDir, "tproxy.yaml")
                val yaml = buildString {
                    append("misc:\n  task-stack-size: 81920\n")
                    append("tunnel:\n  mtu: $mtu\n")
                    append("socks5:\n  port: ${t.socksPort}\n  address: 127.0.0.1\n")
                    if (t.supportsUdp) append("  udp: 'udp'\n")
                    append("mapdns:\n  address: $DNS_ADDR\n  port: 53\n  network: 100.64.0.0\n")
                    append("  netmask: 255.192.0.0\n  cache-size: 10000\n")
                }
                conf.writeText(yaml)
                if (useNative) {
                    VpnState.addLog("Démarrage de tun2socks…")
                    nativeStarted = true
                    Thread {
                        try {
                            TProxyService.TProxyStartService(conf.absolutePath, fd.fd)
                        } catch (e: Throwable) {
                            stopVpn("Moteur tun2socks indisponible : " + (e.message ?: e.javaClass.simpleName))
                        }
                    }.start()
                }

                VpnState.connectedAt = System.currentTimeMillis()
                VpnState.connected = true
                VpnState.connecting = false
                VpnState.status = if (useNative) "Connecté" else "Connecté (mode proxy)"
                VpnState.addLog("VPN actif")
                monitor(t)
            } catch (e: Throwable) {
                VpnState.addLog("Erreur : " + e.javaClass.simpleName + " : " + (e.message ?: ""))
                try { stopVpn(friendly(e)) } catch (_: Throwable) {}
            }
        }.start()
    }

    // ------------------------------------------------------------------ vérifications du moteur

    /** Vérifie que le serveur laisse réellement passer du trafic (via le SOCKS local, sans passer par le VPN). */
    private fun checkTunnel(port: Int): String {
        val urls = listOf("http://cp.cloudflare.com/generate_204", "http://connectivitycheck.gstatic.com/generate_204")
        var last = ""
        for (u in urls) {
            val t0 = System.nanoTime()
            try {
                val px = java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress("127.0.0.1", port))
                val c = java.net.URL(u).openConnection(px) as java.net.HttpURLConnection
                c.connectTimeout = 6000
                c.readTimeout = 6000
                c.instanceFollowRedirects = false
                val code = c.responseCode
                c.disconnect()
                if (code in 200..399) return "OK (" + (System.nanoTime() - t0) / 1_000_000 + " ms)"
                last = "HTTP $code"
            } catch (e: Exception) {
                last = e.javaClass.simpleName + " " + (e.message ?: "")
            }
        }
        return "ÉCHEC ($last) — la connexion continue"
    }

    /** Charge la bibliothèque native dans un processus séparé. Retourne null si tout va bien, sinon la raison. */
    private fun probeNative(): String? {
        val f = File(filesDir, "probe_result.txt")
        try { f.delete() } catch (_: Exception) {}
        startService(Intent(this, ProbeService::class.java))
        val deadline = System.currentTimeMillis() + 7000
        while (System.currentTimeMillis() < deadline) {
            if (f.exists()) {
                val r = try { f.readText().trim() } catch (_: Exception) { "" }
                if (r == "ok") return null
                if (r.startsWith("error")) return r
            }
            try { Thread.sleep(150) } catch (_: InterruptedException) { return "interrompu" }
        }
        val last = try { if (f.exists()) f.readText().trim() else "" } catch (_: Exception) { "" }
        return if (last == "start") "le processus de test s'est arrêté pendant le chargement (plantage natif)"
        else "aucune réponse du processus de test"
    }

    // ------------------------------------------------------------------ surveillance, débits et consommation

    private fun monitor(t: Engine) {
        var last = 0L
        var lastTx = 0L
        var lastRx = 0L
        var lastReport = System.currentTimeMillis()
        val startedAt = System.currentTimeMillis()
        var stable = false
        var firstCheckDone = false
        while (!stopping) {
            try {
                Thread.sleep(1000)
            } catch (_: InterruptedException) {
                return
            }
            if (stopping) return
            if (!stable && System.currentTimeMillis() - startedAt > 8000) {
                stable = true
                CrashLog.setRunning(false)
            }
            if (!t.isAlive()) {
                stopVpn("Connexion au serveur perdue.")
                return
            }
            val stats = if (nativeStarted) try { TProxyService.TProxyGetStats() } catch (_: Throwable) { null } else null
            if (stats != null && stats.size >= 4) {
                val tx = stats[1]
                val rx = stats[3]
                VpnState.upBps = (tx - lastTx).coerceAtLeast(0L) * 8
                VpnState.downBps = (rx - lastRx).coerceAtLeast(0L) * 8
                lastTx = tx
                lastRx = rx
                val total = tx + rx
                unsentBytes += (total - last).coerceAtLeast(0L)
                last = total
                VpnState.sessionBytes = total
            }
            if (proxyMode && !nativeStarted) {
                val total = ShareNet.bytes.get()
                val d = (total - last).coerceAtLeast(0L)
                VpnState.downBps = d * 8
                VpnState.upBps = 0L
                unsentBytes += d
                last = total
                VpnState.sessionBytes = total
            }
            // Premier contrôle : le système a-t-il envoyé des paquets à tun2socks ?
            if (nativeStarted && !firstCheckDone && System.currentTimeMillis() - startedAt > 10000) {
                firstCheckDone = true
                val st = try { TProxyService.TProxyGetStats() } catch (_: Throwable) { null }
                if (st != null && st.size >= 4 && st[0] + st[2] == 0L) {
                    VpnState.addLog("tun2socks : aucun paquet après 10 s (normal si aucune application n'utilise Internet)")
                } else {
                    VpnState.addLog("tun2socks : le trafic circule")
                }
            }
            val now = System.currentTimeMillis()
            if (unsentBytes > 0 && now - lastReport >= REPORT_EVERY_MS) {
                lastReport = now
                val status = sendUsage()
                if (status != null && status != "ok") {
                    stopVpn(statusText(status))
                    return
                }
            }
        }
    }

    /** Envoie la consommation non envoyée. Retourne le statut du serveur, ou null en cas d'échec réseau. */
    private fun sendUsage(): String? {
        val tk = token ?: return null
        val bytes = unsentBytes
        if (bytes <= 0) return "ok"
        return try {
            val r = runBlocking { Api.reportUsage(tk, Device.id(this@NexaVpnService), bytes) }
            unsentBytes -= bytes
            VpnState.addLog("Consommation envoyée : ${fmtBytes(bytes)}")
            r.optString("status")
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ arrêt

    @Synchronized
    private fun stopVpn(message: String) {
        if (stopping) return
        stopping = true
        CrashLog.setRunning(false)
        ShareNet.stop()
        if (nativeStarted) {
            nativeStarted = false
            try { TProxyService.TProxyStopService() } catch (_: Throwable) {}
        }
        try { pfd?.close() } catch (_: Exception) {}
        pfd = null
        tunnel?.stop()
        tunnel = null
        VpnState.connected = false
        VpnState.connecting = false
        VpnState.downBps = 0L
        VpnState.upBps = 0L
        VpnState.status = message
        VpnState.addLog(message)

        // Dernier envoi de la consommation, sans bloquer l'arrêt.
        if (unsentBytes > 0) {
            Thread { sendUsage() }.start()
        }
        stopSelf()
    }

    private fun friendly(e: Throwable): String {
        val m = e.message ?: e.javaClass.simpleName
        return when {
            m.contains("Auth fail", true) || m.contains("USERAUTH", true) -> "Identifiants SSH refusés par le serveur."
            m.contains("timeout", true) || m.contains("timed out", true) -> "Délai dépassé : serveur injoignable."
            m.contains("Trust anchor", true) || m.contains("CertPath", true) ->
                "Certificat TLS du serveur non approuvé. Contacte ton revendeur."
            m.contains("Proxy", true) || m.contains("Xray", true) -> m
            else -> "Échec de connexion : $m"
        }
    }

    private fun statusText(status: String): String = when (status) {
        "data_limit" -> "Limite de données atteinte. Déconnecté."
        "expired" -> "Ce token a expiré. Déconnecté."
        "revoked" -> "Ce token a été révoqué. Déconnecté."
        else -> "Déconnecté ($status)."
    }
}
