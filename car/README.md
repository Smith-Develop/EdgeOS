# EdgeOS Car

Un launcher para **radios Android de coche**, limpio y claro, al estilo de los salpicaderos de gama alta: el coche
visto desde arriba con la velocidad, el mapa o un panel de conducción, la música y los accesos rápidos. Incluye
**Sistema**, una pantalla para optimizar la radio y desactivar servicios que no hacen falta, sin root y con
vuelta atrás.

Pensado para la **k80_bsp** (MediaTek MT6580, Android 8.1, 1024×600) y radios parecidas. Funciona desde Android 8.0.

> [!NOTE]
> **Versión 0.1, compilada pero sin probar en una radio.** El APK se genera sin errores y la interfaz se ha
> revisado en el ordenador (las capturas de abajo), pero todavía no ha funcionado en ningún aparato real.

<table>
  <tr>
    <td align="center"><img src="docs/home.png" width="420" alt="Inicio: el coche con la velocidad, el panel de conducción, la música y los accesos rápidos"><br><b>Inicio</b> (modo Panel)</td>
    <td align="center"><img src="docs/system.png" width="420" alt="Sistema: estado, optimización rápida, ajustes y servicios innecesarios"><br><b>Sistema</b></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/apps.png" width="420" alt="Todas las apps en una cuadrícula"><br><b>Apps</b></td>
    <td></td>
  </tr>
</table>

<sub>Capturas de la interfaz real, dibujada con el motor de Android Studio a 1024×600 y con datos de ejemplo
(velocidad, canción, apps). No vienen de una radio: el mapa no aparece porque necesita internet, y la pantalla
Sistema muestra "layoutlib" en vez del modelo y deja la memoria en blanco, cosas que en la radio salen bien.</sub>

## Qué tiene

**Inicio**
- **Mi coche**: un coche dibujado visto desde arriba, la **velocidad por GPS** en grande, el rumbo y la altitud.
- **En el centro, a elegir** en Sistema → Ajustes:
  - **Mapa** de OpenStreetMap en gris claro, con tu posición, zoom, recentrar y el botón **Navegar** (abre Google
    Maps, Waze u otra). Necesita internet para las zonas nuevas; las ya vistas se guardan para verlas sin conexión.
  - **Panel** de conducción, sin internet: velocímetro de aguja, distancia, tiempo en marcha, media y máxima.
- **Música**: carátula, título y artista de lo que suena (también la radio FM, si la publica), con anterior,
  reproducir/pausa y siguiente.
- **Android Auto**: un botón en la barra lateral y un acceso rápido que abren la app de proyección de la radio
  (Zlink, AutoKit, Carlinkit, TLink…), que es la que muestra Android Auto y CarPlay. Si no la encuentra sola, te
  deja elegirla.
- **Accesos rápidos**: Android Auto, Radio, Teléfono y Navegar. Cada uno busca solo la app adecuada de tu radio;
  mantenlo pulsado para elegir otra.

**Apps**: todas las apps de la radio. Mantener pulsada una: información o usarla para un acceso rápido.

**Sistema**
- **Estado**: modelo, Android, memoria y almacenamiento libres, temperatura del procesador.
- **Optimización rápida**: *Liberar memoria* (cierra lo que está en segundo plano, menos lo que suena, la radio y
  lo del coche) y *Animaciones rápidas* a 0,5×.
- **Ajustes**: mapa o panel, corrección de la velocidad (±15 %), reiniciar el viaje, permitir ver qué suena y
  elegir la pantalla de inicio.
- **Servicios innecesarios**: desactiva con `pm disable-user` actualizadores que envían datos a China (Adups FOTA),
  registros de depuración, fondos animados, impresión y apps de serie que nadie usa en un coche, más las
  *Recommended* de [UAD-ng](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation).
  **Nada que suene a coche aparece nunca**: radio, cámara de marcha atrás, Bluetooth, CAN bus, MCU, mandos del
  volante, audio, GPS, CarPlay/Android Auto… Tampoco los launchers ni Ajustes. **Restaurar todo** lo devuelve.

## Instalar

### Con Claude Code (lo fácil)

En la carpeta del repo, ejecuta `claude` y dile:

> Instala EdgeOS Car en mi radio Android k80_bsp. Ve diciéndome lo que necesites de mí.

Sigue las instrucciones de [`CLAUDE.md`](../CLAUDE.md#edgeos-car-la-radio-del-coche).

### A mano

1. Compila: `./gradlew :car:assembleDebug` → `car/build/outputs/apk/debug/car-debug.apk`.
2. Pásalo a la radio con una memoria USB y ábrelo, o con la depuración USB activa: `adb install -r car-debug.apk`.
3. Ábrelo, **acepta el permiso de ubicación** (sin él no hay velocidad) y pulsa Inicio → **EdgeOS Car** → *Siempre*.

⚠️ **No desactives el launcher de fábrica.** En esta placa hay casos de radio FM que deja de funcionar sin él.
EdgeOS Car nunca lo ofrece en Servicios innecesarios.

### Para Servicios innecesarios y Animaciones rápidas: sin instalar nada más

Android 8 no deja que una app desactive otras. EdgeOS Car lo hace solo, de dos maneras:

1. **Con root**, si la radio lo tiene (muchas lo traen): lo usa directamente.
2. **Con el ADB de la propia radio**: EdgeOS Car lleva dentro un cliente ADB que se conecta a la radio por la
   red interna (`127.0.0.1:5555`). Activa *Ajustes → Opciones de desarrollador → Depuración USB*; la primera vez
   la radio pregunta **¿Permitir depuración USB?**: marca *Permitir siempre* y acepta.

Si la radio no tiene el ADB por red abierto (el puerto 5555), se abre una vez desde un ordenador con la radio
conectada por USB: `adb tcpip 5555`. En muchas radios hay que repetirlo tras cada arranque. Lo que ya desactivaste
sigue desactivado aunque el ADB esté cerrado.

## Volver atrás

- Sistema → **Restaurar todo**. Sin EdgeOS Car: `adb shell pm enable <paquete>`.
- Launcher de fábrica: Ajustes → Apps → App de inicio, o mantén pulsado Inicio y elígelo.

## Limitaciones conocidas

- Android Auto se ve a pantalla completa en su propia app (Zlink, AutoKit…): Android 8 no permite meter otra app
  dentro de una tarjeta del launcher.
- La velocidad, el viaje y el mapa se actualizan mientras EdgeOS Car está a la vista. Con otra app delante,
  Android 8 limita el GPS en segundo plano, así que ese tramo no cuenta en el viaje.
- Sin acceso a notificaciones, la tarjeta de música no sabe qué suena (los botones sí funcionan).
- Climatización, puertas o presión de neumáticos dependen del CAN bus de cada radio y no están.

## Cómo está hecho

Kotlin con vistas clásicas de Android, sin Compose ni AndroidX: en un Mali-400 cada milisegundo cuenta. Todo se
diseña sobre 1024×600 y se escala a la pantalla real (`ui/Ui.kt`).

| Archivo | Qué hace |
|---|---|
| `HomeActivity.kt` | La pantalla de inicio: barra lateral, barra superior y las tres páginas. |
| `drive/Drive.kt` | Velocidad (Doppler del GPS o distancia entre posiciones), rumbo, altitud y viaje. |
| `drive/Media.kt` | Lo que suena y sus controles. |
| `Apps.kt` | Apps instaladas y la app de cada acceso rápido. |
| `system/Optimizer.kt` | Servicios innecesarios, filtro de lo del coche, memoria, animaciones. |
| `system/Shell.kt`, `system/LocalAdb.kt` | Acceso al sistema: root o el ADB de la propia radio (cliente adaptado de Free My TV, MIT). |
| `ui/Tween.kt` | Lleva velocidad, aguja y mapa al nuevo dato fotograma a fotograma: el GPS da uno por segundo. |
| `ui/` | Tarjetas, mapa, coche, iconos y páginas. |
