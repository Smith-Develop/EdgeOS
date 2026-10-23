package io.vidrio.launcher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import io.vidrio.launcher.debloat.Candidate
import io.vidrio.launcher.debloat.Debloater
import io.vidrio.launcher.debloat.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private sealed interface ListState {
    data object Loading : ListState
    data class Loaded(val items: List<Candidate>) : ListState
    data class Failed(val message: String) : ListState
}

@Composable
fun DebloatScreen(
    debloater: Debloater,
    backdrop: Backdrop,
    /** Sube en cada onResume: el estado de Shizuku cambia mientras estamos en su app. */
    resumeEvents: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(Shell.status(context)) }

    DisposableEffect(Unit) {
        val main = Handler(Looper.getMainLooper())
        val received = Shizuku.OnBinderReceivedListener { status = Shell.status(context) }
        val dead = Shizuku.OnBinderDeadListener { status = Shell.status(context) }
        val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> status = Shell.status(context) }
        Shizuku.addBinderReceivedListenerSticky(received, main)
        Shizuku.addBinderDeadListener(dead, main)
        Shizuku.addRequestPermissionResultListener(permission, main)
        onDispose {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(permission)
        }
    }
    LaunchedEffect(resumeEvents) { status = Shell.status(context) }

    var includeAdvanced by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var list by remember { mutableStateOf<ListState>(ListState.Loading) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(status, includeAdvanced, reload) {
        if (status != Shell.Status.Ready) return@LaunchedEffect
        list = ListState.Loading
        list = try {
            ListState.Loaded(withContext(Dispatchers.IO) { debloater.candidates(includeAdvanced) })
        } catch (e: Exception) {
            ListState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    fun work(action: () -> String) {
        busy = true
        scope.launch {
            notice = withContext(Dispatchers.IO) { runCatching(action).getOrElse { "Error: ${it.message}" } }
            selected = emptySet()
            busy = false
            reload++
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
    ) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "‹ Inicio",
                Modifier.clickable(onClick = onBack).padding(8.dp),
                style = Type.body.copy(color = Palette.accent),
            )
        }
        BasicText("Limpieza", Modifier.padding(horizontal = 8.dp), style = Type.title)
        BasicText(
            "Desactiva apps de fábrica sin borrarlas. \"Restaurar todo\" las devuelve tal como estaban.",
            Modifier.padding(8.dp, 4.dp, 8.dp, 12.dp),
            style = Type.caption,
        )

        if (status != Shell.Status.Ready) {
            ShizukuCard(status, backdrop, context)
            return@Column
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .glass(backdrop, RoundedRectangle(24.dp), tint = Palette.glassTintStrong, blurRadius = 24.dp, refraction = 8.dp)
        ) {
            PanelRow("Mostrar también las \"avanzadas\"", { includeAdvanced = !includeAdvanced }) {
                Check(includeAdvanced)
            }
            Divider()
            when (val state = list) {
                ListState.Loading -> BasicText("Buscando apps…", Modifier.padding(20.dp), style = Type.caption)
                is ListState.Failed -> BasicText(
                    "No se pudo cargar la lista de UAD-ng (¿sin internet?): ${state.message}",
                    Modifier.padding(20.dp),
                    style = Type.caption,
                )
                is ListState.Loaded -> {
                    if (state.items.isEmpty()) {
                        BasicText("Nada que limpiar en este teléfono.", Modifier.padding(20.dp), style = Type.caption)
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.items, key = { it.packageName }) { item ->
                            CandidateRow(
                                item,
                                checked = item.packageName in selected,
                                onToggle = {
                                    selected = if (item.packageName in selected) selected - item.packageName
                                        else selected + item.packageName
                                },
                                onEnable = { work { if (debloater.enable(item.packageName)) "Activada: ${item.name}" else "No se pudo activar ${item.name}" } },
                            )
                        }
                    }
                }
            }
        }

        notice?.let { BasicText(it, Modifier.padding(8.dp), style = Type.caption) }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassButton(
                "Restaurar todo",
                backdrop,
                onClick = {
                    if (!busy) work {
                        val failed = debloater.restoreAll()
                        if (failed.isEmpty()) "Todo restaurado." else "No se pudieron activar: ${failed.joinToString()}"
                    }
                },
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                if (busy) "Trabajando…" else "Desactivar (${selected.size})",
                backdrop,
                onClick = {
                    if (!busy && selected.isNotEmpty()) {
                        val chosen = selected.toList()
                        work {
                            val failed = chosen.filterNot { debloater.disable(it) }
                            if (failed.isEmpty()) "Desactivadas: ${chosen.size}." else "No se pudieron desactivar: ${failed.joinToString()}"
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                tint = if (selected.isEmpty()) Palette.glassTint else Palette.danger.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun CandidateRow(item: Candidate, checked: Boolean, onToggle: () -> Unit, onEnable: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = item.enabled, onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(
                item.name,
                style = Type.body.copy(color = if (item.enabled) Color.White else Color.White.copy(alpha = 0.5f)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                buildString {
                    append(item.packageName)
                    if (item.removal == "Advanced") append(" · avanzada")
                    if (!item.enabled) append(" · desactivada")
                },
                style = Type.caption.copy(fontSize = Type.caption.fontSize * 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.description.isNotEmpty()) {
                BasicText(item.description, style = Type.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (item.enabled) {
            Check(checked)
        } else {
            BasicText("Activar", Modifier.clickable(onClick = onEnable).padding(8.dp), style = Type.body.copy(color = Palette.accent))
        }
    }
}

@Composable
private fun Check(on: Boolean) {
    Box(
        Modifier
            .size(24.dp)
            .then(
                if (on) Modifier.background(Palette.accent, CircleShape)
                else Modifier.border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (on) BasicText("✓", style = Type.caption.copy(color = Color.White))
    }
}

@Composable
private fun ShizukuCard(status: Shell.Status, backdrop: Backdrop, context: Context) {
    val (text, button, action): Triple<String, String, () -> Unit> = when (status) {
        Shell.Status.NotInstalled -> Triple(
            "Para desactivar apps sin PC ni root, Vidrio usa Shizuku. Instálalo primero (es gratis y de código abierto).",
            "Instalar Shizuku",
            {
                val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${Shell.SHIZUKU_PACKAGE}"))
                val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
                runCatching { context.startActivity(market) }.onFailure { runCatching { context.startActivity(web) } }
                Unit
            },
        )
        Shell.Status.NotRunning -> Triple(
            "Abre Shizuku y pulsa \"Iniciar\" en \"Iniciar con depuración inalámbrica\" (necesitas las Opciones de " +
                "desarrollador y estar en una red Wi-Fi). Tras reiniciar el teléfono hay que volver a iniciarlo.",
            "Abrir Shizuku",
            {
                context.packageManager.getLaunchIntentForPackage(Shell.SHIZUKU_PACKAGE)
                    ?.let { runCatching { context.startActivity(it) } }
                Unit
            },
        )
        else -> Triple(
            "Shizuku está en marcha. Falta que le permitas a Vidrio usarlo.",
            "Dar permiso",
            { Shizuku.requestPermission(Shell.PERMISSION_REQUEST) },
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .glass(backdrop, RoundedRectangle(24.dp), tint = Palette.glassTintStrong, blurRadius = 24.dp, refraction = 8.dp)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BasicText(text, style = Type.body)
        GlassButton(button, backdrop, onClick = action, tint = Palette.accent.copy(alpha = 0.8f))
    }
}
