package com.nexavpn.pro.engine

import android.content.Context
import android.util.Log
import com.nexavpn.pro.model.ServerConfig
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpEngine : Engine {
    override val socksPort: Int = 1080
    override val supportsUdp: Boolean = true
    override val protocolName: String = "UDP Custom"

    @Volatile private var running = false
    private var udpSocket: DatagramSocket? = null

    override fun connect(context: Context, server: ServerConfig): Boolean {
        return try {
            Log.i("UdpEngine", "Connexion UDP Custom vers ${server.host}:${server.port}")
            udpSocket = DatagramSocket()
            udpSocket?.connect(InetAddress.getByName(server.host), server.port)
            running = true
            true
        } catch (e: Exception) {
            Log.e("UdpEngine", "Erreur UDP", e)
            stop()
            false
        }
    }

    override fun isAlive(): Boolean = running && (udpSocket?.isConnected == true)

    override fun stop() {
        running = false
        try { udpSocket?.close() } catch (_: Exception) {}
        udpSocket = null
    }
}
