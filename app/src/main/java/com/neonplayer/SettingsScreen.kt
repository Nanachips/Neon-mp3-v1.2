package com.neonplayer

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

const val GITHUB_URL = "https://github.com/Nanachips/Mp3-player-apk"

@Composable
fun SettingsScreen(vm: LibraryVM) {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxV = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var fade by remember { mutableFloatStateOf(vm.prefs.fadeSec.toFloat()) }
    var confirmReset by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AppTheme?>(null) }
    var deleting by remember { mutableStateOf<AppTheme?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            val v = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            if (v != vol) vol = v
            delay(500)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Réglages", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Text("Volume", color = Blue, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VolumeDown, null, tint = TextLo)
            Slider(
                value = vol,
                onValueChange = {
                    vol = it.roundToInt().toFloat()
                    try { am.setStreamVolume(AudioManager.STREAM_MUSIC, vol.toInt(), 0) } catch (_: Exception) {}
                },
                valueRange = 0f..maxV.toFloat(),
                steps = (maxV - 1).coerceAtLeast(0),
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Violet, activeTrackColor = Blue, inactiveTrackColor = NeonCard)
            )
            Icon(Icons.Default.VolumeUp, null, tint = TextLo)
        }
        Text("${(vol / maxV * 100).roundToInt()} %", color = TextLo, style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(20.dp))
        Text("Fondu entre les titres", color = Blue, fontWeight = FontWeight.Bold)
        Slider(
            value = fade, onValueChange = { fade = it.roundToInt().toFloat() },
            onValueChangeFinished = { vm.prefs.fadeSec = fade.toInt() },
            valueRange = 0f..8f, steps = 7,
            colors = SliderDefaults.colors(thumbColor = Violet, activeTrackColor = Blue, inactiveTrackColor = NeonCard)
        )
        Text(
            (if (fade.toInt() == 0) "Désactivé" else "${fade.toInt()} s") +
                " · le son monte et descend en douceur au début et à la fin de chaque titre.",
            color = TextLo, style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(20.dp))
        Text("Thème", color = Blue, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        ThemeState.all().forEach { t ->
            val selected = ThemeState.current.id == t.id
            val shape = RoundedCornerShape(16.dp)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(shape)
                    .clickable { ThemeState.current = t; vm.prefs.themeId = t.id }
                    .background(NeonCard)
                    .border(if (selected) 2.dp else 1.dp, if (selected) Violet else TextLo.copy(alpha = 0.3f), shape)
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(t.bg)
                        .border(3.dp, Brush.linearGradient(listOf(t.c1, t.c2)), CircleShape)
                )
                Spacer(Modifier.width(14.dp))
                Text(t.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                if (t.custom) {
                    IconButton(onClick = { editing = t; editorOpen = true }) { Icon(Icons.Default.Edit, "Modifier", tint = TextLo) }
                    IconButton(onClick = { deleting = t }) { Icon(Icons.Default.Delete, "Supprimer", tint = TextLo) }
                }
                if (selected) Icon(Icons.Default.CheckCircle, null, tint = Violet, modifier = Modifier.padding(end = 8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        NeonButton("Créer un thème", { editing = null; editorOpen = true }, Modifier.fillMaxWidth(), Icons.Default.Add)

        Spacer(Modifier.height(20.dp))
        Text("Bibliothèque", color = Blue, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        NeonButton("Actualiser les dossiers importés", { vm.refreshFolders() }, Modifier.fillMaxWidth(), Icons.Default.Refresh)
        Spacer(Modifier.height(8.dp))
        NeonButton("Réinitialiser les statistiques d'écoute", { confirmReset = true }, Modifier.fillMaxWidth(), Icons.Default.RestartAlt)

        Spacer(Modifier.height(20.dp))
        Text("Application", color = Blue, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        NeonButton(
            "Partager l'application",
            {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Neon")
                    putExtra(Intent.EXTRA_TEXT, "Découvre Neon, mon lecteur de musique : $GITHUB_URL")
                }
                ctx.startActivity(Intent.createChooser(send, "Partager Neon"))
            },
            Modifier.fillMaxWidth(), Icons.Default.Share
        )
        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Image(
                painterResource(R.drawable.logo), "Logo Neon",
                Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(8.dp))
            Text("Neon · version 1.2", color = TextLo, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (confirmReset) ConfirmDialog(
        "Effacer toutes les statistiques d'écoute ? Cette action est définitive.",
        onConfirm = { vm.resetStats(); confirmReset = false },
        onDismiss = { confirmReset = false }
    )
    if (editorOpen) ThemeEditorDialog(editing, { ThemeState.saveCustom(it, vm.prefs) }) { editorOpen = false }
    deleting?.let { t ->
        ConfirmDialog(
            "Supprimer le thème « ${t.label} » ?",
            onConfirm = { ThemeState.deleteCustom(t.id, vm.prefs); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
fun HsvPicker(key: Any, color: Color, onChange: (Color) -> Unit) {
    val hsv0 = remember(key) { FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) } }
    var h by remember(key) { mutableFloatStateOf(hsv0[0]) }
    var s by remember(key) { mutableFloatStateOf(hsv0[1]) }
    var v by remember(key) { mutableFloatStateOf(hsv0[2]) }
    fun push() { onChange(Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))) }
    Text("Teinte", color = TextLo, style = MaterialTheme.typography.bodySmall)
    Slider(h, { h = it; push() }, valueRange = 0f..360f)
    Text("Saturation", color = TextLo, style = MaterialTheme.typography.bodySmall)
    Slider(s, { s = it; push() }, valueRange = 0f..1f)
    Text("Luminosité", color = TextLo, style = MaterialTheme.typography.bodySmall)
    Slider(v, { v = it; push() }, valueRange = 0f..1f)
}

@Composable
fun ThemeEditorDialog(initial: AppTheme?, onSave: (AppTheme) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.label ?: "Mon thème") }
    var light by remember { mutableStateOf(initial?.light ?: false) }
    var bg by remember { mutableStateOf(initial?.bg ?: Color(0xFF05050A)) }
    var c1 by remember { mutableStateOf(initial?.c1 ?: Color(0xFF4D8DFF)) }
    var c2 by remember { mutableStateOf(initial?.c2 ?: Color(0xFF9B5CFF)) }
    var target by remember { mutableIntStateOf(1) }
    val preview = makeCustomTheme("p", name, light, bg, c1, c2)
    val grad = Brush.linearGradient(listOf(c1, c2))

    NeonDialog(
        if (initial == null) "Nouveau thème" else "Modifier le thème", onDismiss, confirmEnabled = name.isNotBlank(),
        onConfirm = {
            onSave(makeCustomTheme(initial?.id ?: "custom_" + System.currentTimeMillis(), name.trim(), light, bg, c1, c2))
            onDismiss()
        }
    ) {
        Box(
            Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(16.dp)).background(preview.bg)
                .border(2.dp, grad, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(grad), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = preview.onAccent)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(name, color = preview.hi, fontWeight = FontWeight.Bold)
                    Text("Aperçu", color = preview.lo, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            name, { name = it }, label = { Text("Nom du thème") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), colors = neonFieldColors()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Thème clair", Modifier.weight(1f))
            Switch(light, { light = it }, colors = SwitchDefaults.colors(checkedTrackColor = Violet))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonChip(target == 0, "Fond") { target = 0 }
            NeonChip(target == 1, "Couleur 1") { target = 1 }
            NeonChip(target == 2, "Couleur 2") { target = 2 }
        }
        HsvPicker(target, when (target) { 0 -> bg; 1 -> c1; else -> c2 }) { col ->
            when (target) { 0 -> bg = col; 1 -> c1 = col; else -> c2 = col }
        }
    }
}
