package io.edgeos.car.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Todo se diseña sobre una pantalla de 1024×600 "unidades" y se escala a la real: estas radios declaran
 * densidades muy distintas (160, 213, 240 dpi) para el mismo panel, y en dp el diseño no cabría en todas.
 */
object Ui {
    private var k = 1f

    /** Con el tamaño real de la ventana del launcher (no el de la pantalla: puede haber barras del sistema). */
    fun init(width: Int, height: Int) {
        k = min(maxOf(width, height) / 1024f, minOf(width, height) / 600f)
    }

    fun px(v: Float): Int = (v * k).roundToInt()
    fun px(v: Int): Int = px(v.toFloat())
    fun pxf(v: Float): Float = v * k

    // Claro: gris de fondo, tarjetas blancas, texto casi negro. Oscuro: casi negro, tarjetas grafito, texto claro.
    // INK es el fondo de los botones principales y ON_INK lo que va encima.
    var dark = false
        private set
    var BG = 0; private set
    var CARD = 0; private set
    var TILE = 0; private set
    var TEXT = 0; private set
    var TEXT2 = 0; private set
    var LINE = 0; private set
    var INK = 0; private set
    var ON_INK = 0; private set
    val ACCENT = Color.parseColor("#0A84FF")
    val DANGER = Color.parseColor("#FF3B30")
    val OK = Color.parseColor("#34C759")

    init {
        theme(false)
    }

    /** Cambia los colores; las vistas ya creadas no cambian: hay que reconstruir la interfaz. */
    fun theme(dark: Boolean) {
        this.dark = dark
        if (dark) {
            BG = Color.parseColor("#0E0F12"); CARD = Color.parseColor("#1A1C20"); TILE = Color.parseColor("#25282D")
            TEXT = Color.parseColor("#F2F3F5"); TEXT2 = Color.parseColor("#8E9097"); LINE = Color.parseColor("#2C2F35")
            INK = Color.parseColor("#F2F3F5"); ON_INK = Color.parseColor("#111114")
        } else {
            BG = Color.parseColor("#E9EAEE"); CARD = Color.WHITE; TILE = Color.parseColor("#F3F4F6")
            TEXT = Color.parseColor("#111114"); TEXT2 = Color.parseColor("#7A7A80"); LINE = Color.parseColor("#E4E5E9")
            INK = Color.parseColor("#111114"); ON_INK = Color.WHITE
        }
    }

    /** Diálogos del sistema con el tema que toca. */
    fun dialog(context: Context) = android.app.AlertDialog.Builder(
        context,
        if (dark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert,
    )

    lateinit var light: Typeface
    lateinit var regular: Typeface
    lateinit var medium: Typeface
    lateinit var semibold: Typeface

    fun fonts(context: Context) {
        if (::regular.isInitialized) return
        val a = context.assets
        light = Typeface.createFromAsset(a, "fonts/Inter-Light.otf")
        regular = Typeface.createFromAsset(a, "fonts/Inter-Regular.otf")
        medium = Typeface.createFromAsset(a, "fonts/Inter-Medium.otf")
        semibold = Typeface.createFromAsset(a, "fonts/Inter-SemiBold.otf")
    }

    fun text(
        context: Context,
        value: CharSequence = "",
        size: Float,
        color: Int = TEXT,
        face: Typeface = regular,
        gravity: Int = Gravity.START or Gravity.CENTER_VERTICAL,
    ) = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_PX, pxf(size))
        setTextColor(color)
        typeface = face
        this.gravity = gravity
        includeFontPadding = false
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    fun rounded(color: Int, radius: Float, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = pxf(radius)
        if (stroke != 0) setStroke(px(1f).coerceAtLeast(1), stroke)
    }

    /** Fondo con onda al pulsar, recortada a la forma. */
    fun pressable(color: Int, radius: Float, stroke: Int = 0) = RippleDrawable(
        ColorStateList.valueOf(Color.argb(28, 0, 0, 0)),
        rounded(color, radius, stroke),
        rounded(Color.BLACK, radius),
    )

    /** Tarjeta blanca con sombra suave. */
    fun card(view: View, radius: Float = 18f, color: Int = CARD) {
        view.background = rounded(color, radius)
        view.elevation = pxf(3f)
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) =
                outline.setRoundRect(0, 0, v.width, v.height, pxf(radius))
        }
        view.clipToOutline = true
    }

    fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    fun column(context: Context) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun row(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun space(context: Context, w: Int, h: Int) = View(context).apply { layoutParams = LinearLayout.LayoutParams(w, h) }

    const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
}
