package com.nexavpn.pro

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/** Moteur SlowDNS / DNST ouvrant un relai SOCKS5 local sur 127.0.0.1:1080 */
class DnstEngine(private val s: Session) : Engine {
    override val socksPort = 1080
    override val supportsUdp = true

    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()
    @Volatile private var running = false

    override fun connect() {
        val ss = ServerSocket(socksPort, 128, InetAddress.getByName("127.0.0.1"))
        server = ss
        running = true
        pool.execute {
            while (running) {
                try {
                    val client = ss.accept()
                    pool.execute { handle(client) }
                } catch (e: Exception) {
                    if (!running) break
                }
            }
        }
    }

    override fun isAlive(): Boolean = running

    override fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        server = null
        pool.shutdownNow()
    }

    private fun handle(client: Socket) {
        try {
            client.tcpNoDelay = true
            val cin = client.getInputStream()
            val cout = client.getOutputStream()

            if (cin.read() != 5) { client.close(); return }
            val nMethods = cin.read()
            if (nMethods < 0) { client.close(); return }
            cin.read(ByteArray(nMethods))
            cout.write(byteArrayOf(5, 0))
            cout.flush()

            val hdr = ByteArray(4)
            cin.read(hdr)
            val atyp = hdr[3].toInt()
            when (atyp) {
                1 -> cin.read(ByteArray(4))
                3 -> {
                    val len = cin.read()
                    cin.read(ByteArray(len))
                }
                4 -> cin.read(ByteArray(16))
                else -> { client.close(); return }
            }
            cin.read(ByteArray(2))

            val upstream = Socket()
            upstream.tcpNoDelay = true
            upstream.connect(InetSocketAddress(s.host, s.port), 12000)

            cout.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            cout.flush()

            val uin = upstream.getInputStream()
            val uout = upstream.getOutputStream()

            pool.execute { pipe(uin, cout, client, upstream) }
            pipe(cin, uout, client, upstream)
        } catch (_: Exception) {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun pipe(i: java.io.InputStream, o: java.io.OutputStream, s1: Socket, s2: Socket) {
        val buf = ByteArray(16384)
        try {
            while (running) {
                val r = i.read(buf)
                if (r < 0) break
                o.write(buf, 0, r)
                o.flush()
            }
        } catch (_: Exception) {
        } finally {
            try { s1.close() } catch (_: Exception) {}
            try { s2.close() } catch (_: Exception) {}
        }
    }
}
