package com.nexavpn.pro

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

const val APP_VERSION = "0.8.1"

// ---------------------------------------------------------------- palette (bleu électrique + cyan, thème sombre)
val Bg = Color(0xFF070B12)
val Panel = Color(0xFF0C1322)
val Panel2 = Color(0xFF142038)
val Line = Color(0xFF1E2F4D)
val Brand = Color(0xFF00E5FF)
val Accent = Color(0xFF38BDF8)
val TextC = Color(0xFFF1F5F2)
val Muted = Color(0xFF9DB5A6)
val Dim = Color(0xFF6B8576)
val Ok = Color(0xFF22C55E)
val Warn = Color(0xFFF59E0B)
val Bad = Color(0xFFEF4444)
val OnBrand = Color(0xFF04100A)

val BrandGradient = Brush.linearGradient(listOf(Brand, Accent))
val BrandHorizontal = Brush.horizontalGradient(listOf(Brand, Accent))
val CardGradient = Brush.linearGradient(listOf(Color(0xFF12281C), Color(0xFF0B1A12)))

@Composable
fun NexaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Brand,
            onPrimary = OnBrand,
            secondary = Accent,
            background = Bg,
            onBackground = TextC,
            surface = Panel,
            onSurface = TextC,
            surfaceVariant = Panel2,
            outline = Line
        ),
        content = content
    )
}

// ---------------------------------------------------------------- composants communs

@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Bg, Color(0xFF0A2216))))
            .drawBehind {
                val r1 = 360.dp.toPx()
                val c1 = Offset(size.width, 0f)
                drawCircle(
                    brush = Brush.radialGradient(listOf(Brand.copy(alpha = 0.10f), Color.Transparent), center = c1, radius = r1),
                    radius = r1,
                    center = c1
                )
                val r2 = 280.dp.toPx()
                val c2 = Offset(0f, size.height)
                drawCircle(
                    brush = Brush.radialGradient(listOf(Accent.copy(alpha = 0.07f), Color.Transparent), center = c2, radius = r2),
                    radius = r2,
                    center = c2
                )
            },
        content = content
    )
}

@Composable
fun PanelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CardGradient, shape)
            .border(1.dp, Line, shape)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun GradientButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(if (enabled) BrandHorizontal else SolidColor(Panel2))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) OnBrand else Dim, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun InfoChip(text: String, icon: ImageVector) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .background(Brand.copy(alpha = 0.10f), shape)
            .border(1.dp, Brand.copy(alpha = 0.20f), shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

fun fmtBps(bps: Long): String = when {
    bps >= 1_000_000 -> String.format(java.util.Locale.FRANCE, "%.1f Mb/s", bps / 1_000_000.0)
    bps >= 1_000 -> String.format(java.util.Locale.FRANCE, "%.0f Kb/s", bps / 1_000.0)
    else -> "$bps b/s"
}

fun protoLabel(type: String): String = when (type.lowercase()) {
    "ssh" -> "SSH"
    "vmess" -> "VMess"
    "vless" -> "VLESS"
    "trojan" -> "Trojan"
    else -> type.uppercase()
}

// ---------------------------------------------------------------- racine

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    AppBackground {
        val s = vm.session
        if (s == null) ActivationScreen(vm) else MainShell(vm, s)
    }
}

@Composable
private fun ActivationScreen(vm: AppViewModel) {
    var token by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(BrandGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
        }
        Text(
            text = buildAnnotatedString {
                append("NEXA ")
                withStyle(SpanStyle(color = Brand)) { append("VPN PRO") }
            },
            color = TextC,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            "Saisis le token d'activation fourni par ton revendeur.",
            color = Muted,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )
        DeviceIdCard(vm.deviceId)
        Spacer(Modifier.height(14.dp))
        PanelCard {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Token d'activation") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth()
            )
            GradientButton(
                text = if (vm.busy) "Vérification..." else "Activer",
                enabled = !vm.busy,
                modifier = Modifier.padding(top = 16.dp)
            ) { vm.submit(token) }
            vm.message?.let {
                Text(it, color = Bad, modifier = Modifier.padding(top = 12.dp))
            }
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.material3.OutlinedButton(
            onClick = {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(Config.PORTAL_URL))
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        ) {
            Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(18.dp), tint = Brand)
            Spacer(Modifier.width(8.dp))
            Text("J'ai payé, vérifier ma transaction", color = TextC)
        }
        Spacer(Modifier.height(8.dp))
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.importUri(uri)
        }
        OutlinedButton(
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
        ) { Text("Importer un fichier .nexa") }
    }
}

@Composable
fun DeviceIdCard(id: String) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    PanelCard {
        Text("Identifiant de ton appareil", color = Muted, fontSize = 12.sp)
        Text(
            id,
            color = TextC,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(id)) }) { Text("Copier") }
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "Mon identifiant d'appareil : $id")
                ctx.startActivity(Intent.createChooser(send, "Envoyer à mon revendeur"))
            }) { Text("Envoyer") }
        }
        Text(
            "Donne cet identifiant à ton revendeur pour recevoir ta configuration.",
            color = Dim,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// ---------------------------------------------------------------- navigation : 5 onglets + écrans secondaires

enum class Sub(val title: String) {
    APPS("Applications exclues"),
    IPFINDER("IP Finder"),
    PING("Auto Ping"),
    SHARE("Share Net"),
    SHORT("Short URL"),
    SPEED("Speedtest"),
    HWID("Hardware ID"),
    CHECKER("Response checker"),
    GUIDE("Guide")
}

@Composable
private fun MainShell(vm: AppViewModel, s: Session) {
    var tab by remember { mutableIntStateOf(0) }
    var sub by remember { mutableStateOf<Sub?>(null) }

    BackHandler(enabled = sub != null || tab != 0) {
        if (sub != null) sub = null else tab = 0
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val current = sub
            if (current != null) {
                SubScreen(current) { sub = null }
            } else {
                when (tab) {
                    0 -> HomeScreen(vm, s) { tab = 3 }
                    1 -> AccountScreen(vm, s)
                    2 -> UsageScreen(s)
                    3 -> HistoryScreen()
                    else -> SettingsScreen { sub = it }
                }
            }
        }
        if (sub == null) {
            BottomBar(tab) { tab = it }
        }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        "Accueil" to Icons.Rounded.Home,
        "Compte" to Icons.Rounded.Person,
        "Usage" to Icons.Rounded.DataUsage,
        "Historique" to Icons.Rounded.History,
        "Réglages" to Icons.Rounded.Settings
    )
    NavigationBar(containerColor = Panel) {
        items.forEachIndexed { i, (label, icon) ->
            NavigationBarItem(
                selected = selected == i,
                onClick = { onSelect(i) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Accent,
                    selectedTextColor = Accent,
                    indicatorColor = Brand.copy(alpha = 0.15f),
                    unselectedIconColor = Dim,
                    unselectedTextColor = Dim
                )
            )
        }
    }
}

@Composable
private fun SubScreen(sub: Sub, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("←", color = TextC, fontSize = 22.sp) }
            Text(sub.title, color = TextC, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (sub) {
                Sub.APPS -> ExcludedAppsScreen()
                Sub.IPFINDER -> IpFinderScreen()
                Sub.PING -> AutoPingScreen()
                Sub.SHARE -> ShareNetScreen()
                Sub.SHORT -> ShortUrlScreen()
                Sub.SPEED -> SpeedtestScreen()
                Sub.HWID -> HardwareIdScreen()
                Sub.CHECKER -> ResponseCheckerScreen()
                Sub.GUIDE -> GuideScreen()
            }
        }
    }
}
