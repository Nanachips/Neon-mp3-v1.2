package com.neonplayer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun AddMenuButton(vm: LibraryVM) {
    var open by remember { mutableStateOf(false) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.importFiles(it) }
    val tree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { if (it != null) vm.importTree(it) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.Add, "Ajouter", tint = Violet, modifier = Modifier.size(30.dp)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem("Ajouter des fichiers audio", Icons.Default.AudioFile) { open = false; files.launch(arrayOf("audio/*", "application/ogg")) }
            MenuItem("Ajouter un dossier entier", Icons.Default.FolderOpen) { open = false; tree.launch(null) }
            MenuItem("Actualiser les dossiers", Icons.Default.Refresh) { open = false; vm.refreshFolders() }
        }
    }
}

@Composable
fun TracksScreen(vm: LibraryVM) {
    val tracks by vm.tracks.collectAsState()
    val stats by vm.stats.collectAsState()
    val cur by vm.hub.currentId.collectAsState()
    var q by remember { mutableStateOf("") }
    var favOnly by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(TrackSort.values().getOrElse(vm.prefs.trackSort) { TrackSort.RECENT }) }
    val ds = remember { TrackDialogState() }
    val list = remember(tracks, q, favOnly, sort, stats) {
        val f = tracks.filter { (!favOnly || it.track.favorite) && it.matches(q) }
        when (sort) {
            TrackSort.RECENT -> f
            TrackSort.AZ -> f.sortedBy { it.title.lowercase() }
            TrackSort.ZA -> f.sortedByDescending { it.title.lowercase() }
            TrackSort.PLAYED -> {
                val m = stats.associate { it.trackId to it.plays }
                f.sortedByDescending { m[it.id] ?: 0 }
            }
            TrackSort.LONG -> f.sortedByDescending { it.track.durationMs }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SelectionBar(vm, ds, list)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Titres", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            AddMenuButton(vm)
        }
        SearchField(q, { q = it }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Row(Modifier.padding(start = 16.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonChip(!favOnly, "Tous (${tracks.size})") { favOnly = false }
            NeonChip(favOnly, "Favoris") { favOnly = true }
            Spacer(Modifier.weight(1f))
            SortMenu(TrackSort.values().toList(), sort, { it.label }) { sort = it; vm.prefs.trackSort = it.ordinal }
        }
        if (list.isNotEmpty()) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton("Tout lire", { vm.hub.playQueue(list, 0, false) }, icon = Icons.Default.PlayArrow)
                NeonButton("Aléatoire", { vm.hub.playShuffled(list) }, icon = Icons.Default.Shuffle)
            }
        }
        when {
            tracks.isEmpty() -> EmptyHint("Aucun titre pour l'instant.\nAppuyez sur ＋ pour importer des fichiers ou un dossier entier.")
            list.isEmpty() -> EmptyHint("Aucun résultat.")
            else -> LazyColumn(Modifier.fillMaxSize()) { trackItems(vm, list, cur, ds) }
        }
    }
    TrackDialogs(vm, ds)
}

@Composable
fun <T> LibGrid(data: List<T>, empty: String, content: @Composable (T) -> Unit) {
    if (data.isEmpty()) EmptyHint(empty)
    else LazyVerticalGrid(
        GridCells.Adaptive(140.dp), contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { items(data) { content(it) } }
}

@Composable
fun CardItem(cover: String?, title: String, subtitle: String, round: Boolean, icon: ImageVector, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Cover(cover, Modifier.fillMaxWidth().aspectRatio(1f), if (round) CircleShape else RoundedCornerShape(16.dp), icon)
        Spacer(Modifier.height(6.dp))
        Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = TextLo, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

@Composable
fun LibraryScreen(vm: LibraryVM) {
    val artists by vm.artists.collectAsState()
    val albums by vm.albums.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val refs by vm.playlistRefs.collectAsState()
    var sub by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    var libSort by remember { mutableIntStateOf(vm.prefs.libSort) }
    var newArtist by remember { mutableStateOf(false) }
    var newPlaylist by remember { mutableStateOf(false) }
    val artistCounts = remember(tracks) { tracks.flatMap { it.artists }.groupingBy { it.id }.eachCount() }
    val albumCounts = remember(tracks) { tracks.mapNotNull { it.track.albumId }.groupingBy { it }.eachCount() }
    val plCounts = remember(refs) { refs.groupingBy { it.playlistId }.eachCount() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Bibliothèque", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            SortMenu(listOf(0, 1, 2, 3), libSort, { libSortLabels[it] }) { libSort = it; vm.prefs.libSort = it }
            if (sub != 1) IconButton(onClick = { if (sub == 0) newArtist = true else newPlaylist = true }) {
                Icon(Icons.Default.Add, "Ajouter", tint = Violet, modifier = Modifier.size(30.dp))
            }
        }
        SearchField(q, { q = it }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonChip(sub == 0, "Artistes") { sub = 0 }
            NeonChip(sub == 1, "Albums") { sub = 1 }
            NeonChip(sub == 2, "Playlists") { sub = 2 }
        }
        when (sub) {
            0 -> LibGrid(sortLib(artists.filter { it.name.contains(q, true) }, libSort, { it.name }, { it.id }, { artistCounts[it.id] ?: 0 }), "Aucun artiste.\nAppuyez sur ＋ pour en créer un.") { a ->
                CardItem(a.photo, a.name, "${artistCounts[a.id] ?: 0} titre(s)", true, Icons.Default.Person) { vm.open(Route.ArtistR(a.id)) }
            }
            1 -> LibGrid(sortLib(albums.filter { it.name.contains(q, true) }, libSort, { it.name }, { it.id }, { albumCounts[it.id] ?: 0 }), "Aucun album.\nCréez-en depuis la page d'un artiste.") { al ->
                CardItem(al.cover, al.name, artists.find { it.id == al.artistId }?.name ?: "${albumCounts[al.id] ?: 0} titre(s)", false, Icons.Default.Album) {
                    vm.open(Route.AlbumR(al.id))
                }
            }
            else -> LibGrid(sortLib(playlists.filter { it.name.contains(q, true) }, libSort, { it.name }, { it.id }, { plCounts[it.id] ?: 0 }), "Aucune playlist.\nAppuyez sur ＋ pour en créer une.") { p ->
                CardItem(p.cover, p.name, "${plCounts[p.id] ?: 0} titre(s)", false, Icons.Default.QueueMusic) { vm.open(Route.PlaylistR(p.id)) }
            }
        }
    }
    if (newArtist) NameImageDialog("Nouvel artiste", "", null, true, Icons.Default.Person, { n, i -> vm.createArtist(n, i) }) { newArtist = false }
    if (newPlaylist) NameImageDialog("Nouvelle playlist", "", null, false, Icons.Default.QueueMusic, { n, i -> vm.createPlaylist(n, i) }) { newPlaylist = false }
}

@Composable
fun ArtistScreen(vm: LibraryVM, id: Long) {
    val artists by vm.artists.collectAsState()
    val albums by vm.albums.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val cur by vm.hub.currentId.collectAsState()
    val artist = artists.find { it.id == id }
    if (artist == null) { LaunchedEffect(Unit) { vm.back() }; return }
    val mine = remember(tracks, id) { tracks.filter { t -> t.artists.any { it.id == id } } }
    val myAlbums = remember(albums, id) { albums.filter { it.artistId == id } }
    var edit by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf(false) }
    var newAlbum by remember { mutableStateOf(false) }
    var pickForAlbum by remember { mutableStateOf<Long?>(null) }
    var pickTracks by remember { mutableStateOf(false) }
    val ds = remember { TrackDialogState() }

    Column(Modifier.fillMaxSize()) {
        ScreenBar(artist.name) { vm.back() }
        SelectionBar(vm, ds, mine)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                CollectionHeader(
                    artist.photo, true, Icons.Default.Person, artist.name, "${mine.size} titre(s) · ${myAlbums.size} album(s)",
                    onEdit = { edit = true }, onDelete = { del = true },
                    onPlay = { vm.hub.playQueue(mine, 0, false) }, onShuffle = { vm.hub.playShuffled(mine) }
                )
            }
            item { SectionRow("Albums") { IconButton(onClick = { newAlbum = true }) { Icon(Icons.Default.Add, "Nouvel album", tint = Violet) } } }
            if (myAlbums.isEmpty()) item { Text("Aucun album", color = TextLo, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            else item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(myAlbums, key = { it.id }) { al ->
                        Column(Modifier.width(120.dp).clickable { vm.open(Route.AlbumR(al.id)) }) {
                            Cover(al.cover, Modifier.size(120.dp), RoundedCornerShape(14.dp), Icons.Default.Album)
                            Text(al.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            item { SectionRow("Titres") { IconButton(onClick = { pickTracks = true }) { Icon(Icons.Default.Add, "Ajouter des titres", tint = Violet) } } }
            if (mine.isEmpty()) item { Text("Aucun titre. Utilisez ＋ pour en attribuer à cet artiste.", color = TextLo, modifier = Modifier.padding(16.dp)) }
            trackItems(vm, mine, cur, ds) { t, dismiss ->
                MenuItem("Retirer de cet artiste", Icons.Default.PersonRemove) { dismiss(); vm.removeArtistFrom(t.id, id) }
            }
        }
    }
    if (edit) NameImageDialog("Modifier l'artiste", artist.name, artist.photo, true, Icons.Default.Person, { n, i -> vm.updateArtist(artist.copy(name = n, photo = i)) }) { edit = false }
    if (del) ConfirmDialog("Supprimer l'artiste « ${artist.name} » ? Ses titres restent dans l'application.", { vm.deleteArtist(id); del = false }) { del = false }
    if (newAlbum) NameImageDialog("Nouvel album", "", null, false, Icons.Default.Album, { n, i ->
        vm.createAlbum(n, id, i) { aid -> pickForAlbum = aid }
    }) { newAlbum = false }
    pickForAlbum?.let { aid ->
        PickTracksDialog(vm, "Titres de l'album", emptySet(), { vm.setAlbum(it, aid) }) { pickForAlbum = null }
    }
    if (pickTracks) PickTracksDialog(vm, "Ajouter à ${artist.name}", mine.map { it.id }.toSet(), { vm.addTracksToArtist(id, it) }) { pickTracks = false }
    TrackDialogs(vm, ds)
}

@Composable
fun AlbumScreen(vm: LibraryVM, id: Long) {
    val albums by vm.albums.collectAsState()
    val artists by vm.artists.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val cur by vm.hub.currentId.collectAsState()
    val album = albums.find { it.id == id }
    if (album == null) { LaunchedEffect(Unit) { vm.back() }; return }
    val mine = remember(tracks, id) { tracks.filter { it.track.albumId == id } }
    var edit by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    val ds = remember { TrackDialogState() }
    val artistName = artists.find { it.id == album.artistId }?.name

    Column(Modifier.fillMaxSize()) {
        ScreenBar(album.name) { vm.back() }
        SelectionBar(vm, ds, mine)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                CollectionHeader(
                    album.cover, false, Icons.Default.Album, album.name,
                    listOfNotNull(artistName, "${mine.size} titre(s)").joinToString(" · "),
                    onEdit = { edit = true }, onDelete = { del = true },
                    onPlay = { vm.hub.playQueue(mine, 0, false) }, onShuffle = { vm.hub.playShuffled(mine) }
                )
            }
            item { SectionRow("Titres") { IconButton(onClick = { pick = true }) { Icon(Icons.Default.Add, "Ajouter des titres", tint = Violet) } } }
            if (mine.isEmpty()) item { Text("Album vide. Utilisez ＋ pour ajouter des titres.", color = TextLo, modifier = Modifier.padding(16.dp)) }
            trackItems(vm, mine, cur, ds) { t, dismiss ->
                MenuItem("Retirer de l'album", Icons.Default.RemoveCircleOutline) { dismiss(); vm.setAlbum(listOf(t.id), null) }
            }
        }
    }
    if (edit) NameImageDialog("Modifier l'album", album.name, album.cover, false, Icons.Default.Album, { n, i -> vm.updateAlbum(album.copy(name = n, cover = i)) }) { edit = false }
    if (del) ConfirmDialog("Supprimer l'album « ${album.name} » ? Ses titres restent dans l'application.", { vm.deleteAlbum(id); del = false }) { del = false }
    if (pick) PickTracksDialog(vm, "Ajouter à l'album", mine.map { it.id }.toSet(), { vm.setAlbum(it, id) }) { pick = false }
    TrackDialogs(vm, ds)
}

@Composable
fun PlaylistScreen(vm: LibraryVM, id: Long) {
    val playlists by vm.playlists.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val refs by vm.playlistRefs.collectAsState()
    val cur by vm.hub.currentId.collectAsState()
    val pl = playlists.find { it.id == id }
    if (pl == null) { LaunchedEffect(Unit) { vm.back() }; return }
    val mine = remember(tracks, refs, id) {
        val map = tracks.associateBy { it.id }
        refs.filter { it.playlistId == id }.sortedBy { it.position }.mapNotNull { map[it.trackId] }
    }
    var edit by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    val ds = remember { TrackDialogState() }

    Column(Modifier.fillMaxSize()) {
        ScreenBar(pl.name) { vm.back() }
        SelectionBar(vm, ds, mine)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                CollectionHeader(
                    pl.cover ?: mine.firstOrNull { it.cover != null }?.cover, false, Icons.Default.QueueMusic, pl.name, "${mine.size} titre(s)",
                    onEdit = { edit = true }, onDelete = { del = true },
                    onPlay = { vm.hub.playQueue(mine, 0, false) }, onShuffle = { vm.hub.playShuffled(mine) }
                )
            }
            item { SectionRow("Titres") { IconButton(onClick = { pick = true }) { Icon(Icons.Default.Add, "Ajouter des titres", tint = Violet) } } }
            if (mine.isEmpty()) item { Text("Playlist vide. Utilisez ＋ pour ajouter des titres.", color = TextLo, modifier = Modifier.padding(16.dp)) }
            trackItems(vm, mine, cur, ds) { t, dismiss ->
                MenuItem("Retirer de la playlist", Icons.Default.RemoveCircleOutline) { dismiss(); vm.removeFromPlaylist(id, t.id) }
            }
        }
    }
    if (edit) NameImageDialog("Modifier la playlist", pl.name, pl.cover, false, Icons.Default.QueueMusic, { n, i -> vm.updatePlaylist(pl.copy(name = n, cover = i)) }) { edit = false }
    if (del) ConfirmDialog("Supprimer la playlist « ${pl.name} » ? Les titres restent dans l'application.", { vm.deletePlaylist(id); del = false }) { del = false }
    if (pick) PickTracksDialog(vm, "Ajouter à la playlist", mine.map { it.id }.toSet(), { vm.addToPlaylist(id, it) }) { pick = false }
    TrackDialogs(vm, ds)
}

@Composable
fun ProfileScreen(vm: LibraryVM) {
    val prefs = vm.prefs
    val photo by prefs.photo.collectAsState()
    val auto by prefs.autoArtists.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val artists by vm.artists.collectAsState()
    val albums by vm.albums.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val stats by vm.stats.collectAsState()
    val days by vm.days.collectAsState()
    var period by remember { mutableIntStateOf(3) }
    val summary = remember(stats, days, tracks, period) { buildStats(statRows(period, stats, days), tracks) }
    var name by remember { mutableStateOf(prefs.name.value) }
    var bio by remember { mutableStateOf(prefs.bio.value) }
    val pick = rememberImagePicker { prefs.setPhoto(it) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { if (it != null) vm.exportBackup(it) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) vm.importBackup(it) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Profil", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(16.dp))
        Cover(photo, Modifier.size(130.dp).clickable { pick() }, CircleShape, Icons.Default.Person)
        TextButton(onClick = pick) { Text("Changer la photo") }
        OutlinedTextField(
            name, { name = it; prefs.setName(it) }, label = { Text("Nom d'utilisateur") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), colors = neonFieldColors()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            bio, { bio = it; prefs.setBio(it) }, label = { Text("Biographie") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), colors = neonFieldColors()
        )
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().neonBorder().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Stat(tracks.size, "Titres"); Stat(artists.size, "Artistes"); Stat(albums.size, "Albums"); Stat(playlists.size, "Playlists")
        }
        Spacer(Modifier.height(16.dp))
        Text("Statistiques d'écoute", color = Blue, modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Semaine", "Mois", "Année", "Tout").forEachIndexed { i, l -> NeonChip(period == i, l) { period = i } }
        }
        Spacer(Modifier.height(8.dp))
        StatsCard(summary)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Créer les artistes depuis les tags")
                Text("À l'import, utilise l'artiste intégré au fichier.", color = TextLo, style = MaterialTheme.typography.bodySmall)
            }
            Switch(auto, { prefs.setAutoArtists(it) }, colors = SwitchDefaults.colors(checkedTrackColor = Violet))
        }
        Spacer(Modifier.height(16.dp))
        Text("Sauvegarde de la bibliothèque", color = Blue, modifier = Modifier.align(Alignment.Start))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonButton("Exporter", { export.launch("neon-sauvegarde.zip") }, Modifier.weight(1f), Icons.Default.Upload)
            NeonButton("Importer", { import.launch(arrayOf("*/*")) }, Modifier.weight(1f), Icons.Default.Download)
        }
        Text(
            "La sauvegarde contient vos réglages, artistes, albums, playlists et images. Les fichiers audio ne sont pas copiés.",
            color = TextLo, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun Stat(n: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$n", style = MaterialTheme.typography.titleLarge, color = Violet, fontWeight = FontWeight.Bold)
        Text(label, color = TextLo, style = MaterialTheme.typography.bodySmall)
    }
}
