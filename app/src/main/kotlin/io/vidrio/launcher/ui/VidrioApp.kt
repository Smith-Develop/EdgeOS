package io.vidrio.launcher.ui

import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.vidrio.launcher.AppEntry
import io.vidrio.launcher.AppsModel
import io.vidrio.launcher.Prefs
import io.vidrio.launcher.debloat.Debloater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Screen { Home, Debloat }

@Composable
fun VidrioApp(
    model: AppsModel,
    prefs: Prefs,
    debloater: Debloater,
    homeEvents: Int,
    resumeEvents: Int,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()

    var wallpaper by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) { wallpaper = withContext(Dispatchers.IO) { WallpaperStore.load(prefs.wallpaperFile) } }
    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) { runCatching { WallpaperStore.save(context, uri, prefs.wallpaperFile) }.getOrNull() }
                ?.let { wallpaper = it }
        }
    }

    val apps = model.apps
    var dockKeys by remember { mutableStateOf(prefs.dock) }
    LaunchedEffect(apps) {
        if (dockKeys == null && apps.isNotEmpty()) {
            dockKeys = model.defaultDock()
            prefs.dock = dockKeys
        }
    }
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val dock = remember(byKey, dockKeys) { dockKeys.orEmpty().mapNotNull { byKey[it] } }
    val gridApps = remember(apps, dockKeys) {
        val inDock = dockKeys.orEmpty().toSet()
        apps.filter { it.key !in inDock }
    }
    fun setDock(keys: List<String>) {
        dockKeys = keys
        prefs.dock = keys
    }

    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    var isDefaultHome by remember { mutableStateOf(roleManager.isRoleHeld(RoleManager.ROLE_HOME)) }
    val requestHome = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefaultHome = roleManager.isRoleHeld(RoleManager.ROLE_HOME)
    }
    LaunchedEffect(resumeEvents) { isDefaultHome = roleManager.isRoleHeld(RoleManager.ROLE_HOME) }

    var screen by remember { mutableStateOf(Screen.Home) }
    var searchOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    // Sigue pintando el menú mientras se desvanece, cuando menuApp ya es null.
    var lastMenuApp by remember { mutableStateOf<AppEntry?>(null) }

    // Inicio con el launcher ya delante: cerrar todo, como en iOS.
    LaunchedEffect(homeEvents) {
        searchOpen = false
        settingsOpen = false
        menuApp = null
        screen = Screen.Home
    }
    BackHandler {
        when {
            menuApp != null -> menuApp = null
            searchOpen -> searchOpen = false
            settingsOpen -> settingsOpen = false
            screen != Screen.Home -> screen = Screen.Home
        }
    }

    val launch: (AppEntry) -> Unit = { app ->
        model.launch(app)
        searchOpen = false
    }
    val openMenu: (AppEntry) -> Unit = {
        menuApp = it
        lastMenuApp = it
    }

    Box(Modifier.fillMaxSize()) {
        Wallpaper(wallpaper, Modifier.fillMaxSize().layerBackdrop(backdrop))

        when (screen) {
            Screen.Home -> HomeScreen(
                apps = gridApps,
                dock = dock,
                backdrop = backdrop,
                homeEvents = homeEvents,
                onLaunch = launch,
                onAppMenu = openMenu,
                onSearch = { searchOpen = true },
                onSettings = { settingsOpen = true },
            )
            Screen.Debloat -> DebloatScreen(debloater, backdrop, resumeEvents, onBack = { screen = Screen.Home })
        }

        AnimatedVisibility(searchOpen, enter = fadeIn(), exit = fadeOut()) {
            SearchOverlay(apps, backdrop, launch, openMenu, onDismiss = { searchOpen = false })
        }

        AnimatedVisibility(settingsOpen, enter = fadeIn(), exit = fadeOut()) {
            SettingsSheet(
                backdrop = backdrop,
                isDefaultHome = isDefaultHome,
                hasCustomWallpaper = wallpaper != null,
                onPickWallpaper = {
                    settingsOpen = false
                    pickWallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onResetWallpaper = {
                    settingsOpen = false
                    prefs.wallpaperFile.delete()
                    wallpaper = null
                },
                onDebloat = {
                    settingsOpen = false
                    screen = Screen.Debloat
                },
                onMakeDefault = {
                    settingsOpen = false
                    requestHome.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                },
                onSystemSettings = {
                    settingsOpen = false
                    runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                },
                onDismiss = { settingsOpen = false },
            )
        }

        AnimatedVisibility(menuApp != null, enter = fadeIn() + scaleIn(initialScale = 0.94f), exit = fadeOut() + scaleOut(targetScale = 0.94f)) {
            val app = lastMenuApp ?: return@AnimatedVisibility
            val keys = dockKeys.orEmpty()
            AppMenu(
                app = app,
                inDock = app.key in keys,
                dockFull = keys.size >= Prefs.DOCK_SIZE,
                backdrop = backdrop,
                onToggleDock = {
                    setDock(if (app.key in keys) keys - app.key else keys + app.key)
                    menuApp = null
                },
                onInfo = {
                    model.openInfo(app)
                    menuApp = null
                },
                onUninstall = {
                    model.uninstall(app)
                    menuApp = null
                },
                onDismiss = { menuApp = null },
            )
        }
    }
}
