package io.edgeos.car.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.edgeos.car.maps.MapEntry
import io.edgeos.car.maps.Maps
import java.util.Locale

/** Diálogos para conseguir mapas sin conexión: el de Madrid de un toque, o cualquiera recorriendo el catálogo. */
object MapsUi {
    private val main = Handler(Looper.getMainLooper())

    private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    /** Busca el mapa de la Comunidad de Madrid (o el de España, si el servidor no lo tiene aparte) y ofrece bajarlo. */
    fun offerMadrid(context: Context) {
        toast(context, "Buscando el mapa de la Comunidad de Madrid…")
        Thread {
            val entry = runCatching { Maps.madrid() }.getOrNull()
            main.post {
                if (entry == null) toast(context, "No pude consultar el catálogo de mapas. ¿Hay internet?")
                else confirm(context, entry, if (entry.path.contains("madrid", true)) null
                    else "El servidor no tiene la Comunidad de Madrid por separado: este mapa es España entera.")
            }
        }.start()
    }

    /** Recorre el catálogo por carpetas (continente → país → región). */
    fun browse(context: Context, path: String = "") {
        Thread {
            val list = runCatching { Maps.catalog(path) }
            main.post {
                val entries = list.getOrNull()
                if (entries == null) return@post toast(context, "No pude consultar el catálogo de mapas. ¿Hay internet?")
                val items = entries.map { if (it.folder) "${it.name}  ›" else "${it.name}   ${it.size}" }
                Ui.dialog(context)
                    .setTitle(if (path.isEmpty()) "Mapas sin conexión" else path.trimEnd('/').substringAfterLast('/').replaceFirstChar { it.uppercase() })
                    .setItems(items.toTypedArray()) { _, i ->
                        val e = entries[i]
                        if (e.folder) browse(context, e.path) else confirm(context, e, null)
                    }
                    .apply { if (path.isNotEmpty()) setNeutralButton("‹ Atrás") { _, _ -> browse(context, path.trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }) } }
                    .setNegativeButton("Cerrar", null)
                    .show()
            }
        }.start()
    }

    fun confirm(context: Context, entry: MapEntry, note: String?) {
        val free = Maps.freeBytes(context)
        val need = Maps.bytes(entry.size)
        val freeText = String.format(Locale.forLanguageTag("es"), "%.1f GB", free / 1e9)
        if (need > 0 && need > free - 200_000_000) {
            return Ui.dialog(context).setTitle("No cabe")
                .setMessage("${entry.name} ocupa ${entry.size} y la radio tiene $freeText libres. Libera espacio o elige una región más pequeña.")
                .setPositiveButton("Entendido", null).show().let {}
        }
        Ui.dialog(context)
            .setTitle("Descargar ${entry.name}")
            .setMessage(listOfNotNull(note,
                "Ocupa ${entry.size.ifEmpty { "(tamaño desconocido)" }}; libres: $freeText.",
                "Mejor con Wi-Fi. La descarga sigue aunque cambies de pantalla, y al terminar el mapa funciona sin internet.").joinToString("\n\n"))
            .setPositiveButton("Descargar") { _, _ ->
                Maps.download(context, entry)
                toast(context, "Descargando ${entry.name}. Puedes seguir usando la radio.")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
