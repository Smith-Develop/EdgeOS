# EdgeOS · Vidrio: notas para Claude

Un launcher para Android con aspecto de iOS 26 ("Liquid Glass") y una pantalla, **Limpieza**, que desactiva apps
de fábrica con Shizuku. Kotlin + Jetpack Compose. El teléfono objetivo es un **Motorola Edge 70 con Android 16**.

La persona a la que ayudas quiere que **tú lo hagas todo** desde su ordenador y que solo le pidas lo que no puedes
hacer tú: los toques en el teléfono y las decisiones. Habla en español, corto y claro, sin jerga. Antes de cada
paso que cambie algo en su ordenador (instalar herramientas) o en su teléfono (instalar, desactivar apps, cambiar el
inicio), di qué vas a hacer y espera su "sí".

## Estado: la primera compilación es tuya

El código (versión 0.1) se escribió en un entorno sin acceso al Android SDK: **nunca se ha compilado**. Lo primero
es que `./gradlew assembleDebug` termine con `BUILD SUCCESSFUL`. Cuando falle, lee el error, corrige el código y
vuelve a compilar, sin pedirle nada a la persona: es trabajo tuyo, no suyo. Cuéntale solo el resultado ("ya
compila, había 3 errores pequeños").

- Las versiones de `gradle/libs.versions.toml` son las mismas con las que compila
  [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass), la librería del vidrio: AGP 9.3.2,
  Kotlin 2.4.10, Compose 1.12.0, `backdrop` 2.0.1. `backdrop` 2.0.1 pide **compileSdk 37**. Si algo no resuelve,
  mira qué usa ese repo en su `gradle/libs.versions.toml` antes de cambiar versiones a ciegas.
- AGP 9 trae Kotlin integrado: no se aplica `org.jetbrains.kotlin.android`, solo `org.jetbrains.kotlin.plugin.compose`.
- `debloat/Shell.kt` llama por reflexión a `Shizuku.newProcess` (privado desde la API 13 de Shizuku). Si eso falla
  en tiempo de ejecución, la alternativa correcta es un `UserService` de Shizuku.

## Instalarlo para alguien, paso a paso

### 1. Herramientas del ordenador (sin Android Studio)

Hace falta un JDK 17+ (mejor 21), las *command-line tools* del Android SDK y `adb`. Mira qué hay ya
(`java -version`, `echo $ANDROID_HOME`, `adb version`, `sdkmanager --version`) e instala solo lo que falte. Pide
permiso antes de instalar.

- **macOS:** `brew install --cask temurin android-commandlinetools android-platform-tools`
- **Windows:** `winget install Microsoft.OpenJDK.21 Google.PlatformTools`, y las *command-line tools* en ZIP desde
  developer.android.com/studio#command-tools, descomprimidas en `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest`.
  Claude Code usa Git Bash en Windows: `./gradlew` funciona ahí.
- **Linux:** OpenJDK 21 y `adb` del gestor de paquetes, y las *command-line tools* como en Windows, en
  `~/Android/Sdk/cmdline-tools/latest`.

Luego, en todos:

```bash
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-37" "build-tools;37.0.0"
```

y crea `local.properties` en la raíz con `sdk.dir=<ruta del SDK>` (en Windows, con `\\` o `/`). No lo subas a git.

### 2. Compilar

```bash
./gradlew assembleDebug      # → app/build/outputs/apk/debug/app-debug.apk
```

La primera vez descarga Gradle 9.7.1 y las librerías: tarda. Si falla, ver "Estado" arriba.

### 3. El teléfono (lo hace la persona)

Pídele exactamente esto, de uno en uno:

1. **Ajustes → Acerca del teléfono** → pulsar 7 veces **Número de compilación** (pide el PIN).
2. **Ajustes → Sistema → Opciones para desarrolladores** → activar **Depuración USB**.
3. Conectar el teléfono al ordenador con el cable USB. Si pregunta qué hacer con el USB: **Transferencia de archivos**.

Ejecuta `adb devices`:
- `unauthorized`: dile que acepte **¿Permitir la depuración por USB?** en el teléfono, marcando *Permitir siempre*.
- Nada en la lista: otro cable o puerto, y el modo USB en *Transferencia de archivos*. En Windows puede faltar el
  driver USB de Motorola (motorola.com, "Motorola Device Manager").

Sin cable, con Android 11+: **Depuración inalámbrica** → *Vincular con código*. Te dirá la IP:puerto y el código;
`adb pair <ip>:<puerto> <código>` y después `adb connect <ip>:<otro puerto que muestra la pantalla>`.

### 4. Instalar y abrir

```bash
./gradlew installDebug
adb shell am start -n io.vidrio.launcher/.MainActivity
```

Para ver si se cuelga: `adb logcat -d --pid=$(adb shell pidof -s io.vidrio.launcher)` (o `adb logcat -d *:E | tail -100`
justo después de que se cierre). Si se cierra, corrige y repite sin preocuparle.

### 5. Hacerlo el inicio

Pregúntale primero. Que lo haga en el teléfono (mantener pulsado el fondo → **Usar Vidrio como inicio**), o tú con
`adb shell cmd role add-role-holder android.app.role.HOME io.vidrio.launcher`. Para volver al de Motorola:
`adb shell cmd role add-role-holder android.app.role.HOME com.motorola.launcher3` (comprueba el paquete con
`adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME`).

### 6. Capturas reales

`docs/screenshots/*.png` son **maquetas** del diseño, no capturas. Cuando la app funcione, cámbialas por capturas
de verdad con los mismos nombres: pídele que deje el teléfono en cada pantalla (inicio, búsqueda con "ca" escrito,
menú de una app, ajustes, Limpieza) y haz `adb exec-out screencap -p > docs/screenshots/<nombre>.png`. Después
quita del `README.md` los avisos de "maqueta" y de "sin compilar". Avísale de que las capturas muestran sus apps y su
fondo, por si hay algo que no quiere publicar.

### 7. Limpieza con Shizuku (opcional)

1. Shizuku: que lo instale desde Google Play (`moe.shizuku.privileged.api`), o con su permiso descarga el APK de
   github.com/RikkaApps/Shizuku/releases e instálalo con `adb install`.
2. Iniciarlo desde el ordenador es más fácil que por Wi-Fi: en Shizuku, *Iniciar mediante conexión a un
   ordenador* → *Ver comando* muestra el comando exacto; pídeselo o prueba
   `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`. Tras cada reinicio del teléfono hay que
   iniciarlo otra vez.
3. En Vidrio: **Limpieza** → **Dar permiso**. Que elija ella misma qué desactivar en la app. No desactives apps
   desde el ordenador por tu cuenta; si te lo pide, enséñale la lista y la descripción de cada una antes.

## El código

| Archivo | Qué hace |
|---|---|
| `MainActivity.kt` | La actividad HOME (`singleTask`). `onNewIntent` con HOME = volver a la primera página. |
| `AppsModel.kt` | Apps de todos los perfiles con `LauncherApps`, iconos en cuadrado redondeado, dock por defecto. |
| `Prefs.kt` | Dock, fondo y lo que desactivó Limpieza (`disabledByUs`, para *Restaurar todo*). |
| `ui/Home.kt` | Cuadrícula por páginas (4 columnas, filas según la altura), puntos, botón Buscar, dock. |
| `ui/Overlays.kt` | Búsqueda, menú de una app, ajustes del launcher. |
| `ui/DebloatScreen.kt` | Limpieza: estado de Shizuku, lista, desactivar y restaurar. |
| `ui/Glass.kt` | `Modifier.glass()`: el vidrio de `backdrop` (vibrancy + blur + lens). |
| `ui/Wallpaper.kt` | El fondo propio (Android 13+ no deja leer el del sistema a apps normales). |
| `debloat/Shell.kt`, `debloat/Debloater.kt` | Comandos vía Shizuku; la lista de UAD-ng (se descarga, es GPL-3). |

- `minSdk 33`: el vidrio usa `RuntimeShader`. `targetSdk 36` (Android 16).
- Textos de la interfaz en español. Comentarios en español, cortos, solo donde el porqué no es obvio.
- Sin recursos de Apple: ni iconos, ni SF Symbols, ni SF Pro, ni fondos. La fuente es Inter (OFL).

## Siempre

- Nunca `pm uninstall` ni borrar datos de apps del sistema: solo `pm disable-user --user 0`, a través de
  `Debloater`, para que *Restaurar todo* lo pueda devolver.
- Nunca ofrecer en Limpieza los launchers, Ajustes, la interfaz del sistema, Shizuku ni las apps que UAD-ng marca
  *Expert* o *Unsafe* (`Debloater.protectedPackages` y el filtro de `removal`).
- No cambies ajustes del teléfono que no tengan que ver con Vidrio (red, cuentas, pantalla, sonido).
- Nunca subas `local.properties`, `*.keystore`, `*.jks` ni claves.
- Después de cada cambio, `./gradlew assembleDebug` debe terminar en `BUILD SUCCESSFUL`.
- Haz commits cuando algo funcione, con mensajes en español. Pregunta antes de hacer `git push`.
