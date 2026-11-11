package com.neonplayer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

@Composable
fun MiniPlayer(vm: LibraryVM) {
    val hub = vm.hub
    val tracks by vm.tracks.collectAsState()
    val cur by hub.currentId.collectAsState()
    val playing by hub.isPlaying.collectAsState()
    val t = remember(cur, tracks) { tracks.firstOrNull { it.id == cur } }
    var frac by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(cur) {
        while (true) {
            val d = hub.duration()
            frac = if (d > 0) (hub.position().toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }
    if (t == null) return
    Column(Modifier.fillMaxWidth().background(NeonSurface).neonBorder(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))) {
        Row(
            Modifier.fillMaxWidth().clickable { vm.showPlayer = true }.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Cover(t.cover, Modifier.size(44.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(t.artistText.ifBlank { "Artiste inconnu" }, color = TextLo, maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { hub.togglePlay() }) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Lecture/Pause", tint = Violet, modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = { hub.next() }) { Icon(Icons.Default.SkipNext, "Suivant", tint = TextHi) }
        }
        LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(2.dp), color = Violet, trackColor = NeonCard)
    }
}

@Composable
fun PlayerScreen(vm: LibraryVM) {
    val hub = vm.hub
    val tracks by vm.tracks.collectAsState()
    val cur by hub.currentId.collectAsState()
    val playing by hub.isPlaying.collectAsState()
    val shuffle by hub.shuffle.collectAsState()
    val repeat by hub.repeat.collectAsState()
    val speed by hub.speed.collectAsState()
    val sleep by hub.sleepLeftMs.collectAsState()
    val t = remember(cur, tracks) { tracks.firstOrNull { it.id == cur } }
    var eq by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<TrackUi?>(null) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(0f) }
    val ctx = LocalContext.current
    val exporting by vm.exporting.collectAsState()
    val loopA by hub.loopA.collectAsState()
    val loopB by hub.loopB.collectAsState()
    var showExport by remember { mutableStateOf(false) }
    var exportFactor by remember { mutableFloatStateOf(1f) }
    var pendingExport by remember { mutableStateOf<Pair<TrackUi, Float>?>(null) }
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mp4")) { uri ->
        val p = pendingExport
        if (uri != null && p != null) vm.exportModified(p.first, p.second, uri)
    }

    BackHandler { if (eq) eq = false else vm.showPlayer = false }
    LaunchedEffect(cur) {
        while (true) { pos = hub.position(); dur = hub.duration(); delay(250) }
    }

    Surface(Modifier.fillMaxSize(), color = NeonBg) {
        if (eq) {
            EqualizerScreen(vm) { eq = false }
        } else {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.showPlayer = false }) { Icon(Icons.Default.KeyboardArrowDown, "Fermer", modifier = Modifier.size(32.dp)) }
                    Spacer(Modifier.weight(1f))
                    sleep?.let { Text(fmt(it), color = Violet) }
                    IconButton(onClick = { showTimer = true }) { Icon(Icons.Default.Bedtime, "Minuterie", tint = if (sleep != null) Violet else TextHi) }
                    IconButton(onClick = { eq = true }) { Icon(Icons.Default.Equalizer, "Égaliseur") }
                    IconButton(onClick = { showQueue = true }) { Icon(Icons.Default.QueueMusic, "File d'attente") }
                }
                if (t == null) {
                    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) { Text("Aucune lecture en cours", color = TextLo) }
                } else {
                    Cover(t.cover, Modifier.fillMaxWidth(0.85f).aspectRatio(1f).align(Alignment.CenterHorizontally), RoundedCornerShape(28.dp))
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(t.artistText.ifBlank { "Artiste inconnu" }, color = TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { shareTrack(ctx, t) }) { Icon(Icons.Default.Share, "Partager", tint = Blue) }
                        IconButton(onClick = { edit = t }) { Icon(Icons.Default.Edit, "Modifier", tint = Blue) }
                        IconButton(onClick = { vm.toggleFavorite(t) }) {
                            Icon(if (t.track.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favori", tint = Violet)
                        }
                    }
                    val max = dur.toFloat().coerceAtLeast(1f)
                    Slider(
                        value = (if (dragging) dragPos else pos.toFloat()).coerceIn(0f, max),
                        onValueChange = { dragging = true; dragPos = it },
                        onValueChangeFinished = { hub.seekTo(dragPos.toLong()); dragging = false },
                        valueRange = 0f..max,
                        colors = SliderDefaults.colors(thumbColor = Violet, activeTrackColor = Blue, inactiveTrackColor = NeonCard)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmt(if (dragging) dragPos.toLong() else pos), color = TextLo, style = MaterialTheme.typography.bodySmall)
                        Text(fmt(dur), color = TextLo, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { hub.setShuffle(!shuffle) }) {
                            Icon(Icons.Default.Shuffle, "Aléatoire", tint = if (shuffle) Violet else TextLo)
                        }
                        IconButton(onClick = { hub.previous() }) { Icon(Icons.Default.SkipPrevious, "Précédent", modifier = Modifier.size(38.dp)) }
                        Box(
                            Modifier.size(72.dp).clip(CircleShape).background(NeonBrush).clickable { hub.togglePlay() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Lecture/Pause", tint = ThemeState.current.onAccent, modifier = Modifier.size(42.dp))
                        }
                        IconButton(onClick = { hub.next() }) { Icon(Icons.Default.SkipNext, "Suivant", modifier = Modifier.size(38.dp)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        NeonChip(repeat == Player.REPEAT_MODE_OFF, "Défiler") { hub.setRepeat(Player.REPEAT_MODE_OFF) }
                        NeonChip(repeat == Player.REPEAT_MODE_ONE, "Boucle du titre") { hub.setRepeat(Player.REPEAT_MODE_ONE) }
                        NeonChip(repeat == Player.REPEAT_MODE_ALL, "Boucle liste") { hub.setRepeat(Player.REPEAT_MODE_ALL) }
                    }
                    Spacer(Modifier.height(16.dp))
                    var sp by remember(speed) { mutableFloatStateOf(speed) }
                    val card = RoundedCornerShape(20.dp)
                    Column(Modifier.fillMaxWidth().clip(card).background(NeonCard).neonBorder(card).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Vitesse & hauteur", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text("×" + String.format(Locale.FRANCE, "%.2f", sp), color = Violet, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { exportFactor = sp; showExport = true }, enabled = exporting == null) {
                                Icon(Icons.Default.Download, "Télécharger la version modifiée", tint = Blue)
                            }
                        }
                        exporting?.let { p ->
                            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = Violet, trackColor = NeonBg)
                            Text("Export en cours… ${(p * 100).toInt()} %", color = TextLo, style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = sp, onValueChange = { sp = (it * 100).roundToInt() / 100f },
                            onValueChangeFinished = { hub.setSpeed(sp) }, valueRange = 0.2f..3f,
                            colors = SliderDefaults.colors(thumbColor = Violet, activeTrackColor = Blue, inactiveTrackColor = NeonCard)
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("×0,2", color = TextLo, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { sp = 1f; hub.setSpeed(1f) }) { Text("Réinitialiser ×1") }
                            Text("×3", color = TextLo, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    val abShape = RoundedCornerShape(20.dp)
                    val la = loopA
                    val lb = loopB
                    Column(Modifier.fillMaxWidth().clip(abShape).background(NeonCard).neonBorder(abShape).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Boucle d'un passage", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text(
                                if (lb != null && (la ?: 0L) < lb) "active" else "inactive",
                                color = if (lb != null && (la ?: 0L) < lb) Violet else TextLo
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NeonButton("A · " + fmt(la ?: 0L), { hub.setLoopA(hub.position()) }, Modifier.weight(1f))
                            NeonButton("B · " + (lb?.let { fmt(it) } ?: "--:--"), { hub.setLoopB(hub.position()) }, Modifier.weight(1f))
                        }
                        TextButton(onClick = { hub.clearLoop() }) { Text("Effacer la boucle") }
                        Text("Lancez la lecture, appuyez sur A au début du passage et sur B à la fin.", color = TextLo, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    edit?.let { TrackEditDialog(vm, it) { edit = null } }

    if (showExport && t != null) {
        val label = String.format(Locale.FRANCE, "%.2f", exportFactor)
        NeonDialog("Version modifiée", { showExport = false }, onConfirm = null) {
            Text("« ${t.title} » à ×$label (vitesse et hauteur), au format M4A.", color = TextLo)
            Spacer(Modifier.height(12.dp))
            NeonButton(
                "Enregistrer sur l'appareil",
                {
                    pendingExport = t to exportFactor
                    showExport = false
                    createDoc.launch(t.title.map { c -> if (c in "/\\:*?<>|") ' ' else c }.joinToString("") + " x" + label.replace(',', '.') + ".m4a")
                },
                Modifier.fillMaxWidth(), Icons.Default.Download
            )
            Spacer(Modifier.height(8.dp))
            NeonButton(
                "Ajouter à la bibliothèque",
                { vm.exportModified(t, exportFactor, null); showExport = false },
                Modifier.fillMaxWidth(), Icons.Default.LibraryAdd
            )
        }
    }

    if (showTimer) {
        NeonDialog("Minuterie de sommeil", { showTimer = false }, onConfirm = null) {
            Text("La lecture se mettra en pause à la fin du délai.", color = TextLo)
            Spacer(Modifier.height(8.dp))
            listOf(5, 10, 15, 30, 45, 60, 90).forEach { m ->
                TextButton(onClick = { hub.startSleep(m); showTimer = false }, modifier = Modifier.fillMaxWidth()) { Text("$m minutes") }
            }
            if (sleep != null) TextButton(onClick = { hub.cancelSleep(); showTimer = false }, modifier = Modifier.fillMaxWidth()) { Text("Désactiver la minuterie") }
        }
    }

    if (showQueue) {
        val queue by hub.queue.collectAsState()
        val map = remember(tracks) { tracks.associateBy { it.id } }
        NeonDialog("File d'attente (${queue.size})", { showQueue = false }, onConfirm = null, scroll = false) {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(queue) { i, id ->
                    val q = map[id]
                    Row(
                        Modifier.fillMaxWidth().clickable { hub.jumpTo(i) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Cover(q?.cover, Modifier.size(40.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(q?.title ?: "Titre supprimé", color = if (id == cur) Violet else TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(q?.artistText?.ifBlank { null } ?: "Artiste inconnu", color = TextLo, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        IconButton(onClick = { hub.removeAt(i) }) { Icon(Icons.Default.Close, "Retirer", tint = TextLo) }
                    }
                }
            }
        }
    }
}

private fun eqPreset(name: String, i: EqInfo): List<Int> = List(i.bands) { b ->
    val p = if (i.bands > 1) b / (i.bands - 1f) else 0f
    val f = when (name) {
        "Basses" -> (1f - p) * 0.7f
        "Aigus" -> p * 0.7f
        "Voix" -> (1f - abs(p - 0.55f) * 2.2f).coerceAtLeast(0f) * 0.5f
        else -> 0f
    }
    (f * i.max).toInt().coerceIn(i.min, i.max)
}

@Composable
fun EqualizerScreen(vm: LibraryVM, onBack: () -> Unit) {
    val hub = vm.hub
    val prefs = vm.prefs
    val connected by hub.connected.collectAsState()
    var info by remember { mutableStateOf<EqInfo?>(null) }
    var on by remember { mutableStateOf(prefs.eqEnabled) }
    var levels by remember { mutableStateOf(prefs.eqLevels) }
    LaunchedEffect(connected) { if (connected) hub.eqInfo { info = it } }

    fun commit(l: List<Int>) { levels = l; prefs.eqLevels = l; hub.eqApply() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour") }
            Text("Égaliseur", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Switch(on, { on = it; prefs.eqEnabled = it; hub.eqApply() }, colors = SwitchDefaults.colors(checkedTrackColor = Violet))
        }
        val i = info
        if (i == null) {
            Text("Égaliseur indisponible pour le moment. Lancez une lecture puis réessayez.", color = TextLo, modifier = Modifier.padding(16.dp))
        } else {
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Plat", "Basses", "Aigus", "Voix").forEach { n -> NeonChip(false, n) { commit(eqPreset(n, i)) } }
            }
            for (b in 0 until i.bands) {
                val f = i.freqs.getOrElse(b) { 0 }
                val lv = levels.getOrElse(b) { 0 }
                Row {
                    Text(if (f >= 1000) "${f / 1000} kHz" else "$f Hz", Modifier.weight(1f))
                    Text(String.format(Locale.FRANCE, "%+.1f dB", lv / 100f), color = Violet)
                }
                Slider(
                    value = lv.toFloat().coerceIn(i.min.toFloat(), i.max.toFloat()),
                    onValueChange = { v ->
                        levels = levels.toMutableList().also { l -> while (l.size <= b) l.add(0); l[b] = v.toInt() }
                    },
                    onValueChangeFinished = { commit(levels) },
                    valueRange = i.min.toFloat()..i.max.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Violet, activeTrackColor = Blue, inactiveTrackColor = NeonCard)
                )
            }
        }
    }
}
