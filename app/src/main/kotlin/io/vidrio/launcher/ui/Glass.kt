package io.vidrio.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

/**
 * El "Liquid Glass": desenfoca y refracta lo que hay detrás (el fondo, vía [backdrop]).
 * [blurRadius] alto para paneles grandes donde hay que leer texto, bajo para el dock y los botones.
 */
fun Modifier.glass(
    backdrop: Backdrop,
    shape: Shape,
    tint: Color = Palette.glassTint,
    blurRadius: Dp = 4.dp,
    refraction: Dp = 16.dp,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(blurRadius.toPx())
        lens(refraction.toPx(), refraction.toPx() * 2f)
    },
    onDrawSurface = { drawRect(tint) },
)

/** Se encoge un poco al pulsar, como en iOS. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.92f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
fun GlassButton(
    text: String,
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Palette.glassTint,
    textColor: Color = Color.White,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction)
            .glass(backdrop, Capsule(), tint = tint)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .height(48.dp)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = Type.headline.copy(color = textColor))
    }
}

/** Una fila de un panel (ajustes, menús): texto a la izquierda, lo que sea a la derecha. */
@Composable
fun PanelRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text, Modifier.weight(1f), style = Type.body.copy(color = color))
        trailing()
    }
}
