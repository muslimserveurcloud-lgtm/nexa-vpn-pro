package com.nexavpn.pro

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.Executors

/**
 * Partage de connexion : un proxy qui accepte à la fois HTTP (CONNECT et requêtes simples) et SOCKS5
 * sur le port 8888, et renvoie tout dans le tunnel VPN. Les appareils connectés au point d'accès
 * du téléphone peuvent l'utiliser comme proxy manuel.
 */
object ShareNet {
    const val PORT = 8888
    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()

    @Volatile private var running = false

    /** Octets relayés (utilisé pour la consommation en mode proxy). */
    val bytes = java.util.concurrent.atomic.AtomicLong(0)

    /** loopbackOnly = true : proxy local réservé au téléphone (mode proxy du VPN), sans partage. */
    fun start(socksPort: Int, loopbackOnly: Boolean = false): Boolean {
        if (running) return true
        return try {
            val ss = ServerSocket(PORT, 50, InetAddress.getByName(if (loopbackOnly) "127.0.0.1" else "0.0.0.0"))
            server = ss
            running = true
            VpnState.sharing = !loopbackOnly
            bytes.set(0)
            pool.execute {
                while (running) {
                    try {
                        val c = ss.accept()
                        pool.execute { handle(c, socksPort) }
                    } catch (e: Exception) {
                        if (!running) break
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun stop() {
        running = false
        VpnState.sharing = false
        try { server?.close() } catch (_: Exception) {}
        server = null
    }

    /** Adresses IPv4 du téléphone sur ses réseaux (Wi-Fi, point d'accès...), hors tunnel VPN. */
    fun addresses(): List<String> {
        val out = ArrayList<String>()
        try {
            val ifs = NetworkInterface.getNetworkInterfaces() ?: return out
            for (ni in ifs) {
                if (!ni.isUp || ni.isLoopback) continue
                for (a in ni.inetAddresses) {
                    if (a is Inet4Address) {
                        val ip = a.hostAddress ?: continue
                        if (ip.startsWith("198.18.")) continue
                        out.add("$ip  (${ni.name})")
                    }
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun handle(c: Socket, socksPort: Int) {
        var up: Socket? = null
        val close: () -> Unit = {
            try { c.close() } catch (_: Exception) {}
            try { up?.close() } catch (_: Exception) {}
        }
        try {
            c.tcpNoDelay = true
            val cin = BufferedInputStream(c.getInputStream(), 16384)
            val cout = c.getOutputStream()
            cin.mark(1)
            val first = cin.read()
            cin.reset()

            if (first == 5) {
                // Client SOCKS5 : relais direct vers le SOCKS local.
                val u = Socket("127.0.0.1", socksPort)
                up = u
                pool.execute { pump(u.getInputStream(), cout, close) }
                pump(cin, u.getOutputStream(), close)
                return
            }

            val head = readHead(cin) ?: return close()
            val lines = head.split("\r\n")
            val parts = lines[0].split(" ")
            if (parts.size < 3) return close()
            val method = parts[0]
            val target = parts[1]

            if (method.equals("CONNECT", ignoreCase = true)) {
                val hp = splitHostPort(target, 443)
                val u = socksConnect(hp.first, hp.second, socksPort)
                up = u
                cout.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                cout.flush()
                pool.execute { pump(u.getInputStream(), cout, close) }
                pump(cin, u.getOutputStream(), close)
            } else {
                val url = URL(target)
                val host = url.host
                val port = if (url.port == -1) 80 else url.port
                val path = if (url.file.isNullOrEmpty()) "/" else url.file
                val u = socksConnect(host, port, socksPort)
                up = u
                val sb = StringBuilder("$method $path ${parts[2]}\r\n")
                for (i in 1 until lines.size) {
                    val l = lines[i]
                    if (l.isEmpty()) continue
                    val low = l.lowercase()
                    if (low.startsWith("proxy-") || low.startsWith("connection:")) continue
                    sb.append(l).append("\r\n")
                }
                sb.append("Connection: close\r\n\r\n")
                val uo = u.getOutputStream()
                uo.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
                uo.flush()
                pool.execute { pump(u.getInputStream(), cout, close) }
                pump(cin, uo, close)
            }
        } catch (e: Exception) {
            close()
        }
    }

    private fun splitHostPort(t: String, defPort: Int): Pair<String, Int> {
        val i = t.lastIndexOf(':')
        return if (i > 0 && !t.endsWith("]")) Pair(t.substring(0, i), t.substring(i + 1).toIntOrNull() ?: defPort)
        else Pair(t, defPort)
    }

    private fun readHead(i: InputStream): String? {
        val sb = StringBuilder()
        var m = 0
        while (sb.length < 16384) {
            val b = i.read()
            if (b < 0) return null
            sb.append(b.toChar())
            m = when (m) {
                0 -> if (b == 13) 1 else 0
                1 -> if (b == 10) 2 else if (b == 13) 1 else 0
                2 -> if (b == 13) 3 else 0
                else -> if (b == 10) 4 else if (b == 13) 1 else 0
            }
            if (m == 4) return sb.toString().removeSuffix("\r\n\r\n")
        }
        return null
    }

    private fun readN(i: InputStream, n: Int): ByteArray {
        val b = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = i.read(b, off, n - off)
            if (r < 0) throw IOException("Fin de flux")
            off += r
        }
        return b
    }

    private fun socksConnect(host: String, port: Int, socksPort: Int): Socket {
        val s = Socket("127.0.0.1", socksPort)
        try {
            val o = s.getOutputStream()
            val i = s.getInputStream()
            o.write(byteArrayOf(5, 1, 0))
            o.flush()
            val g = readN(i, 2)
            if (g[1].toInt() != 0) throw IOException("SOCKS refusé")
            val hb = host.toByteArray(Charsets.UTF_8)
            val req = ByteArray(7 + hb.size)
            req[0] = 5
            req[1] = 1
            req[2] = 0
            req[3] = 3
            req[4] = hb.size.toByte()
            System.arraycopy(hb, 0, req, 5, hb.size)
            req[5 + hb.size] = (port shr 8).toByte()
            req[6 + hb.size] = (port and 0xFF).toByte()
            o.write(req)
            o.flush()
            val h = readN(i, 4)
            if (h[1].toInt() != 0) throw IOException("Connexion refusée par le tunnel")
            when (h[3].toInt()) {
                1 -> readN(i, 6)
                3 -> {
                    val l = i.read()
                    readN(i, l + 2)
                }
                else -> readN(i, 18)
            }
            return s
        } catch (e: Exception) {
            try { s.close() } catch (_: Exception) {}
            throw e
        }
    }

    private fun pump(i: InputStream, o: OutputStream, onEnd: () -> Unit) {
        val buf = ByteArray(16384)
        try {
            while (true) {
                val r = i.read(buf)
                if (r < 0) break
                bytes.addAndGet(r.toLong())
                o.write(buf, 0, r)
                o.flush()
            }
        } catch (_: Exception) {
        } finally {
            onEnd()
        }
    }
}
