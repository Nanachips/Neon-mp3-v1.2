package com.neonplayer

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun Cover(
    path: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    icon: ImageVector = Icons.Default.MusicNote
) {
    Box(modifier.clip(shape).background(NeonCard).neonBorder(shape), contentAlignment = Alignment.Center) {
        if (path != null) {
            AsyncImage(
                model = File(path), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(icon, null, tint = Violet, modifier = Modifier.fillMaxSize(0.5f))
        }
    }
}

@Composable
fun rememberImagePicker(onPicked: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val p = withContext(Dispatchers.IO) { Media.saveImageUri(ctx, uri) }
            if (p != null) onPicked(p)
        }
    }
    return { launcher.launch("image/*") }
}

@Composable
fun neonFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Violet,
    unfocusedBorderColor = Blue.copy(alpha = 0.5f),
    cursorColor = Violet,
    focusedLabelColor = Violet
)

@Composable
fun NeonButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector? = null, enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick, modifier = modifier, enabled = enabled,
        border = BorderStroke(1.dp, NeonBrush),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextHi)
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text)
    }
}

@Composable
fun NeonChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Violet.copy(alpha = 0.3f),
            selectedLabelColor = TextHi
        )
    )
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        modifier = modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text("Rechercher…") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, null) } }
        } else null,
        shape = RoundedCornerShape(24.dp), colors = neonFieldColors()
    )
}

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextLo, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionRow(title: String, action: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Blue, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action()
    }
}

@Composable
fun ScreenBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour") }
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    t: TrackUi, playing: Boolean, selecting: Boolean, selected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, menu: @Composable (() -> Unit) -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) Violet.copy(alpha = 0.18f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            Checkbox(selected, null, colors = CheckboxDefaults.colors(checkedColor = Violet))
            Spacer(Modifier.width(8.dp))
        }
        Cover(t.cover, Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.title, color = if (playing) Violet else TextHi, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                t.artistText.ifBlank { "Artiste inconnu" }, color = TextLo,
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (t.track.favorite) Icon(Icons.Default.Favorite, null, tint = Violet, modifier = Modifier.size(18.dp))
        if (!selecting) {
            Box {
                var open by remember { mutableStateOf(false) }
                IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "Options", tint = TextLo) }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
            }
        }
    }
}

class TrackDialogState {
    var edit by mutableStateOf<TrackUi?>(null)
    var playlist by mutableStateOf<TrackUi?>(null)
    var delete by mutableStateOf<TrackUi?>(null)
    val sel = mutableStateListOf<Long>()
    fun toggle(id: Long) { if (id in sel) sel.remove(id) else sel.add(id) }
}

@Composable
fun TrackDialogs(vm: LibraryVM, s: TrackDialogState) {
    s.edit?.let { TrackEditDialog(vm, it) { s.edit = null } }
    s.playlist?.let { AddToPlaylistDialog(vm, listOf(it.id)) { s.playlist = null } }
    s.delete?.let {
        ConfirmDialog(
            "Supprimer « ${it.title} » de l'application ? Le fichier audio reste sur votre appareil.",
            onConfirm = { vm.deleteTracks(listOf(it.id)); s.delete = null },
            onDismiss = { s.delete = null }
        )
    }
}

fun shareTrack(ctx: Context, t: TrackUi) {
    val uri = Uri.parse(t.track.uri)
    val shareUri = if (uri.scheme == "file") {
        FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(uri.path ?: return))
    } else uri
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, shareUri)
        putExtra(Intent.EXTRA_TITLE, t.title)
        clipData = ClipData.newRawUri("", shareUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try { ctx.startActivity(Intent.createChooser(send, "Partager « ${t.title} »")) } catch (_: Exception) {}
}

@Composable
fun StdTrackMenu(vm: LibraryVM, s: TrackDialogState, t: TrackUi, dismiss: () -> Unit, extra: @Composable () -> Unit = {}) {
    val ctx = LocalContext.current
    MenuItem("Sélectionner", Icons.Default.CheckCircle) { dismiss(); s.toggle(t.id) }
    MenuItem("Modifier", Icons.Default.Edit) { dismiss(); s.edit = t }
    MenuItem("Lire ensuite", Icons.Default.PlaylistPlay) { dismiss(); vm.hub.playNext(t) }
    MenuItem("Ajouter à la file", Icons.Default.QueueMusic) { dismiss(); vm.hub.enqueue(t) }
    MenuItem("Ajouter à une playlist", Icons.Default.PlaylistAdd) { dismiss(); s.playlist = t }
    MenuItem(if (t.track.favorite) "Retirer des favoris" else "Ajouter aux favoris", Icons.Default.FavoriteBorder) {
        dismiss(); vm.toggleFavorite(t)
    }
    MenuItem("Partager le fichier", Icons.Default.Share) { dismiss(); shareTrack(ctx, t) }
    extra()
    MenuItem("Supprimer de l'application", Icons.Default.Delete) { dismiss(); s.delete = t }
}

@Composable
fun SelectionBar(vm: LibraryVM, ds: TrackDialogState, list: List<TrackUi>) {
    if (ds.sel.isEmpty()) return
    BackHandler { ds.sel.clear() }
    var playlistDlg by remember { mutableStateOf(false) }
    var albumDlg by remember { mutableStateOf(false) }
    var delDlg by remember { mutableStateOf(false) }
    val ids = ds.sel.toList()
    Row(Modifier.fillMaxWidth().background(NeonCard).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { ds.sel.clear() }) { Icon(Icons.Default.Close, "Annuler") }
        Text("${ids.size} sélectionné(s)", Modifier.weight(1f), fontWeight = FontWeight.Medium)
        IconButton(onClick = { ds.sel.clear(); ds.sel.addAll(list.map { it.id }) }) { Icon(Icons.Default.SelectAll, "Tout sélectionner") }
        IconButton(onClick = { vm.setFavorites(ids, true); ds.sel.clear() }) { Icon(Icons.Default.Favorite, "Favoris") }
        IconButton(onClick = { playlistDlg = true }) { Icon(Icons.Default.PlaylistAdd, "Playlist") }
        IconButton(onClick = { albumDlg = true }) { Icon(Icons.Default.Album, "Album") }
        IconButton(onClick = { delDlg = true }) { Icon(Icons.Default.Delete, "Supprimer") }
    }
    if (playlistDlg) AddToPlaylistDialog(vm, ids, onDone = { ds.sel.clear() }) { playlistDlg = false }
    if (albumDlg) PickAlbumDialog(vm, { vm.setAlbum(ids, it); ds.sel.clear() }) { albumDlg = false }
    if (delDlg) ConfirmDialog(
        "Supprimer ${ids.size} titre(s) de l'application ? Les fichiers audio restent sur votre appareil.",
        onConfirm = { vm.deleteTracks(ids); ds.sel.clear(); delDlg = false },
        onDismiss = { delDlg = false }
    )
}

fun LazyListScope.trackItems(
    vm: LibraryVM, list: List<TrackUi>, cur: Long?, ds: TrackDialogState,
    extra: @Composable (TrackUi, () -> Unit) -> Unit = { _, _ -> }
) {
    itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
        TrackRow(
            t, t.id == cur, selecting = ds.sel.isNotEmpty(), selected = t.id in ds.sel,
            onClick = { if (ds.sel.isNotEmpty()) ds.toggle(t.id) else vm.hub.playQueue(list, i) },
            onLongClick = { ds.toggle(t.id) }
        ) { dismiss ->
            StdTrackMenu(vm, ds, t, dismiss) { extra(t, dismiss) }
        }
    }
}

enum class TrackSort(val label: String) {
    RECENT("Récents"), AZ("A → Z"), ZA("Z → A"), PLAYED("Plus écoutés"), LONG("Plus longs")
}

val libSortLabels = listOf("A → Z", "Z → A", "Récents", "Plus de titres")

fun <T> sortLib(list: List<T>, mode: Int, name: (T) -> String, id: (T) -> Long, count: (T) -> Int): List<T> = when (mode) {
    0 -> list.sortedBy { name(it).lowercase() }
    1 -> list.sortedByDescending { name(it).lowercase() }
    2 -> list.sortedByDescending { id(it) }
    else -> list.sortedByDescending { count(it) }
}

@Composable
fun <T> SortMenu(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.Sort, "Trier", tint = Violet) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(label(o) + if (o == selected) "  ✓" else "") },
                    onClick = { open = false; onSelect(o) }
                )
            }
        }
    }
}

@Composable
fun CollectionHeader(
    cover: String?, round: Boolean, icon: ImageVector, title: String, subtitle: String,
    onEdit: () -> Unit, onDelete: () -> Unit, onPlay: () -> Unit, onShuffle: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Cover(cover, Modifier.size(160.dp), if (round) CircleShape else RoundedCornerShape(20.dp), icon)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(subtitle, color = TextLo)
        Row {
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Modifier", tint = Blue) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Supprimer", tint = Blue) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonButton("Lire", onPlay, icon = Icons.Default.PlayArrow)
            NeonButton("Aléatoire", onShuffle, icon = Icons.Default.Shuffle)
        }
    }
}
