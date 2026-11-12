package com.nexavpn.pro

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    /** Identifiant de l'appareil (NEXA-XXXX-XXXX-XXXX), visible dès la page d'activation. */
    val deviceId: String = Device.id(app)

    var session by mutableStateOf(store.load())
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private var fromImport = false

    fun submit(token: String) {
        val t = token.trim()
        if (t.isEmpty()) {
            message = "Saisis ton token d'activation."
            return
        }
        activate(t)
    }

    fun refresh() {
        session?.let { activate(it.token) }
    }

    fun logout() {
        store.clear()
        session = null
        message = null
    }

    /** Importe une configuration .nexa (texte déjà lu). */
    fun importText(raw: String?) {
        val cfg = NexaFile.parse(raw)
        if (cfg == null) {
            message = "Fichier de configuration invalide."
            toast("Fichier de configuration invalide.")
            return
        }
        val target = cfg.device
        if (target != null && !target.equals(deviceId, ignoreCase = true)) {
            val m = "Ce fichier est réservé à un autre appareil ($target). Ton identifiant : $deviceId"
            message = m
            toast(m)
            return
        }
        fromImport = true
        activate(cfg.token)
    }

    /** Importe un fichier partagé ou choisi dans le sélecteur. */
    fun importUri(uri: Uri) {
        viewModelScope.launch {
            val txt = withContext(Dispatchers.IO) { NexaFile.readUri(getApplication(), uri) }
            importText(txt)
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(getApplication<Application>(), msg, Toast.LENGTH_LONG).show()
    }

    private fun activate(token: String) {
        viewModelScope.launch {
            busy = true
            message = null
            when (val r = Api.activate(token, deviceId)) {
                is Act.Ok -> {
                    store.save(r.session)
                    session = r.session
                    if (fromImport) toast("Configuration importée : ${r.session.serverName}")
                }
                is Act.Fail -> {
                    if (r.status in setOf("invalid", "revoked", "expired")) logout()
                    message = errorText(r.status)
                    if (fromImport) toast(errorText(r.status))
                }
                is Act.NetErr -> {
                    message = "Connexion impossible. Vérifie ton réseau."
                    if (fromImport) toast("Connexion impossible. Vérifie ton réseau.")
                }
            }
            fromImport = false
            busy = false
        }
    }

    private fun errorText(status: String): String = when (status) {
        "invalid" -> "Token invalide."
        "revoked" -> "Ce token a été révoqué."
        "expired" -> "Ce token a expiré."
        "data_limit" -> "La limite de données de ce token est atteinte."
        "device_limit" -> "Ce token est déjà utilisé sur le nombre maximum d'appareils."
        "device_mismatch" -> "Cette configuration est réservée à un autre appareil."
        "server_unavailable" -> "Serveur indisponible. Contacte ton revendeur."
        else -> "Erreur inconnue ($status)."
    }
}
