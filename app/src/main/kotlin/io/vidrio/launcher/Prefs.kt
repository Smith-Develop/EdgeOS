package io.vidrio.launcher

import android.content.Context
import java.io.File

/** Lo que el launcher recuerda: el dock, el fondo y las apps que desactivó Limpieza. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("vidrio", Context.MODE_PRIVATE)

    val wallpaperFile = File(context.filesDir, "wallpaper.jpg")

    /** Claves de AppEntry, en orden. null: aún no se eligió (se rellena con las apps por defecto). */
    var dock: List<String>?
        get() = sp.getString("dock", null)?.split('\n')?.filter { it.isNotEmpty() }
        set(value) = sp.edit().putString("dock", value?.joinToString("\n")).apply()

    /** Paquetes que desactivó Vidrio: "Restaurar todo" los vuelve a activar. */
    var disabledByUs: Set<String>
        get() = sp.getStringSet("disabled", emptySet())!!.toSet()
        set(value) = sp.edit().putStringSet("disabled", value).apply()

    companion object {
        const val DOCK_SIZE = 4
    }
}
