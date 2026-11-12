package com.nexavpn.pro

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SNIServerName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Requêtes réseau des utilitaires. Quand le VPN est connecté, elles passent par le proxy SOCKS local :
 * l'application est exclue du VPN, donc sans cela on mesurerait la connexion sans tunnel.
 */
object Net {
    private const val SOCKS_PORT = 1080

    private fun proxy(): Proxy? =
        if (VpnState.connected) Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", SOCKS_PORT)) else null

    fun open(url: String): HttpURLConnection {
        val u = URL(url)
        val px = proxy()
        val c = (if (px != null) u.openConnection(px) else u.openConnection()) as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "MuslimVPNPro/0.4")
        return c
    }

    fun getText(url: String): String {
        val c = open(url)
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Durée d'établissement d'une connexion TCP, en millisecondes. */
    fun connectMs(host: String, port: Int, timeout: Int = 5000): Long {
        val px = proxy()
        val s = if (px != null) Socket(px) else Socket()
        val t0 = System.nanoTime()
        try {
            val addr = if (px != null) InetSocketAddress.createUnresolved(host, port) else InetSocketAddress(host, port)
            s.connect(addr, timeout)
            return (System.nanoTime() - t0) / 1_000_000
        } finally {
            try { s.close() } catch (_: Exception) {}
        }
    }

    fun tls(sock: Socket, sni: String, port: Int, insecure: Boolean): SSLSocket {
        val ctx = if (insecure) {
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            SSLContext.getInstance("TLS").also { it.init(null, arrayOf<TrustManager>(tm), SecureRandom()) }
        } else {
            SSLContext.getDefault()
        }
        val ssl = ctx.socketFactory.createSocket(sock, sni, port, true) as SSLSocket
        val params = ssl.sslParameters
        params.serverNames = listOf<SNIServerName>(SNIHostName(sni))
        ssl.sslParameters = params
        ssl.startHandshake()
        return ssl
    }
}
