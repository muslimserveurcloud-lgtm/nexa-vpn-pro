package com.nexavpn.pro.engine

import android.content.Context
import android.util.Log
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.nexavpn.pro.model.ServerConfig
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Properties

class SshEngine : Engine {
    override val socksPort: Int = 1080
    override val supportsUdp: Boolean = false
    override val protocolName: String = "SSH / Direct"

    private var jschSession: Session? = null
    private var localSocksServer: ServerSocket? = null
    @Volatile private var running = false

    override fun connect(context: Context, server: ServerConfig): Boolean {
        return try {
            Log.i("SshEngine", "Démarrage SSH vers ${server.host}:${server.port}")
            val jsch = JSch()
            val user = server.username ?: "vpn"
            val session = jsch.getSession(user, server.host, server.port)
            session.setPassword(server.password ?: "")

            val config = Properties().apply {
                put("StrictHostKeyChecking", "no")
                put("PreferredAuthentications", "password")
            }
            session.setConfig(config)
            session.serverAliveInterval = 25000
            session.connect(10000)

            jschSession = session
            running = true
            startLocalSocksProxy(session)
            Log.i("SshEngine", "SSH connecté avec succès, SOCKS5 actif sur port $socksPort")
            true
        } catch (e: Exception) {
            Log.e("SshEngine", "Erreur connexion SSH", e)
            stop()
            false
        }
    }

    private fun startLocalSocksProxy(session: Session) {
        Thread {
            try {
                localSocksServer = ServerSocket(socksPort, 50, InetAddress.getByName("127.0.0.1"))
                while (running && localSocksServer?.isClosed == false) {
                    val client = localSocksServer?.accept() ?: break
                    Thread { handleSocksClient(session, client) }.start()
                }
            } catch (e: Exception) {
                if (running) Log.e("SshEngine", "Erreur SOCKS Server", e)
            }
        }.start()
    }

    private fun handleSocksClient(session: Session, client: Socket) {
        try {
            val cin = client.getInputStream()
            val cout = client.getOutputStream()

            // SOCKS5 handshake
            val ver = cin.read()
            if (ver != 0x05) { client.close(); return }
            val nmethods = cin.read()
            val methods = ByteArray(nmethods)
            cin.read(methods)
            cout.write(byteArrayOf(0x05, 0x00))
            cout.flush()

            // Request
            cin.read() // ver
            val cmd = cin.read() // 1 = CONNECT
            cin.read() // rsv
            val atyp = cin.read()

            val targetHost = when (atyp) {
                0x01 -> {
                    val b = ByteArray(4); cin.read(b)
                    InetAddress.getByAddress(b).hostAddress
                }
                0x03 -> {
                    val len = cin.read()
                    val b = ByteArray(len); cin.read(b)
                    String(b)
                }
                0x04 -> {
                    val b = ByteArray(16); cin.read(b)
                    InetAddress.getByAddress(b).hostAddress
                }
                else -> { client.close(); return }
            }
            val portHigh = cin.read()
            val portLow = cin.read()
            val targetPort = (portHigh shl 8) or portLow

            if (cmd != 0x01) {
                cout.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 127, 0, 0, 1, 0, 0))
                cout.flush()
                client.close()
                return
            }

            // Open SSH direct-tcpip channel
            val channel = session.openChannel("direct-tcpip") as com.jcraft.jsch.ChannelDirectTCPIP
            channel.setHost(targetHost)
            channel.setPort(targetPort)
            channel.connect(5000)

            val chin = channel.inputStream
            val chout = channel.outputStream

            // Bidirectional relay
            val t1 = Thread { forwardStream(cin, chout) }
            val t2 = Thread { forwardStream(chin, cout) }
            t1.start(); t2.start()
            t1.join(); t2.join()

            channel.disconnect()
            client.close()
        } catch (e: Exception) {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun forwardStream(input: InputStream, output: OutputStream) {
        val buf = ByteArray(8192)
        try {
            while (running) {
                val len = input.read(buf)
                if (len == -1) break
                output.write(buf, 0, len)
                output.flush()
            }
        } catch (_: Exception) {}
    }

    override fun isAlive(): Boolean = running && (jschSession?.isConnected == true)

    override fun stop() {
        running = false
        try { localSocksServer?.close() } catch (_: Exception) {}
        try { jschSession?.disconnect() } catch (_: Exception) {}
        localSocksServer = null
        jschSession = null
    }
}
