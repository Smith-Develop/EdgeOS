package io.vidrio.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import io.vidrio.launcher.AppEntry
import io.vidrio.launcher.AppsModel

const val COLUMNS = 4
private val CELL_HEIGHT = 100.dp

/** Cuántas filas caben: en el Edge 70 (6,7") salen 6, como en un iPhone Pro Max. */
fun rowsFor(heightDp: Float) = (heightDp / CELL_HEIGHT.value).toInt().coerceIn(4, 7)

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    dock: List<AppEntry>,
    backdrop: Backdrop,
    /** Sube cada vez que se pulsa Inicio estando ya en el inicio: vuelve a la primera página. */
    homeEvents: Int,
    onLaunch: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    var rows by remember { mutableIntStateOf(6) }
    val pages = remember(apps, rows) { apps.chunked(rows * COLUMNS).ifEmpty { listOf(emptyList()) } }
    val pagerState = rememberPagerState { pages.size }
    LaunchedEffect(homeEvents) { if (homeEvents > 0) pagerState.animateScrollToPage(0) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // Mantener pulsado el fondo: ajustes del launcher (en iOS, el modo edición).
                .pointerInput(Unit) { detectTapGestures(onLongPress = { onSettings() }) }
        ) {
            val fit = rowsFor(maxHeight.value - 32f)
            SideEffect { rows = fit }
            HorizontalPager(pagerState, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                AppGrid(pages.getOrElse(page) { emptyList() }, rows, onLaunch, onAppMenu)
            }
        }

        PageDots(pagerState.pageCount, pagerState.currentPage, Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(10.dp))
        SearchPill(backdrop, onSearch, Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(14.dp))
        Dock(dock, backdrop, onLaunch, onAppMenu)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AppGrid(
    apps: List<AppEntry>,
    rows: Int,
    onLaunch: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        for (row in apps.chunked(COLUMNS).take(rows)) {
            Row(Modifier.fillMaxWidth().height(CELL_HEIGHT)) {
                for (app in row) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        AppIcon(app, onLaunch, onAppMenu, showLabel = true)
                    }
                }
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppEntry,
    onLaunch: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .width(80.dp)
            .pressScale(interaction, pressed = 0.88f)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = app.label,
                onLongClick = { onAppMenu(app) },
                onClick = { onLaunch(app) },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(app.icon, app.label, Modifier.size(AppsModel.ICON_DP.dp))
        if (showLabel) {
            BasicText(
                app.label,
                Modifier.padding(top = 6.dp),
                style = Type.iconLabel.copy(textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    if (count <= 1) return
    Row(modifier.height(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(7.dp)
                    .background(Color.White.copy(alpha = if (i == current) 1f else 0.35f), CircleShape)
            )
        }
    }
}

/** El botón "Buscar" que iOS 26 pone sobre el dock. */
@Composable
private fun SearchPill(backdrop: Backdrop, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(interaction)
            .glass(backdrop, Capsule(), blurRadius = 6.dp, refraction = 10.dp)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .height(34.dp)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchGlyph(Modifier.size(14.dp))
        BasicText("Buscar", style = Type.body.copy(fontSize = Type.caption.fontSize))
    }
}

@Composable
fun SearchGlyph(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.14f
        val r = size.minDimension * 0.34f
        val c = Offset(r + stroke, r + stroke)
        drawCircle(color, r, c, style = Stroke(stroke))
        val d = r * 0.72f
        drawLine(color, Offset(c.x + d, c.y + d), Offset(size.width - stroke / 2, size.height - stroke / 2), stroke)
    }
}

@Composable
private fun Dock(
    apps: List<AppEntry>,
    backdrop: Backdrop,
    onLaunch: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
) {
    Row(
        Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
            .height(92.dp)
            .glass(backdrop, RoundedRectangle(34.dp), blurRadius = 3.dp, refraction = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (apps.isEmpty()) {
            BasicText(
                "Mantén pulsada una app para añadirla aquí",
                Modifier.padding(horizontal = 16.dp),
                style = Type.caption,
            )
        }
        for (app in apps) AppIcon(app, onLaunch, onAppMenu, showLabel = false)
    }
}
