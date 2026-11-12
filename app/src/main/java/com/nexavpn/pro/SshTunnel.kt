package com.nexavpn.pro

import com.jcraft.jsch.Channel
import com.jcraft.jsch.JSch
import com.jcraft.jsch.SocketFactory
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import javax.net.ssl.SNIHostName
import javax.net.ssl.SNIServerName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import com.jcraft.jsch.Session as SshSession

/**
 * Tunnel SSH façon HTTP Custom :
 *   [proxy optionnel] -> [TLS + SNI optionnel] -> [payload HTTP optionnel] -> SSH
 * puis un serveur SOCKS5 local (127.0.0.1:1080) qui ouvre des canaux direct-tcpip dans la session SSH.
 */
class SshTunnel(private val cfg: TunnelConfig) : Engine {

    private class Transport(val socket: Socket, val input: InputStream, val output: OutputStream)

    override val socksPort = 1080
    override val supportsUdp = false

    private var ssh: SshSession? = null
    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()

    @Volatile
    private var running = false

    override fun isAlive(): Boolean = running && ssh?.isConnected == true

    override fun connect() {
        start()
        startSocks()
    }

    /** Établit le transport puis la session SSH (bloquant). */
    fun start() {
        val transport = openTransport()
        val factory = object : SocketFactory {
            override fun createSocket(host: String, port: Int): Socket = transport.socket
            override fun getInputStream(socket: Socket): InputStream = transport.input
            override fun getOutputStream(socket: Socket): OutputStream = transport.output
        }
        val session = JSch().getSession(cfg.user, cfg.host, cfg.port)
        session.setPassword(cfg.password)
        session.setSocketFactory(factory)
        session.setConfig("StrictHostKeyChecking", "no")
        session.setConfig("PreferredAuthentications", "password,keyboard-interactive")
        // Accepte aussi les anciens serveurs qui n'annoncent que ssh-rsa.
        val hostKeys = session.getConfig("server_host_key")
        if (hostKeys != null && !hostKeys.contains("ssh-rsa")) {
            session.setConfig("server_host_key", "$hostKeys,ssh-rsa")
        }
        session.setServerAliveInterval(25000)
        session.setServerAliveCountMax(3)
        session.connect(20000)
        ssh = session
        running = true
    }

    /** Lance le serveur SOCKS5 local. */
    fun startSocks() {
        val ss = ServerSocket(socksPort, 128, InetAddress.getByName("127.0.0.1"))
        server = ss
        pool.execute {
            while (running) {
                try {
                    val c = ss.accept()
                    pool.execute { handle(c) }
                } catch (e: Exception) {
                    if (!running) break
                }
            }
        }
    }

    override fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        try { ssh?.disconnect() } catch (_: Exception) {}
        pool.shutdownNow()
    }

    // ---------------------------------------------------------------- transport

    private fun openTransport(): Transport {
        val useProxy = cfg.proxyHost.isNotEmpty()
        val targetHost = if (useProxy) cfg.proxyHost else cfg.host
        val targetPort = if (useProxy) cfg.proxyPort else cfg.port

        var sock = Socket()
        sock.tcpNoDelay = true
        sock.connect(InetSocketAddress(targetHost, targetPort), 15000)
        sock.soTimeout = 15000

        if (cfg.sni.isNotEmpty()) {
            val ctx = if (cfg.tlsInsecure) trustAllContext() else SSLContext.getDefault()
            val ssl = ctx.socketFactory.createSocket(sock, cfg.sni, targetPort, true) as SSLSocket
            val params = ssl.sslParameters
            params.serverNames = listOf<SNIServerName>(SNIHostName(cfg.sni))
            ssl.sslParameters = params
            ssl.startHandshake()
            sock = ssl
        }

        var input: InputStream = sock.getInputStream()
        val output: OutputStream = sock.getOutputStream()

        if (cfg.payload.isNotBlank()) {
            val parts = expand(cfg.payload).split("[split]")
            for ((i, part) in parts.withIndex()) {
                output.write(part.toByteArray(Charsets.ISO_8859_1))
                output.flush()
                if (i < parts.size - 1) Thread.sleep(150)
            }
            input = skipHttpResponse(input)
        }
        return Transport(sock, input, output)
    }

    private fun expand(p: String): String = p
        .replace("[crlf]", "\r\n")
        .replace("[cr]", "\r")
        .replace("[lf]", "\n")
        .replace("[host_port]", "${cfg.host}:${cfg.port}")
        .replace("[host]", cfg.host)
        .replace("[port]", cfg.port.toString())
        .replace("[protocol]", "HTTP/1.1")
        .replace("[ua]", "Mozilla/5.0 (Linux; Android 13)")

    /** Lit la réponse HTTP du proxy (200, 101...) sans avaler la bannière SSH qui suit. */
    private fun skipHttpResponse(raw: InputStream): InputStream {
        val pb = PushbackInputStream(raw, 8192)
        val head = ByteArray(4)
        var n = 0
        try {
            while (n < 4) {
                val r = pb.read(head, n, 4 - n)
                if (r < 0) throw IOException("Connexion fermée par le serveur")
                n += r
            }
        } catch (e: SocketTimeoutException) {
            // Le serveur attend que le client parle en premier : on laisse SSH démarrer.
            if (n > 0) pb.unread(head, 0, n)
            return pb
        }
        pb.unread(head, 0, 4)
        if (String(head, Charsets.ISO_8859_1).startsWith("SSH-")) return pb

        val buf = ByteArrayOutputStream()
        var matched = 0
        while (true) {
            val b = pb.read()
            if (b < 0) throw IOException("Réponse HTTP incomplète")
            buf.write(b)
            matched = when (matched) {
                0 -> if (b == 13) 1 else 0
                1 -> if (b == 10) 2 else if (b == 13) 1 else 0
                2 -> if (b == 13) 3 else 0
                else -> if (b == 10) 4 else if (b == 13) 1 else 0
            }
            if (matched == 4) break
            if (buf.size() > 16384) throw IOException("En-têtes HTTP trop longs")
        }
        val statusLine = buf.toString("ISO-8859-1").lineSequence().first()
        val code = statusLine.split(" ").getOrNull(1)?.toIntOrNull() ?: 0
        if (!(code in 200..299 || code == 101)) throw IOException("Proxy : $statusLine")
        return pb
    }

    private fun trustAllContext(): SSLContext {
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(tm), SecureRandom())
        return ctx
    }

    // ---------------------------------------------------------------- SOCKS5

    private fun handle(c: Socket) {
        var ch: Channel? = null
        val closeAll: () -> Unit = {
            try { c.close() } catch (_: Exception) {}
            try { ch?.disconnect() } catch (_: Exception) {}
        }
        try {
            c.tcpNoDelay = true
            val cin = c.getInputStream()
            val cout = c.getOutputStream()

            if (cin.read() != 5) return closeAll()
            val nMethods = cin.read()
            if (nMethods < 0) return closeAll()
            readFully(cin, nMethods)
            cout.write(byteArrayOf(5, 0))
            cout.flush()

            val hdr = readFully(cin, 4)
            if (hdr[0].toInt() != 5) return closeAll()
            if (hdr[1].toInt() != 1) {
                reply(cout, 7)
                return closeAll()
            }
            val host: String = when (hdr[3].toInt()) {
                1 -> InetAddress.getByAddress(readFully(cin, 4)).hostAddress ?: ""
                3 -> {
                    val len = cin.read()
                    if (len < 0) return closeAll()
                    String(readFully(cin, len), Charsets.UTF_8)
                }
                4 -> InetAddress.getByAddress(readFully(cin, 16)).hostAddress ?: ""
                else -> {
                    reply(cout, 8)
                    return closeAll()
                }
            }
            val pp = readFully(cin, 2)
            val port = ((pp[0].toInt() and 0xFF) shl 8) or (pp[1].toInt() and 0xFF)

            val session = ssh
            if (session == null || !session.isConnected) return closeAll()
            val channel = session.getStreamForwarder(host, port)
            ch = channel
            val sin = channel.inputStream
            val sout = channel.outputStream
            try {
                channel.connect(15000)
            } catch (e: Exception) {
                reply(cout, 5)
                return closeAll()
            }
            reply(cout, 0)

            pool.execute { pipe(sin, cout, closeAll) }
            pipe(cin, sout, closeAll)
        } catch (e: Exception) {
            closeAll()
        }
    }

    private fun pipe(i: InputStream, o: OutputStream, onEnd: () -> Unit) {
        val buf = ByteArray(16384)
        try {
            while (true) {
                val r = i.read(buf)
                if (r < 0) break
                o.write(buf, 0, r)
                o.flush()
            }
        } catch (_: Exception) {
        } finally {
            onEnd()
        }
    }

    private fun reply(o: OutputStream, code: Int) {
        o.write(byteArrayOf(5, code.toByte(), 0, 1, 0, 0, 0, 0, 0, 0))
        o.flush()
    }

    private fun readFully(i: InputStream, n: Int): ByteArray {
        val b = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = i.read(b, off, n - off)
            if (r < 0) throw EOFException()
            off += r
        }
        return b
    }
}
