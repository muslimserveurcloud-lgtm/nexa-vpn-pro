package com.nexavpn.pro

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** État du VPN observable par l'interface. */
object VpnState {
    var status by mutableStateOf("Déconnecté")
    var connected by mutableStateOf(false)
    var connecting by mutableStateOf(false)
    var sharing by mutableStateOf(false)

    /** Heure (ms) à laquelle la connexion en cours a été établie. */
    var connectedAt by mutableLongStateOf(0L)

    /** Octets échangés depuis le début de la connexion en cours. */
    var sessionBytes by mutableLongStateOf(0L)

    /** Débits instantanés en bits par seconde. */
    var downBps by mutableLongStateOf(0L)
    var upBps by mutableLongStateOf(0L)

    val log = mutableStateListOf<String>()

    fun addLog(msg: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        CrashLog.mark(msg)
        synchronized(log) {
            log.add("$t  $msg")
            while (log.size > 200) log.removeAt(0)
        }
    }

    fun clearLog() {
        synchronized(log) { log.clear() }
    }
}
