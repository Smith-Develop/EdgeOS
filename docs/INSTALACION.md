# Guía de instalación

Paso a paso para poner **Vidrio** en un Motorola Edge 70 (Android 16). En otros Android 13+ los pasos son los
mismos, aunque algunos menús cambian de nombre.

Tiempo: unos 30 minutos la primera vez, casi todo esperando a que Android Studio descargue lo que necesita.

> [!NOTE]
> La versión 0.1 aún no se ha compilado nunca (ver el [README](../README.md)). Si algún paso falla con un
> error de compilación, copia el mensaje en un *issue*.

## Lo que necesitas

- El teléfono, con Android 13 o superior, y su cable USB.
- Un PC con Windows, macOS o Linux, con unos 15 GB libres.
- Wi-Fi, solo para la parte de Limpieza (Shizuku).

---

## 1. Preparar el teléfono

### 1.1 Activar las Opciones de desarrollador

1. Abre **Ajustes → Acerca del teléfono**.
2. Pulsa **7 veces** en **Número de compilación**. Te pedirá el PIN y luego dirá *"Ya eres desarrollador"*.

### 1.2 Activar la Depuración USB

1. Abre **Ajustes → Sistema → Opciones para desarrolladores**.
2. Activa **Depuración USB** y confirma.

---

## 2. Preparar el PC

### 2.1 Instalar Android Studio

Descárgalo de [developer.android.com/studio](https://developer.android.com/studio) e instálalo con las opciones
por defecto. Trae todo lo necesario: Java, el Android SDK y `adb`.

### 2.2 Descargar el proyecto

Con Git:

```bash
git clone https://github.com/Smith-Develop/EdgeOS.git
```

O en GitHub: botón verde **Code → Download ZIP**, y descomprímelo.

### 2.3 Abrirlo en Android Studio

1. **File → Open** y elige la carpeta `EdgeOS`.
2. Espera a que termine la barra de abajo (*Gradle sync*). La primera vez descarga Gradle 9.7, el SDK y las
   librerías, y tarda varios minutos.
3. Si avisa de que falta **Android SDK Platform 37**, pulsa el enlace *Install* que aparece, o instálalo en
   **Tools → SDK Manager → SDK Platforms**.

---

## 3. Instalar Vidrio en el teléfono

1. Conecta el teléfono al PC por USB.
2. En el teléfono aparece **¿Permitir la depuración por USB?**: marca *Permitir siempre desde este ordenador* y
   acepta.
3. En Android Studio, elige tu teléfono en el desplegable de dispositivos (arriba) y pulsa **▶ Run 'app'**.

Desde una terminal, en la carpeta del proyecto, también funciona:

```bash
./gradlew installDebug          # macOS / Linux
gradlew.bat installDebug        # Windows
```

### 3.1 Hacer de Vidrio la pantalla de inicio

1. Abre **Vidrio** en el teléfono (aparece entre tus apps).
2. **Mantén pulsado el fondo**, en una zona sin iconos → **Usar Vidrio como inicio** → elige *Vidrio*.

O desde **Ajustes → Apps → Apps predeterminadas → App de inicio → Vidrio**.

A partir de ahí, el botón o gesto de Inicio lleva a Vidrio.

### 3.2 Poner tu fondo de pantalla

Mantén pulsado el fondo → **Cambiar fondo de pantalla** y elige una foto. Vidrio usa su propio fondo, no el
del sistema, porque es el único que puede desenfocar.

---

## 4. Limpieza de apps de fábrica (opcional)

Para desactivar apps del sistema sin root, Vidrio usa **Shizuku**, una app de código abierto que da a otras
apps los mismos permisos que tendría `adb`.

### 4.1 Instalar Shizuku

Desde [Google Play](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) o desde
[shizuku.rikka.app](https://shizuku.rikka.app/download/).

### 4.2 Iniciar Shizuku (la primera vez)

1. Conéctate a una red **Wi-Fi**.
2. Abre Shizuku → **Iniciar con depuración inalámbrica → Emparejar**.
3. Shizuku deja una notificación para escribir el código. Ve a **Ajustes → Sistema → Opciones para
   desarrolladores → Depuración inalámbrica** (actívala) → **Vincular dispositivo con código de sincronización**.
4. Escribe en la notificación de Shizuku el código de 6 cifras que aparece.
5. Vuelve a Shizuku y pulsa **Iniciar**. Arriba debe poner *Shizuku está en ejecución*.

> [!IMPORTANT]
> **Después de cada reinicio del teléfono**, Shizuku se detiene: ábrelo y pulsa **Iniciar** otra vez (no hace
> falta volver a emparejar). Las apps que ya desactivaste siguen desactivadas aunque Shizuku esté parado.

### 4.3 Usar Limpieza

1. En Vidrio, mantén pulsado el fondo → **Limpieza de apps de fábrica**.
2. Pulsa **Dar permiso** y acepta en el aviso de Shizuku.
3. Vidrio descarga la lista de UAD-ng (la primera vez, con internet) y muestra solo las apps **instaladas en
   tu teléfono** que esa lista marca como *Recommended*.
4. Marca las que quieras quitar y pulsa **Desactivar**.

Consejos:

- **Empieza por pocas** y usa el teléfono un día antes de seguir.
- *Mostrar también las "avanzadas"* añade las apps *Advanced* de UAD-ng, que tienen más riesgo: lee su
  descripción antes de marcarlas.
- Una app desactivada aparece atenuada, con el botón **Activar** para devolverla.

### 4.4 Restaurar

**Restaurar todo** vuelve a activar cada app que desactivó Vidrio. Sin Vidrio, desde un PC:

```bash
adb shell pm enable <nombre.del.paquete>
```

---

## 5. Volver al launcher de Motorola

**Ajustes → Apps → Apps predeterminadas → App de inicio** → el launcher de Motorola.

Para quitar Vidrio del todo: primero **Restaurar todo** en Limpieza, luego vuelve al launcher de Motorola y
desinstala Vidrio desde Ajustes → Apps.

---

## Problemas frecuentes

| Problema | Solución |
|---|---|
| *Failed to find target with hash string 'android-37'* | Instala **Android SDK Platform 37** en Tools → SDK Manager. |
| El *Gradle sync* falla al descargar | Revisa la conexión o el proxy (File → Settings → HTTP Proxy) y pulsa *Try again*. |
| Android Studio no ve el teléfono | Cambia el modo USB a **Transferencia de archivos**, vuelve a aceptar la depuración USB y prueba otro cable o puerto. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Hay otra copia de Vidrio firmada con otra clave: desinstálala y vuelve a instalar. |
| No hay efecto de vidrio | El vidrio necesita Android 13 o superior. En el Edge 70 con Android 16 debe verse. |
| Limpieza dice *Abrir Shizuku* aunque ya lo iniciaste | Shizuku se detuvo, por ejemplo tras un reinicio: ábrelo y pulsa **Iniciar**. |
| *No se pudo cargar la lista de UAD-ng* | La primera vez hace falta internet. Después usa la copia guardada durante una semana. |
| Una app no se pudo desactivar | Algunas apps del sistema están protegidas por Android y no se pueden desactivar sin root. |
| El teléfono va mal tras desactivar algo | Limpieza → **Restaurar todo**, o activa esa app con `adb shell pm enable <paquete>`. |
