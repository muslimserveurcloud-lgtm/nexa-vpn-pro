package com.nexavpn.pro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nexavpn.pro.NexaApplication
import com.nexavpn.pro.engine.DnstEngine
import com.nexavpn.pro.engine.Engine
import com.nexavpn.pro.engine.SshEngine
import com.nexavpn.pro.engine.UdpEngine
import com.nexavpn.pro.engine.VitoraEngine
import com.nexavpn.pro.model.ProtocolType
import com.nexavpn.pro.model.ServerConfig
import com.nexavpn.pro.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream

class NexaVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.nexavpn.pro.START"
        const val ACTION_STOP = "com.nexavpn.pro.STOP"
        const val EXTRA_SERVER_JSON = "extra_server_json"
        const val CHANNEL_ID = "nexa_vpn_status"

        @Volatile var isRunning = false
        @Volatile var bytesIn: Long = 0
        @Volatile var bytesOut: Long = 0
        @Volatile var currentProtocol: String = "Inactif"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var engine: Engine? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var monitorJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val serverJsonStr = intent.getStringExtra(EXTRA_SERVER_JSON)
                if (serverJsonStr != null) {
                    val server = ServerConfig.fromJson(org.json.JSONObject(serverJsonStr))
                    startVpn(server)
                }
            }
            ACTION_STOP -> stopVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn(server: ServerConfig) {
        val prefs = getSharedPreferences(NexaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(NexaApplication.KEY_CONNECTING_FLAG, true).apply()

        startForeground(1001, createNotification("Connexion en cours..."))

        scope.launch {
            try {
                // 1. Sélectionner et démarrer le moteur réseau
                engine = when (server.protocol) {
                    ProtocolType.SSH -> SshEngine()
                    ProtocolType.UDP -> UdpEngine()
                    ProtocolType.DNST -> DnstEngine()
                    ProtocolType.VITORA, ProtocolType.VLESS, ProtocolType.VMESS, ProtocolType.TROJAN -> VitoraEngine()
                    else -> SshEngine()
                }

                currentProtocol = engine?.protocolName ?: "Inconnu"
                val connected = engine?.connect(this@NexaVpnService, server) ?: false
                if (!connected) {
                    stopVpn()
                    return@launch
                }

                // 2. Établir l'interface TUN VpnService
                val builder = Builder().apply {
                    setSession("NEXA VPN PRO")
                    setMtu(1500)
                    addAddress("198.18.0.1", 32)
                    addRoute("0.0.0.0", 0)
                    addDnsServer("198.18.0.2")

                    // Bloquer IPv6
                    try {
                        addAddress("fd00:1:fd00:1::1", 128)
                        addRoute("::", 0)
                    } catch (e: Exception) {
                        Log.w("NexaVpn", "IPv6 non disponible sur cet appareil")
                    }

                    // Exclure notre propre application pour éviter les boucles
                    try {
                        addDisallowedApplication(packageName)
                    } catch (e: Exception) {
                        Log.e("NexaVpn", "Impossible d'exclure l'application", e)
                    }
                }

                vpnInterface = builder.establish()
                if (vpnInterface == null) {
                    stopVpn()
                    return@launch
                }

                isRunning = true
                updateNotification("Protégé — Connecté à ${server.name}")

                // Baisser le drapeau "en cours de connexion" après 8s de stabilité
                launch {
                    delay(8000)
                    if (isRunning) {
                        prefs.edit().putBoolean(NexaApplication.KEY_CONNECTING_FLAG, false).apply()
                    }
                }

                // Surveillance du trafic
                startTrafficMonitor()

            } catch (e: Exception) {
                Log.e("NexaVpn", "Échec démarrage VPN", e)
                stopVpn()
            }
        }
    }

    private fun startTrafficMonitor() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            val fd = vpnInterface?.fileDescriptor ?: return@launch
            val input = FileInputStream(fd)
            val output = FileOutputStream(fd)
            val buffer = ByteArray(32768)

            while (isActive && isRunning) {
                try {
                    val len = input.read(buffer)
                    if (len > 0) {
                        bytesOut += len
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    private fun stopVpn() {
        isRunning = false
        currentProtocol = "Inactif"
        monitorJob?.cancel()
        engine?.stop()
        engine = null

        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null

        val prefs = getSharedPreferences(NexaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(NexaApplication.KEY_CONNECTING_FLAG, false).apply()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotification(status: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(CHANNEL_ID, "Statut VPN", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(chan)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NEXA VPN PRO")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1001, createNotification(status))
    }
}
