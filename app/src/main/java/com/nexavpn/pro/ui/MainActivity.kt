package com.nexavpn.pro.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nexavpn.pro.NexaApplication
import com.nexavpn.pro.R
import com.nexavpn.pro.api.NexaApi
import com.nexavpn.pro.model.ProtocolType
import com.nexavpn.pro.model.ServerConfig
import com.nexavpn.pro.service.NexaVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var hardwareIdText: TextView
    private lateinit var tokenInput: android.widget.EditText
    private lateinit var connectButton: Button
    private var selectedServer: ServerConfig? = null

    companion object {
        private const val VPN_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        hardwareIdText = findViewById(R.id.hardwareIdText)
        tokenInput = findViewById(R.id.tokenInput)
        connectButton = findViewById(R.id.connectButton)

        val hwid = NexaApi.getHardwareId(this)
        hardwareIdText.text = "Hardware ID : $hwid"

        selectedServer = ServerConfig(
            id = "demo",
            name = "Serveur de démonstration",
            protocol = ProtocolType.SSH,
            host = "127.0.0.1",
            port = 443,
            country = "CM",
            network = "Orange / MTN",
            tls = false,
            sni = null,
            path = null,
            username = "demo",
            password = "demo"
        )

        connectButton.setOnClickListener {
            if (NexaVpnService.isRunning) {
                stopVpn()
            } else {
                val token = tokenInput.text.toString().trim()
                if (token.isEmpty()) {
                    Toast.makeText(this, "Collez votre token VPN", Toast.LENGTH_SHORT).show()
                } else {
                    verifyAndConnect(token)
                }
            }
        }

        findViewById<Button>(R.id.checkTransactionButton).setOnClickListener {
            Toast.makeText(this, "Ouvrez le panel NEXA pour vérifier votre transaction.", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.tabsHome).setOnClickListener { showHome() }
        findViewById<Button>(R.id.tabsAccount).setOnClickListener { showInfo("Compte", "Connectez votre compte NEXA.") }
        findViewById<Button>(R.id.tabsUsage).setOnClickListener { showInfo("Usage", "Votre consommation apparaîtra ici.") }
        findViewById<Button>(R.id.tabsHistory).setOnClickListener { showInfo("Historique", "Historique VPN local.") }
        findViewById<Button>(R.id.tabsSettings).setOnClickListener { showInfo("Réglages", "MTU : 1500 · Applications exclues") }

        updateStatus()
        showCrashCardIfNeeded()
    }

    private fun verifyAndConnect(token: String) {
        statusText.text = "Vérification du token..."
        connectButton.isEnabled = false
        CoroutineScope(Dispatchers.Main).launch {
            NexaApi.verifyToken(token, NexaApi.getHardwareId(this@MainActivity))
                .onSuccess { response ->
                    if (response.optBoolean("valid", false)) {
                        requestVpnPermission()
                    } else {
                        statusText.text = response.optString("message", "Token invalide")
                        Toast.makeText(this@MainActivity, statusText.text, Toast.LENGTH_LONG).show()
                    }
                }
                .onFailure {
                    statusText.text = "Erreur de connexion au serveur"
                    Toast.makeText(this@MainActivity, "Impossible de vérifier le token. Réessayez.", Toast.LENGTH_LONG).show()
                }
            connectButton.isEnabled = true
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            startVpnService()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) startVpnService()
    }

    private fun startVpnService() {
        val server = selectedServer ?: return
        val intent = Intent(this, NexaVpnService::class.java).apply {
            action = NexaVpnService.ACTION_START
            putExtra(NexaVpnService.EXTRA_SERVER_JSON, JSONObject().apply {
                put("id", server.id)
                put("name", server.name)
                put("protocol", server.protocol.name.lowercase())
                put("host", server.host)
                put("port", server.port)
                put("country", server.country)
                put("tls", server.tls)
                put("username", server.username)
                put("password", server.password)
            }.toString())
        }
        ContextCompat.startForegroundService(this, intent)
        statusText.text = "Établissement..."
        updateStatus()
    }

    private fun stopVpn() {
        val intent = Intent(this, NexaVpnService::class.java).apply { action = NexaVpnService.ACTION_STOP }
        startService(intent)
        updateStatus()
    }

    private fun updateStatus() {
        val active = NexaVpnService.isRunning
        statusText.text = if (active) "VPN Actif" else "VPN Inactif"
        connectButton.text = if (active) "DÉCONNECTER" else "CONNECTER"
    }

    private fun showHome() {
        setContentView(R.layout.activity_main)
        recreate()
    }

    private fun showInfo(title: String, message: String) {
        Toast.makeText(this, "$title : $message", Toast.LENGTH_LONG).show()
    }

    private fun showCrashCardIfNeeded() {
        val prefs = getSharedPreferences(NexaApplication.PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(NexaApplication.KEY_CONNECTING_FLAG, false)) {
            statusText.text = "Diagnostic : connexion interrompue. Vérifiez vos paramètres réseau."
            prefs.edit().putBoolean(NexaApplication.KEY_CONNECTING_FLAG, false).apply()
        }
        val crash = prefs.getString(NexaApplication.KEY_LAST_CRASH, null)
        if (!crash.isNullOrBlank()) {
            findViewById<TextView>(R.id.diagnosticText).apply {
                text = "⚠ Diagnostic : l'application s'est fermée de façon inattendue. Appuyez pour afficher le rapport."
                setOnClickListener { android.app.AlertDialog.Builder(this@MainActivity).setTitle("Rapport de diagnostic").setMessage(crash).setPositiveButton("Fermer", null).setNeutralButton("Effacer") { _, _ -> prefs.edit().remove(NexaApplication.KEY_LAST_CRASH).apply(); text = "" }.show() }
            }
        }
    }
}
