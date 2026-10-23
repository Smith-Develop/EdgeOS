package io.vidrio.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import io.vidrio.launcher.AppEntry
import java.text.Normalizer

/** Fondo oscurecido que cierra el panel al tocarlo. */
@Composable
private fun Scrim(onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        content = content,
    )
}

/** Un panel de vidrio esmerilado: mucho desenfoque para que el texto se lea sobre cualquier fondo. */
private fun Modifier.panel(backdrop: Backdrop) =
    glass(backdrop, RoundedRectangle(28.dp), tint = Palette.glassTintStrong, blurRadius = 24.dp, refraction = 12.dp)

/** Quita tildes y mayúsculas: "camara" encuentra "Cámara". */
private fun fold(text: String) =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

@Composable
fun SearchOverlay(
    apps: List<AppEntry>,
    backdrop: Backdrop,
    onLaunch: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val results = remember(apps, query) {
        val q = fold(query.trim())
        if (q.isEmpty()) apps.take(8) else apps.filter { fold(it.label).contains(q) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .glass(backdrop, RectangleShape, tint = Color.Black.copy(alpha = 0.3f), blurRadius = 28.dp, refraction = 0.dp)
            .clickable(interactionSource = null, indication = null, onClick = onDismiss)
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .glass(backdrop, Capsule(), tint = Palette.glassTint, blurRadius = 8.dp, refraction = 8.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchGlyph(Modifier.size(16.dp), Color.White.copy(alpha = 0.7f))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) BasicText("Buscar apps", style = Type.body.copy(color = Color.White.copy(alpha = 0.5f)))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        textStyle = Type.body,
                        singleLine = true,
                        cursorBrush = SolidColor(Palette.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let(onLaunch) }),
                    )
                }
            }
            BasicText(
                if (query.isBlank()) "Apps" else "${results.size} resultados",
                Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                style = Type.caption,
            )
            LazyVerticalGrid(
                GridCells.Fixed(COLUMNS),
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(results, key = { it.key }) { app ->
                    Box(contentAlignment = Alignment.TopCenter) {
                        AppIcon(app, onLaunch, onAppMenu, showLabel = true)
                    }
                }
            }
        }
    }
}

/** Mantener pulsada una app: dock, información y desinstalar. */
@Composable
fun AppMenu(
    app: AppEntry,
    inDock: Boolean,
    dockFull: Boolean,
    backdrop: Backdrop,
    onToggleDock: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    Scrim(onDismiss) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(32.dp)
                .fillMaxWidth()
                .panel(backdrop)
                .clickable(interactionSource = null, indication = null) {} // que tocar el panel no lo cierre
                .padding(vertical = 8.dp)
        ) {
            Row(
                Modifier.padding(20.dp, 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(app.icon, null, Modifier.size(44.dp))
                BasicText(app.label, style = Type.headline, maxLines = 2)
            }
            Divider()
            when {
                inDock -> PanelRow("Quitar del dock", onToggleDock)
                dockFull -> PanelRow("Dock lleno (máx. 4)", {}, color = Color.White.copy(alpha = 0.4f))
                else -> PanelRow("Añadir al dock", onToggleDock)
            }
            PanelRow("Información de la app", onInfo)
            if (!app.isSystem) PanelRow("Desinstalar", onUninstall, color = Palette.danger)
        }
    }
}

/** Mantener pulsado el fondo. */
@Composable
fun SettingsSheet(
    backdrop: Backdrop,
    isDefaultHome: Boolean,
    hasCustomWallpaper: Boolean,
    onPickWallpaper: () -> Unit,
    onResetWallpaper: () -> Unit,
    onDebloat: () -> Unit,
    onMakeDefault: () -> Unit,
    onSystemSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Scrim(onDismiss) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp)
                .fillMaxWidth()
                .panel(backdrop)
                .clickable(interactionSource = null, indication = null) {}
                .padding(vertical = 8.dp)
        ) {
            BasicText("Vidrio", Modifier.padding(20.dp, 12.dp), style = Type.headline)
            Divider()
            PanelRow("Cambiar fondo de pantalla", onPickWallpaper)
            if (hasCustomWallpaper) PanelRow("Fondo original de Vidrio", onResetWallpaper)
            PanelRow("Limpieza de apps de fábrica", onDebloat) {
                BasicText("Shizuku", style = Type.caption)
            }
            if (!isDefaultHome) PanelRow("Usar Vidrio como inicio", onMakeDefault, color = Palette.accent)
            PanelRow("Ajustes del teléfono", onSystemSettings)
        }
    }
}

@Composable
fun Divider() {
    Box(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Color.White.copy(alpha = 0.15f))
    )
}
