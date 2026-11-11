package com.neonplayer

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun NeonDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)?,
    confirmText: String = "Enregistrer",
    confirmEnabled: Boolean = true,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(16.dp).fillMaxWidth().heightIn(max = 620.dp),
            shape = RoundedCornerShape(20.dp), color = NeonSurface,
            border = BorderStroke(1.dp, NeonBrush)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                val m = Modifier.weight(1f, fill = false)
                Column(if (scroll) m.verticalScroll(rememberScrollState()) else m) { content() }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Annuler") }
                    if (onConfirm != null) TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmText) }
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    NeonDialog("Confirmation", onDismiss, onConfirm = onConfirm, confirmText = "Supprimer") {
        Text(text)
    }
}

@Composable
fun NameImageDialog(
    title: String, initialName: String, initialImage: String?, round: Boolean, icon: ImageVector,
    onSave: (String, String?) -> Unit, onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var img by remember { mutableStateOf(initialImage) }
    val pick = rememberImagePicker { img = it }
    NeonDialog(title, onDismiss, onConfirm = { onSave(name.trim(), img); onDismiss() }, confirmEnabled = name.isNotBlank()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Cover(img, Modifier.size(120.dp).clickable { pick() }, if (round) CircleShape else RoundedCornerShape(16.dp), icon)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = pick) { Text("Choisir une image") }
            if (img != null) TextButton(onClick = { img = null }) { Text("Retirer") }
        }
        OutlinedTextField(
            name, { name = it }, label = { Text("Nom") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), colors = neonFieldColors()
        )
    }
}

@Composable
fun TrackEditDialog(vm: LibraryVM, t: TrackUi, onDismiss: () -> Unit) {
    val artists by vm.artists.collectAsState()
    val albums by vm.albums.collectAsState()
    var title by remember { mutableStateOf(t.title) }
    var cover by remember { mutableStateOf(t.track.cover) }
    var sel by remember { mutableStateOf(t.artists.map { it.id }.toSet()) }
    var albumId by remember { mutableStateOf(t.track.albumId) }
    var newArtist by remember { mutableStateOf("") }
    val pick = rememberImagePicker { cover = it }
    val shownCover = cover ?: albums.find { it.id == albumId }?.cover

    NeonDialog(
        "Modifier le titre", onDismiss, confirmEnabled = title.isNotBlank(),
        onConfirm = {
            vm.saveTrack(t.track.copy(title = title.trim(), cover = cover, albumId = albumId), sel.toList())
            onDismiss()
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(shownCover, Modifier.size(84.dp).clickable { pick() })
            Spacer(Modifier.width(12.dp))
            Column {
                NeonButton("Pochette", pick, icon = Icons.Default.Image)
                if (cover != null) TextButton(onClick = { cover = null }) { Text("Retirer la pochette") }
            }
        }
        Text("Sans pochette propre, le titre prend celle de son album.", color = TextLo, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            title, { title = it }, label = { Text("Titre") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), colors = neonFieldColors()
        )
        Spacer(Modifier.height(12.dp))
        Text("Artistes (featuring possible)", color = Blue)
        Column(Modifier.heightIn(max = 170.dp).verticalScroll(rememberScrollState())) {
            artists.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { sel = if (a.id in sel) sel - a.id else sel + a.id },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(a.id in sel, null, colors = CheckboxDefaults.colors(checkedColor = Violet))
                    Spacer(Modifier.width(8.dp))
                    Text(a.name)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                newArtist, { newArtist = it }, placeholder = { Text("Nouvel artiste") }, singleLine = true,
                modifier = Modifier.weight(1f), colors = neonFieldColors()
            )
            IconButton(onClick = {
                if (newArtist.isNotBlank()) {
                    vm.createArtist(newArtist.trim(), null) { id -> sel = sel + id }
                    newArtist = ""
                }
            }) { Icon(Icons.Default.Add, "Ajouter", tint = Violet) }
        }
        Spacer(Modifier.height(12.dp))
        Text("Album", color = Blue)
        Column(Modifier.heightIn(max = 170.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().clickable { albumId = null }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(albumId == null, null, colors = RadioButtonDefaults.colors(selectedColor = Violet))
                Spacer(Modifier.width(8.dp)); Text("Aucun")
            }
            albums.forEach { al ->
                Row(Modifier.fillMaxWidth().clickable { albumId = al.id }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(albumId == al.id, null, colors = RadioButtonDefaults.colors(selectedColor = Violet))
                    Spacer(Modifier.width(8.dp)); Text(al.name)
                }
            }
        }
    }
}

@Composable
fun PickTracksDialog(vm: LibraryVM, title: String, exclude: Set<Long>, onPick: (List<Long>) -> Unit, onDismiss: () -> Unit) {
    val all by vm.tracks.collectAsState()
    var q by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf(setOf<Long>()) }
    val list = remember(all, q) { all.filter { it.id !in exclude && it.matches(q) } }
    NeonDialog(
        title, onDismiss, confirmText = "Ajouter (${sel.size})", confirmEnabled = sel.isNotEmpty(),
        onConfirm = { onPick(sel.toList()); onDismiss() }, scroll = false
    ) {
        SearchField(q, { q = it })
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) Text("Aucun titre disponible.", color = TextLo)
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(list, key = { it.id }) { t ->
                Row(
                    Modifier.fillMaxWidth().clickable { sel = if (t.id in sel) sel - t.id else sel + t.id }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(t.id in sel, null, colors = CheckboxDefaults.colors(checkedColor = Violet))
                    Spacer(Modifier.width(8.dp))
                    Cover(t.cover, Modifier.size(40.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artistText.ifBlank { "Artiste inconnu" }, color = TextLo, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun AddToPlaylistDialog(vm: LibraryVM, ids: List<Long>, onDone: () -> Unit = {}, onDismiss: () -> Unit) {
    val pls by vm.playlists.collectAsState()
    var newName by remember { mutableStateOf("") }
    NeonDialog("Ajouter à une playlist", onDismiss, onConfirm = null) {
        pls.forEach { p ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.addToPlaylist(p.id, ids); onDone(); onDismiss() }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.QueueMusic, null, tint = Violet)
                Spacer(Modifier.width(12.dp))
                Text(p.name)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                newName, { newName = it }, placeholder = { Text("Nouvelle playlist") }, singleLine = true,
                modifier = Modifier.weight(1f), colors = neonFieldColors()
            )
            IconButton(onClick = {
                if (newName.isNotBlank()) {
                    vm.createPlaylist(newName.trim(), null) { id -> vm.addToPlaylist(id, ids) }
                    onDone()
                    onDismiss()
                }
            }) { Icon(Icons.Default.Add, "Créer", tint = Violet) }
        }
    }
}

@Composable
fun PickAlbumDialog(vm: LibraryVM, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    val albums by vm.albums.collectAsState()
    NeonDialog("Ajouter à un album", onDismiss, onConfirm = null) {
        if (albums.isEmpty()) Text("Aucun album. Créez-en un depuis la page d'un artiste.", color = TextLo)
        albums.forEach { al ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(al.id); onDismiss() }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Album, null, tint = Violet)
                Spacer(Modifier.width(12.dp))
                Text(al.name)
            }
        }
        TextButton(onClick = { onPick(null); onDismiss() }) { Text("Retirer de tout album") }
    }
}
