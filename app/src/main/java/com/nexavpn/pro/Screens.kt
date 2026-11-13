package com.nexavpn.pro

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ====================================================================== Accueil

@Composable
private fun PowerButton(connected: Boolean, connecting: Boolean, onClick: () -> Unit) {
    val color = when {
        connected -> Accent
        connecting -> Warn
        else -> Brand
    }
    val inf = rememberInfiniteTransition(label = "power")
    val pulse by inf.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val spin by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "spin"
    )
    val rings by inf.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "rings"
    )

    Box(
        modifier = Modifier
            .size(240.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = center
            drawCircle(
                color = color.copy(alpha = if (connected) 0.15f else 0.08f),
                radius = 110.dp.toPx() * rings,
                center = c,
                style = Stroke(1.5.dp.toPx())
            )
            drawCircle(
                color = color.copy(alpha = if (connected) 0.20f else 0.10f),
                radius = 100.dp.toPx() * rings * 0.88f,
                center = c,
                style = Stroke(1.dp.toPx())
            )
            if (connected || connecting) {
                val ar = 92.dp.toPx()
                rotate(spin, pivot = c) {
                    drawArc(
                        color = color.copy(alpha = if (connecting) 0.6f else 0.3f),
                        startAngle = -90f,
                        sweepAngle = 252f,
                        useCenter = false,
                        topLeft = Offset(c.x - ar, c.y - ar),
                        size = Size(ar * 2, ar * 2),
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            val gr = 120.dp.toPx() * pulse
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = if (connected) 0.30f else 0.14f), Color.Transparent),
                    center = c,
                    radius = gr
                ),
                radius = gr,
                center = c
            )
            val rr = 77.dp.toPx()
            val ringW = 7.dp.toPx()
            // Disque sombre avec un anneau lumineux (comme la maquette)
            drawCircle(color = Panel, radius = rr, center = c)
            drawCircle(
                color = color.copy(alpha = 0.18f),
                radius = rr - ringW * 2.2f,
                center = c,
                style = Stroke(1.5.dp.toPx())
            )
            drawCircle(
                brush = Brush.sweepGradient(listOf(color, color.copy(alpha = 0.55f), color), center = c),
                radius = rr - ringW / 2,
                center = c,
                style = Stroke(ringW)
            )
            if (connected) {
                // icône « pause »
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(c.x - 19.dp.toPx(), c.y - 22.dp.toPx()),
                    size = Size(11.dp.toPx(), 44.dp.toPx()),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(c.x + 8.dp.toPx(), c.y - 22.dp.toPx()),
                    size = Size(11.dp.toPx(), 44.dp.toPx()),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            } else if (!connecting) {
                // icône « marche/arrêt »
                val ir = 22.dp.toPx()
                val sw = 5.dp.toPx()
                drawArc(
                    color = Color.White,
                    startAngle = -50f,
                    sweepAngle = 280f,
                    useCenter = false,
                    topLeft = Offset(c.x - ir, c.y - ir + 4.dp.toPx()),
                    size = Size(ir * 2, ir * 2),
                    style = Stroke(sw, cap = StrokeCap.Round)
                )
                drawLine(
                    color = Color.White,
                    start = Offset(c.x, c.y - ir - 6.dp.toPx()),
                    end = Offset(c.x, c.y - 2.dp.toPx()),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }
        }
        if (connecting) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun SpeedChip(icon: ImageVector, value: String, label: String, tint: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .background(Panel2, shape)
            .border(1.dp, Line, shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(value, color = TextC, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(label, color = Dim, fontSize = 10.sp)
        }
    }
}

@Composable
fun HomeScreen(vm: AppViewModel, s: Session, onOpenLogs: () -> Unit) {
    val days = s.daysLeft()
    val lim = s.limitBytes
    val used = s.usedBytes + (if (VpnState.connected) VpnState.sessionBytes else 0L)
    val frac = if (lim == null || lim <= 0L) 1f else (used.toDouble() / lim).coerceIn(0.0, 1.0).toFloat()
    val barColor = when {
        lim == null -> Ok
        frac >= 1f -> Bad
        frac >= 0.8f -> Warn
        else -> Accent
    }
    val expired = days != null && days <= 0
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) startVpn(ctx)
    }
    val limitReached = lim != null && used >= lim
    val supported = s.type in setOf("ssh", "vmess", "vless", "trojan")
    val canConnect = supported && !expired && !limitReached && !VpnState.connecting

    // Quand le VPN s'arrête, on actualise la consommation officielle auprès du serveur.
    var wasConnected by remember { mutableStateOf(false) }
    LaunchedEffect(VpnState.connected) {
        if (wasConnected && !VpnState.connected) {
            delay(2000)
            vm.refresh()
        }
        wasConnected = VpnState.connected
    }

    // Chronomètre de connexion.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(VpnState.connected) {
        while (VpnState.connected) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val stateColor = when {
        VpnState.connected -> Ok
        VpnState.connecting -> Warn
        else -> Dim
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (CrashLog.report.isNotEmpty()) {
            PanelCard {
                Text("Diagnostic : l'application s'est fermée", color = Bad, fontWeight = FontWeight.SemiBold)
                Text(
                    CrashLog.report,
                    color = TextC,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(CrashLog.report)) }) { Text("Copier") }
                    OutlinedButton(onClick = { CrashLog.clear() }) { Text("Effacer") }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // En-tête NEXA avec le logo fourni et le statut réel du tunnel.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_logo),
                contentDescription = "Logo NEXA VPN PRO",
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .border(1.dp, Brand.copy(alpha = 0.45f), RoundedCornerShape(13.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text("NEXA", color = TextC, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("VPN PRO", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(stateColor))
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    VpnState.connected -> "Protégé"
                    VpnState.connecting -> "Connexion..."
                    else -> "Non protégé"
                },
                color = stateColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(Modifier.weight(1f))
            val pill = RoundedCornerShape(20.dp)
            Row(
                modifier = Modifier
                    .border(1.dp, Line, pill)
                    .clip(pill)
                    .clickable(onClick = onOpenLogs)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = Dim, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text("Logs", color = Dim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(20.dp))
        PowerButton(connected = VpnState.connected, connecting = VpnState.connecting) {
            if (VpnState.connecting) return@PowerButton
            if (VpnState.connected) {
                stopVpn(ctx)
            } else if (canConnect) {
                val prepare = VpnService.prepare(ctx)
                if (prepare != null) launcher.launch(prepare) else startVpn(ctx)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            when {
                VpnState.connected -> "VPN Actif"
                VpnState.connecting -> "Établissement..."
                else -> "VPN Inactif"
            },
            color = stateColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        if (VpnState.connected) {
            val sec = ((now - VpnState.connectedAt) / 1000).coerceAtLeast(0)
            Text(
                String.format("%02d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60),
                color = Dim,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp
            )
        } else {
            Text(
                if (VpnState.connecting) VpnState.status else "Appuyez pour connecter",
                color = Dim,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        if (VpnState.connected) {
            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpeedChip(Icons.Rounded.ArrowDownward, fmtBps(VpnState.downBps), "Téléchargement", Accent, Modifier.weight(1f))
                SpeedChip(Icons.Rounded.ArrowUpward, fmtBps(VpnState.upBps), "Envoi", Brand, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(18.dp))
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(BrandGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.serverName, color = TextC, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        if (expired) "Expiré" else (days?.let { "$it jour(s) restant(s)" } ?: "Expiration inconnue"),
                        color = if (expired) Bad else Dim,
                        fontSize = 12.sp
                    )
                }
                Icon(Icons.Rounded.Lock, contentDescription = "Profil verrouillé", tint = Dim, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip(protoLabel(s.type), Icons.Rounded.VpnLock)
            if (s.country.isNotEmpty()) InfoChip(s.country, Icons.Rounded.Language)
        }

        Spacer(Modifier.height(14.dp))
        PanelCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text("Consommation", color = Muted, fontSize = 13.sp)
                Text(
                    if (lim == null) "${fmtBytes(used)} · illimité" else "${fmtBytes(used)} / ${fmtBytes(lim)}",
                    color = TextC,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            LinearProgressIndicator(
                progress = { frac },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(8.dp).clip(RoundedCornerShape(8.dp)),
                color = barColor,
                trackColor = Panel2
            )
        }

        if (!supported) {
            Spacer(Modifier.height(12.dp))
            WarnCard("Les serveurs de type ${s.type} ne sont pas encore pris en charge par cette version.", Warn)
        }
        if (expired) {
            Spacer(Modifier.height(12.dp))
            WarnCard("Ce token a expiré. Demande un nouveau token à ton revendeur.", Bad)
        }
        if (limitReached) {
            Spacer(Modifier.height(12.dp))
            WarnCard("La limite de données de ce token est atteinte.", Bad)
        }
        if (!VpnState.connected && !VpnState.connecting && VpnState.status != "Déconnecté" && VpnState.status != "Connexion…") {
            Spacer(Modifier.height(12.dp))
            WarnCard(VpnState.status, Warn)
        }
        vm.message?.let {
            Spacer(Modifier.height(12.dp))
            WarnCard(it, Bad)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun WarnCard(text: String, tint: Color) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.08f), shape)
            .border(1.dp, tint.copy(alpha = 0.25f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = tint, fontSize = 12.sp)
    }
}

// ====================================================================== Compte

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Muted, fontSize = 14.sp)
        Text(value, color = TextC, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun AccountScreen(vm: AppViewModel, s: Session) {
    val ctx = LocalContext.current
    val days = s.daysLeft()
    ScreenColumn {
        Text("Mon accès", color = TextC, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(BrandGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(s.serverName, color = TextC, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        protoLabel(s.type) + (if (s.country.isNotEmpty()) " · " + s.country else ""),
                        color = Dim,
                        fontSize = 13.sp
                    )
                }
            }
        }
        PanelCard {
            InfoRow("Token", s.token.take(4) + " •••• •••• " + s.token.takeLast(4))
            InfoRow("Expiration", if (s.expiresAt.length >= 10) s.expiresAt.take(10) else "—")
            InfoRow("Jours restants", days?.let { if (it > 0) it.toString() else "expiré" } ?: "—")
            InfoRow("Appareil", Device.id(ctx))
        }
        Text(
            "Ce profil est verrouillé : il vient de ton token et seul l'administrateur peut le modifier. " +
                "Il n'est pas possible d'ajouter ni de modifier une configuration depuis l'application.",
            color = Dim,
            fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { vm.refresh() }, enabled = !vm.busy) { Text(if (vm.busy) "Actualisation..." else "Actualiser") }
            OutlinedButton(onClick = { vm.logout() }) { Text("Changer de token", color = Bad) }
        }
    }
}

// ====================================================================== Usage

@Composable
private fun UsageRing(frac: Float, unlimited: Boolean, centerText: String, sub: String) {
    val color = when {
        unlimited -> Ok
        frac >= 1f -> Bad
        frac >= 0.8f -> Warn
        else -> Accent
    }
    Box(modifier = Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val sz = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Panel2,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = sz,
                style = Stroke(stroke)
            )
            drawArc(
                color = if (unlimited) color.copy(alpha = 0.35f) else color,
                startAngle = -90f,
                sweepAngle = if (unlimited) 360f else 360f * frac,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = sz,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerText, color = TextC, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Dim, fontSize = 12.sp)
        }
    }
}

@Composable
fun UsageScreen(s: Session) {
    val lim = s.limitBytes
    val used = s.usedBytes + (if (VpnState.connected) VpnState.sessionBytes else 0L)
    val frac = if (lim == null || lim <= 0L) 0f else (used.toDouble() / lim).coerceIn(0.0, 1.0).toFloat()
    val days = s.daysLeft()
    ScreenColumn {
        Text("Consommation", color = TextC, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            UsageRing(
                frac = frac,
                unlimited = lim == null,
                centerText = if (lim == null) "∞" else "${(frac * 100).toInt()} %",
                sub = if (lim == null) "données illimitées" else "du quota utilisé"
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("Utilisé", fmtBytes(used), Modifier.weight(1f))
            StatBox("Limite", if (lim == null) "illimitée" else fmtBytes(lim), Modifier.weight(1f))
            StatBox("Restant", if (lim == null) "illimité" else fmtBytes((lim - used).coerceAtLeast(0L)), Modifier.weight(1f))
        }
        PanelCard {
            InfoRow("Jours restants", days?.let { if (it > 0) it.toString() else "expiré" } ?: "—")
            InfoRow("Session en cours", if (VpnState.connected) fmtBytes(VpnState.sessionBytes) else "—")
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .background(Panel2, shape)
            .border(1.dp, Line, shape)
            .padding(12.dp)
    ) {
        Text(label, color = Dim, fontSize = 11.sp)
        Text(value, color = TextC, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
    }
}

// ====================================================================== Historique

@Composable
fun HistoryScreen() {
    ScreenColumn {
        Text("Historique", color = TextC, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        PanelCard {
            val lines = VpnState.log.toList().asReversed()
            Text(
                if (lines.isEmpty()) "Aucune activité pour l'instant." else lines.joinToString("\n"),
                color = TextC,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
        OutlinedButton(onClick = { VpnState.clearLog() }) { Text("Effacer le journal") }
    }
}

// ====================================================================== Réglages

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Brand.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextC, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Dim, fontSize = 12.sp)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Dim)
    }
}

@Composable
private fun ChoicePill(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Brand else Panel2)
            .border(1.dp, if (selected) Brand else Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) OnBrand else Muted, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SettingsScreen(open: (Sub) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var mtu by remember { mutableIntStateOf(prefs.mtu) }

    ScreenColumn {
        Text("Réglages", color = TextC, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        PanelCard {
            Text("MTU", color = TextC, fontWeight = FontWeight.SemiBold)
            Text(
                "8500 est plus rapide ; essaie 1500 si certains sites se chargent mal. Appliqué à la prochaine connexion.",
                color = Dim,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(1500, 8500).forEach { v ->
                    ChoicePill(v.toString(), mtu == v) {
                        mtu = v
                        prefs.mtu = v
                    }
                }
            }
        }

        PanelCard {
            SettingRow(
                Icons.Rounded.Apps,
                "Applications exclues",
                "${prefs.excluded.size} application(s) contournent le VPN"
            ) { open(Sub.APPS) }
        }

        Text("Outils", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        PanelCard {
            SettingRow(Icons.Rounded.Language, "IP Finder", "Voir ton IP publique et ton pays") { open(Sub.IPFINDER) }
            SettingRow(Icons.Rounded.NetworkCheck, "Auto Ping", "Mesurer la latence en continu") { open(Sub.PING) }
            SettingRow(Icons.Rounded.Speed, "Speedtest", "Débit montant et descendant") { open(Sub.SPEED) }
            SettingRow(Icons.Rounded.Share, "Share Net", "Partager le VPN par point d'accès") { open(Sub.SHARE) }
            SettingRow(Icons.Rounded.Link, "Short URL", "Raccourcir un lien") { open(Sub.SHORT) }
            SettingRow(Icons.Rounded.Fingerprint, "Hardware ID", "Identifiant lié à ton token") { open(Sub.HWID) }
            SettingRow(Icons.Rounded.Shield, "Response checker", "Tester un payload sur un serveur") { open(Sub.CHECKER) }
        }

        PanelCard {
            SettingRow(Icons.Rounded.MenuBook, "Guide", "Comment utiliser l'application") { open(Sub.GUIDE) }
        }
        Text(
            "NEXA VPN PRO · version $APP_VERSION",
            color = Dim,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ExcludedAppsScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var excluded by remember { mutableStateOf(prefs.excluded) }
    var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .filter { it.first != ctx.packageName }
                .sortedBy { it.second.lowercase() }
        }
    }

    val list = apps
    if (list == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Chargement des applications…", color = Muted)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            items(list) { app ->
                val checked = excluded.contains(app.first)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val n = HashSet(excluded)
                            if (checked) n.remove(app.first) else n.add(app.first)
                            excluded = n
                            prefs.excluded = n
                        }
                        .padding(vertical = 6.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(app.second, color = TextC)
                        Text(app.first, color = Dim, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ====================================================================== Guide

@Composable
fun GuideScreen() {
    ScreenColumn {
        GuideBlock(
            "Démarrer",
            "1. Saisis le token fourni par ton revendeur (écran d'activation).\n" +
                "2. Sur l'accueil, appuie sur le grand bouton et accepte la demande VPN d'Android.\n" +
                "3. Quand « VPN Actif » s'affiche, tout le trafic du téléphone passe par le serveur."
        )
        GuideBlock(
            "Consommation",
            "L'onglet Usage montre les données utilisées par ton token. Quand la limite est atteinte, " +
                "ou quand le token expire, la connexion est coupée automatiquement."
        )
        GuideBlock(
            "Outils",
            "Dans Réglages : IP Finder, Auto Ping, Speedtest, Short URL, Hardware ID, Response checker " +
                "et Share Net (partager la connexion par point d'accès)."
        )
        GuideBlock(
            "En cas de problème",
            "• « Identifiants refusés » : contacte ton revendeur.\n" +
                "• « Délai dépassé » : essaie un autre réseau ou un autre token.\n" +
                "• Certains sites lents : essaie un MTU de 1500 dans les réglages.\n" +
                "• L'onglet Historique indique la dernière étape qui a échoué."
        )
    }
}

@Composable
private fun GuideBlock(title: String, body: String) {
    PanelCard {
        Text(title, color = Accent, fontWeight = FontWeight.SemiBold)
        Text(body, color = TextC, modifier = Modifier.padding(top = 6.dp))
    }
}
