package io.vidrio.launcher.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.vidrio.launcher.R

/** Inter (OFL), tomada de Free My TV: se parece a SF Pro sin usar la fuente de Apple. */
val Inter = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
)

private val textShadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1f), 6f)

object Type {
    val iconLabel = TextStyle(Color.White, 12.sp, FontWeight.Medium, fontFamily = Inter, shadow = textShadow)
    val title = TextStyle(Color.White, 28.sp, FontWeight.Bold, fontFamily = Inter, shadow = textShadow)
    val headline = TextStyle(Color.White, 17.sp, FontWeight.SemiBold, fontFamily = Inter)
    val body = TextStyle(Color.White, 15.sp, FontWeight.Normal, fontFamily = Inter)
    val caption = TextStyle(Color.White.copy(alpha = 0.7f), 13.sp, FontWeight.Normal, fontFamily = Inter)
}

object Palette {
    val accent = Color(0xFF0A84FF)
    val danger = Color(0xFFFF453A)
    val glassTint = Color.White.copy(alpha = 0.10f)
    val glassTintStrong = Color(0xFF1C1C1E).copy(alpha = 0.55f)
}
