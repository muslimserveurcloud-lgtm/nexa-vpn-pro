package com.nexavpn.pro.engine

import android.content.Context
import android.util.Log
import com.nexavpn.pro.model.ServerConfig

class VitoraEngine : Engine {
    override val socksPort: Int = 1080
    override val supportsUdp: Boolean = true
    override val protocolName: String = "Vitora / VLESS"

    @Volatile private var running = false

    override fun connect(context: Context, server: ServerConfig): Boolean {
        Log.i("VitoraEngine", "Démarrage moteur Vitora vers ${server.host}:${server.port}")
        running = true
        return true
    }

    override fun isAlive(): Boolean = running

    override fun stop() {
        running = false
    }
}
