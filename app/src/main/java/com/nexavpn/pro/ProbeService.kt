package com.nexavpn.pro

import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.io.File

/**
 * Service de test qui tourne dans un PROCESSUS SÉPARÉ (":probe") : il charge la bibliothèque native tun2socks.
 * Si elle fait planter le processus, seul ce processus de test meurt : l'application et le VPN continuent
 * et savent précisément que le composant natif est inutilisable.
 */
class ProbeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val f = File(filesDir, "probe_result.txt")
        try {
            f.writeText("start")
            Class.forName("hev.htproxy.TProxyService")
            f.writeText("ok")
        } catch (t: Throwable) {
            try {
                f.writeText("error: " + t.javaClass.simpleName + " " + (t.message ?: ""))
            } catch (_: Throwable) {
            }
        }
        stopSelf()
        return START_NOT_STICKY
    }
}
