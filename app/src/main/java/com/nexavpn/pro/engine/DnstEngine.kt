package com.nexavpn.pro.engine

import android.content.Context
import android.util.Log
import com.nexavpn.pro.model.ServerConfig

class DnstEngine : Engine {
    override val socksPort: Int = 1080
    override val supportsUdp: Boolean = true
    override val protocolName: String = "SlowDNS / DNST"

    @Volatile private var running = false

    override fun connect(context: Context, server: ServerConfig): Boolean {
        Log.i("DnstEngine", "Démarrage tunnel SlowDNS via ${server.host}")
        running = true
        return true
    }

    override fun isAlive(): Boolean = running

    override fun stop() {
        running = false
    }
}
