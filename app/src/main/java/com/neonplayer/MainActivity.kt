package com.neonplayer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        (application as App).hub.connect()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { NeonTheme { AppRoot() } }
    }
}

@Composable
fun AppRoot(vm: LibraryVM = viewModel()) {
    val snack = remember { SnackbarHostState() }
    val msg by vm.message.collectAsState()
    LaunchedEffect(msg) {
        msg?.let {
            snack.showSnackbar(it)
            if (vm.message.value == it) vm.message.value = null
        }
    }
    BackHandler(enabled = vm.stack.isNotEmpty()) { vm.back() }

    val act = LocalContext.current as ComponentActivity
    val light = ThemeState.current.light
    LaunchedEffect(light) {
        val style = if (light) SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT) else SystemBarStyle.dark(AColor.TRANSPARENT)
        act.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    val tabs = listOf("Titres" to Icons.Default.MusicNote, "Bibliothèque" to Icons.Default.LibraryMusic, "Profil" to Icons.Default.Person, "Réglages" to Icons.Default.Settings)

    Scaffold(
        containerColor = NeonBg,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Column {
                MiniPlayer(vm)
                NavigationBar(containerColor = NeonSurface) {
                    tabs.forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(
                            selected = vm.tab == i && vm.stack.isEmpty(),
                            onClick = { vm.tab = i; vm.stack.clear() },
                            icon = { Icon(icon, null) }, label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TextHi, selectedTextColor = Violet,
                                indicatorColor = Violet.copy(alpha = 0.35f), unselectedIconColor = TextLo, unselectedTextColor = TextLo
                            )
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).statusBarsPadding()) {
            when (val r = vm.stack.lastOrNull()) {
                is Route.ArtistR -> ArtistScreen(vm, r.id)
                is Route.AlbumR -> AlbumScreen(vm, r.id)
                is Route.PlaylistR -> PlaylistScreen(vm, r.id)
                null -> when (vm.tab) {
                    0 -> TracksScreen(vm)
                    1 -> LibraryScreen(vm)
                    2 -> ProfileScreen(vm)
                    else -> SettingsScreen(vm)
                }
            }
        }
    }
    if (vm.showPlayer) PlayerScreen(vm)
}
