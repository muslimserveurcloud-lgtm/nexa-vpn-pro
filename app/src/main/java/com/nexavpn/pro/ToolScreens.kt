package com.nexavpn.pro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun ViaNote() {
    Text(
        if (VpnState.connected) "Les requêtes passent par le VPN."
        else "VPN déconnecté : les requêtes partent sans tunnel.",
        color = if (VpnState.connected) Ok else Muted,
        fontSize = 13.sp
    )
}

// ====================================================================== IP Finder

@Composable
fun IpFinderScreen() {
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ScreenColumn {
        ViaNote()
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    result = withContext(Dispatchers.IO) {
                        try { Tools.ipInfo() } catch (e: Exception) { "Erreur : " + (e.message ?: "réseau") }
                    }
                    busy = false
                }
            },
            enabled = !busy
        ) { Text(if (busy) "Recherche…" else "Trouver mon IP") }
        if (result.isNotEmpty()) PanelCard { Text(result, color = TextC) }
    }
}

// ====================================================================== Speedtest

@Composable
fun SpeedtestScreen() {
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ScreenColumn {
        ViaNote()
        Text(
            "Le test utilise environ 20 Mo de données (déduits de ton token si le VPN est connecté).",
            color = Muted,
            fontSize = 13.sp
        )
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    result = "Démarrage…"
                    result = withContext(Dispatchers.IO) {
                        try { Tools.speedtest { p -> result = p } } catch (e: Exception) { "Erreur : " + (e.message ?: "réseau") }
                    }
                    busy = false
                }
            },
            enabled = !busy
        ) { Text(if (busy) "Test en cours…" else "Lancer le test") }
        if (result.isNotEmpty()) PanelCard { Text(result, color = TextC) }
    }
}

// ====================================================================== Auto Ping

@Composable
fun AutoPingScreen() {
    var host by remember { mutableStateOf("www.google.com") }
    var running by remember { mutableStateOf(false) }
    val results = remember { mutableStateListOf<Long>() }

    LaunchedEffect(running, host) {
        while (running) {
            val ms = withContext(Dispatchers.IO) {
                try { Net.connectMs(host.trim(), 443) } catch (e: Exception) { -1L }
            }
            results.add(0, ms)
            while (results.size > 30) results.removeAt(results.lastIndex)
            delay(2000)
        }
    }

    val ok = results.filter { it >= 0 }
    ScreenColumn {
        ViaNote()
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Hôte à tester (port 443)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { running = !running }) { Text(if (running) "Arrêter" else "Démarrer") }
            OutlinedButton(onClick = { results.clear() }) { Text("Effacer") }
        }
        if (results.isNotEmpty()) {
            PanelCard {
                Text(
                    if (ok.isEmpty()) "Aucune réponse" else
                        "Min ${ok.min()} ms · Moy ${ok.sum() / ok.size} ms · Max ${ok.max()} ms",
                    color = TextC,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Perdus : ${results.size - ok.size} sur ${results.size}",
                    color = Muted,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )
                Text(
                    results.take(12).joinToString("\n") { if (it >= 0) "$it ms" else "délai dépassé" },
                    color = TextC,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ====================================================================== Short URL

@Composable
fun ShortUrlScreen() {
    var url by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    ScreenColumn {
        ViaNote()
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Lien à raccourcir (https://…)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    result = withContext(Dispatchers.IO) {
                        try { Tools.shortUrl(url.trim()) } catch (e: Exception) { "Erreur : " + (e.message ?: "réseau") }
                    }
                    busy = false
                }
            },
            enabled = !busy && url.isNotBlank()
        ) { Text(if (busy) "Création…" else "Raccourcir") }
        if (result.isNotEmpty()) {
            PanelCard {
                Text(result, color = TextC)
                if (result.startsWith("http")) {
                    OutlinedButton(
                        onClick = { clip.setText(AnnotatedString(result)) },
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text("Copier") }
                }
            }
        }
    }
}

// ====================================================================== Hardware ID

@Composable
fun HardwareIdScreen() {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val id = remember { Device.id(ctx) }
    ScreenColumn {
        Text(
            "Cet identifiant lie ton token à ce téléphone. Donne-le à ton revendeur si on te le demande.",
            color = Muted
        )
        PanelCard {
            Text(id, color = TextC, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
            OutlinedButton(
                onClick = { clip.setText(AnnotatedString(id)) },
                modifier = Modifier.padding(top = 10.dp)
            ) { Text("Copier") }
        }
    }
}

// ====================================================================== Response checker

@Composable
fun ResponseCheckerScreen() {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("80") }
    var sni by remember { mutableStateOf("") }
    var payload by remember {
        mutableStateOf("GET / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: websocket[crlf]Connection: Keep-Alive[crlf][crlf]")
    }
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ScreenColumn {
        Text(
            "Envoie un payload à un serveur et affiche le début de sa réponse (200, 101…). " +
                "Balises : [crlf] [host] [port] [host_port] [protocol] [ua].",
            color = Muted,
            fontSize = 13.sp
        )
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Hôte") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = sni,
                onValueChange = { sni = it },
                label = { Text("SNI (TLS, optionnel)") },
                singleLine = true,
                modifier = Modifier.weight(2f)
            )
        }
        OutlinedTextField(
            value = payload,
            onValueChange = { payload = it },
            label = { Text("Payload") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    result = withContext(Dispatchers.IO) {
                        try {
                            Tools.checkResponse(host.trim(), port.trim().toIntOrNull() ?: 80, sni.trim(), payload)
                        } catch (e: Exception) {
                            "Erreur : " + (e.message ?: e.javaClass.simpleName)
                        }
                    }
                    busy = false
                }
            },
            enabled = !busy && host.isNotBlank()
        ) { Text(if (busy) "Test en cours…" else "Tester") }
        if (result.isNotEmpty()) {
            PanelCard {
                Text(result, color = TextC, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}

// ====================================================================== Share Net

@Composable
fun ShareNetScreen() {
    var addrs by remember { mutableStateOf(ShareNet.addresses()) }
    var error by remember { mutableStateOf("") }
    ScreenColumn {
        Text(
            "Partage ta connexion VPN avec d'autres appareils : active le point d'accès Wi-Fi du téléphone, " +
                "démarre le partage, puis règle un proxy manuel (HTTP ou SOCKS5) sur l'autre appareil.",
            color = Muted
        )
        if (!VpnState.connected) {
            Text("Connecte d'abord le VPN depuis l'accueil.", color = Warn)
        }
        Button(
            onClick = {
                error = ""
                if (VpnState.sharing) {
                    ShareNet.stop()
                } else if (!ShareNet.start(1080)) {
                    error = "Impossible d'ouvrir le port ${ShareNet.PORT}."
                }
                addrs = ShareNet.addresses()
            },
            enabled = VpnState.connected || VpnState.sharing
        ) { Text(if (VpnState.sharing) "Arrêter le partage" else "Démarrer le partage") }
        if (error.isNotEmpty()) Text(error, color = Bad)
        PanelCard {
            Text(
                if (VpnState.sharing) "Partage actif" else "Partage arrêté",
                color = if (VpnState.sharing) Ok else Muted,
                fontWeight = FontWeight.SemiBold
            )
            Text("Port : ${ShareNet.PORT}", color = TextC, modifier = Modifier.padding(top = 6.dp))
            Text("Adresses du téléphone :", color = Muted, modifier = Modifier.padding(top = 6.dp))
            Text(
                if (addrs.isEmpty()) "Aucune adresse réseau trouvée." else addrs.joinToString("\n"),
                color = TextC,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
            OutlinedButton(
                onClick = { addrs = ShareNet.addresses() },
                modifier = Modifier.padding(top = 8.dp)
            ) { Text("Actualiser les adresses") }
        }
        Text(
            "Attention : toute personne connectée à ton point d'accès peut utiliser ce partage et consommer " +
                "les données de ton token. Arrête le partage quand tu n'en as plus besoin.",
            color = Muted,
            fontSize = 13.sp
        )
    }
}
