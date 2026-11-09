package io.edgeos.car.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import io.edgeos.car.drive.DriveState
import io.edgeos.car.ui.Ui.MATCH
import io.edgeos.car.ui.Ui.WRAP
import io.edgeos.car.ui.Ui.lp
import io.edgeos.car.ui.Ui.px
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.io.File

/** La flecha de posición, girada según el rumbo. */
private class PositionOverlay : Overlay() {
    var point: GeoPoint? = null
    var bearing = 0f
    private val pt = Point()
    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.INK }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 17, 17, 20) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    override fun draw(c: Canvas, map: MapView, shadow: Boolean) {
        if (shadow) return
        val p = point ?: return
        map.projection.toPixels(p, pt)
        val s = Ui.pxf(1f)
        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 30 * s, halo)
        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 15 * s, ring)
        c.save()
        c.rotate(bearing, pt.x.toFloat(), pt.y.toFloat())
        path.reset()
        path.moveTo(pt.x.toFloat(), pt.y - 11 * s)
        path.lineTo(pt.x + 8 * s, pt.y + 9 * s)
        path.lineTo(pt.x.toFloat(), pt.y + 5 * s)
        path.lineTo(pt.x - 8 * s, pt.y + 9 * s)
        path.close()
        c.drawPath(path, fill)
        c.restore()
    }
}

/**
 * Mapa de OpenStreetMap en gris claro, siguiendo la posición. Necesita internet para las zonas nuevas; las
 * ya vistas quedan en caché (hasta 300 MB) y se ven sin conexión.
 */
@SuppressLint("ClickableViewAccessibility")
class MapCard(context: Context, onNavigate: () -> Unit) : FrameLayout(context) {
    private val map: MapView
    private val position = PositionOverlay()
    private var following = true
    private val recenter: View
    private val distance = Stat(context, "Distancia", 18f)
    private val time = Stat(context, "En marcha", 18f)
    private val avg = Stat(context, "Media", 18f)
    private val max = Stat(context, "Máxima", 18f)

    init {
        Ui.card(this)
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.filesDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "tiles")
            tileFileSystemCacheMaxBytes = 300L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 250L * 1024 * 1024
        }
        map = MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 4.0
            maxZoomLevel = 19.0
            controller.setZoom(16.5)
            // Gris claro: casi sin color y algo más luminoso, como un mapa de salpicadero.
            val m = ColorMatrix().apply { setSaturation(0.12f) }
            m.postConcat(ColorMatrix(floatArrayOf(1.05f, 0f, 0f, 0f, 8f, 0f, 1.05f, 0f, 0f, 8f, 0f, 0f, 1.05f, 0f, 10f, 0f, 0f, 0f, 1f, 0f)))
            overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(m))
            overlays.add(position)
            setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_MOVE && following) {
                    following = false
                    recenter.visibility = View.VISIBLE
                }
                false
            }
        }
        addView(map, LayoutParams(MATCH, MATCH))

        // "Navegar": abre Google Maps, Waze u otra app de rutas.
        val nav = Ui.row(context)
        nav.background = Ui.pressable(Ui.INK, 14f)
        nav.setPadding(px(14), px(10), px(16), px(10))
        val navIcon = View(context)
        navIcon.background = GlyphDrawable(Glyph.NAV, Color.WHITE)
        nav.addView(navIcon, lp(px(20), px(20)))
        nav.addView(Ui.text(context, "Navegar", 15f, Color.WHITE, Ui.semibold), lp(WRAP, WRAP).apply { marginStart = px(10) })
        nav.setOnClickListener { onNavigate() }
        addView(nav, LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply { setMargins(px(14), px(14), 0, 0) })

        // Zoom y recentrar, a la derecha.
        val side = Ui.column(context)
        side.background = Ui.rounded(Ui.CARD, 14f)
        side.elevation = Ui.pxf(2f)
        side.addView(button(context, GlyphDrawable(Glyph.PLUS, Ui.TEXT), 0) { map.controller.zoomIn() }, lp(px(48), px(48)))
        side.addView(button(context, GlyphDrawable(Glyph.MINUS, Ui.TEXT), 0) { map.controller.zoomOut() }, lp(px(48), px(48)))
        addView(side, LayoutParams(WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = px(14) })
        recenter = button(context, GlyphDrawable(Glyph.LOCATE, Ui.TEXT), Ui.CARD) {
            following = true
            recenterNow()
        }
        recenter.elevation = Ui.pxf(2f)
        recenter.visibility = View.GONE
        addView(recenter, LayoutParams(px(48), px(48), Gravity.TOP or Gravity.END).apply { setMargins(0, px(14), px(14), 0) })

        // El viaje, en una franja blanca abajo.
        val trip = Ui.row(context)
        trip.background = Ui.rounded(Ui.CARD, 16f)
        trip.elevation = Ui.pxf(2f)
        trip.setPadding(px(16), px(10), px(16), px(10))
        for (s in listOf(distance, time, avg, max)) trip.addView(s, lp(0, WRAP, 1f))
        addView(trip, LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { setMargins(px(14), 0, px(14), px(26)) })

        // Atribución obligatoria de OpenStreetMap.
        val credit = Ui.text(context, "© OpenStreetMap", 10f, Ui.TEXT2)
        addView(credit, LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, px(16), px(8)) })
    }

    private fun recenterNow() {
        position.point?.let { map.controller.animateTo(it) }
        recenter.visibility = View.GONE
    }

    fun update(s: DriveState) {
        s.location?.let { loc ->
            val p = GeoPoint(loc.latitude, loc.longitude)
            val first = position.point == null
            position.point = p
            position.bearing = s.bearing ?: 0f
            if (first) map.controller.setCenter(p) else if (following) map.controller.animateTo(p)
            map.invalidate()
        }
        distance.set("${fmtKm(s.tripKm)} km")
        time.set(fmtTime(s.tripSeconds))
        avg.set("${s.avgKmh} km/h")
        max.set("${s.maxKmh} km/h")
    }

    fun onResume() = map.onResume()
    fun onPause() = map.onPause()
}
