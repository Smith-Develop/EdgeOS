#!/usr/bin/env bash
# Inspecciona una radio Android de coche por ADB y deja un informe para planificar EdgeOS Car:
# cómo controla la radio FM, si llegan las teclas del volante, si hay caja CAN-bus y qué trae el fabricante.
# SOLO LEE: no instala, no desactiva ni cambia nada.
#
# Uso:  tools/inspeccionar-radio.sh 192.168.1.50        (ADB por red)
#       tools/inspeccionar-radio.sh                     (radio conectada por USB)
#
# Al final pide dos cosas con la radio delante: pulsar los botones del volante y cambiar de emisora.
# El informe queda en informe-radio-<fecha>/ y en un .tar.gz. Contiene datos del aparato (modelo, apps,
# ajustes): compártelo solo con quien vaya a ayudarte, no lo subas a un sitio público.

set -u
IP="${1:-}"
OUT="informe-radio-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

if ! command -v adb >/dev/null 2>&1; then
    echo "Falta adb (Android platform-tools). Instálalo y vuelve a ejecutar." >&2
    exit 1
fi

if [ -n "$IP" ]; then
    case "$IP" in *:*) ;; *) IP="$IP:5555" ;; esac
    echo "Conectando con $IP…"
    adb connect "$IP" | tee "$OUT/00-conexion.txt"
    export ANDROID_SERIAL="$IP"
fi

if ! adb get-state >/dev/null 2>&1; then
    echo "No hay radio conectada. Si en la radio aparece \"¿Permitir depuración USB?\", acepta y repite." >&2
    exit 1
fi

sh() { adb shell "$@" 2>&1 | tr -d '\r'; }
paso() { echo "  · $1"; }

# Paquetes que suelen ser del coche: radio, MCU, CAN-bus, volante, Bluetooth, proyección…
CAR='radio|fm|tuner|can|mcu|swc|steer|wheel|key|bt|blue|phone|car|vehicle|auto|zlink|carlink|link|syu|fyt|mtc|hct|microntek|ts10|avin|dvr|reverse|backcar|camera|amp|eq|sound|audio|navi|launcher|setting|factory'

echo "Leyendo la radio (1-2 minutos)…"

paso "sistema"
sh getprop > "$OUT/01-getprop.txt"
{
    echo "modelo:   $(sh getprop ro.product.model) / $(sh getprop ro.product.device) / $(sh getprop ro.board.platform)"
    echo "android:  $(sh getprop ro.build.version.release) (API $(sh getprop ro.build.version.sdk))"
    echo "firmware: $(sh getprop ro.build.display.id)"
    echo "root:     $(sh 'su -c id 2>/dev/null || echo no')"
    echo "adb red:  $(sh getprop service.adb.tcp.port) / persist $(sh getprop persist.adb.tcp.port)"
    echo "pantalla: $(sh wm size) $(sh wm density)"
} > "$OUT/02-resumen.txt"
cat "$OUT/02-resumen.txt"

paso "apps"
sh pm list packages -f -s > "$OUT/03-apps-sistema.txt"
sh pm list packages -f -3 > "$OUT/03-apps-instaladas.txt"
sh pm list packages -d > "$OUT/03-apps-desactivadas.txt"
grep -iE "$CAR" "$OUT/03-apps-sistema.txt" "$OUT/03-apps-instaladas.txt" | sed 's/.*package://' > "$OUT/04-apps-del-coche.txt"

paso "detalle de las apps del coche"
: > "$OUT/05-apps-del-coche-detalle.txt"
for pkg in $(sed 's/.*=//' "$OUT/04-apps-del-coche.txt" | sort -u); do
    {
        echo "=================== $pkg"
        sh dumpsys package "$pkg" | grep -E "versionName|codePath|Activity|Service|Receiver|Provider|Action:|Category:|permission|sharedUser" | head -150
    } >> "$OUT/05-apps-del-coche-detalle.txt"
done

paso "servicios del sistema y procesos"
sh service list > "$OUT/06-servicios.txt"
sh ps -A > "$OUT/07-procesos.txt" 2>/dev/null || sh ps > "$OUT/07-procesos.txt"

paso "hardware: puertos serie (MCU), CAN, entradas"
{
    echo "## /dev (serie, MCU, CAN, radio)"
    sh 'ls -l /dev 2>/dev/null' | grep -iE "tty|mcu|can|fm|radio|i2c|spi|uart|hidraw|input|bt"
    echo; echo "## interfaces de red (can0?)"
    sh 'ls /sys/class/net 2>/dev/null'
    echo; echo "## librerías nativas de radio/CAN/MCU"
    sh 'ls /system/lib /system/lib64 /vendor/lib /vendor/lib64 2>/dev/null' | grep -iE "fm|radio|can|mcu|swc|car|tuner"
    echo; echo "## configuraciones del fabricante"
    sh 'ls /system/etc /vendor/etc /oem 2>/dev/null' | grep -iE "can|car|mcu|swc|key|radio|fm|config"
} > "$OUT/08-hardware.txt"
sh getevent -lp > "$OUT/09-dispositivos-de-entrada.txt"
sh dumpsys input > "$OUT/10-dumpsys-input.txt"

paso "ajustes con pinta de coche"
for t in system global; do
    echo "## $t"; sh settings list "$t" | grep -iE "$CAR|canbus|car_|swc|radio|mcu"
done > "$OUT/11-ajustes.txt"

paso "lo que está en pantalla y el registro reciente"
sh dumpsys activity activities | grep -E "mResumedActivity|mFocusedActivity|Run #" | head -40 > "$OUT/12-pantalla.txt"
sh logcat -d -v time | tail -3000 > "$OUT/13-logcat-reciente.txt"

# --- Pruebas con la radio delante ----------------------------------------------------------------------

echo
echo "PRUEBA 1/2: teclas del volante."
echo "Cuando pulses Intro, tienes 25 segundos: pulsa UNA VEZ cada botón del volante, despacio y en este orden:"
echo "  volumen +, volumen −, siguiente, anterior, modo/fuente, descolgar, colgar, silencio (los que tengas)."
read -r -p "Pulsa Intro para empezar (o escribe s y Intro para saltar)… " r
if [ "$r" != "s" ]; then
    adb logcat -c
    adb shell getevent -lt > "$OUT/20-volante-getevent.txt" 2>/dev/null &
    pid=$!
    sleep 25
    kill "$pid" 2>/dev/null
    wait "$pid" 2>/dev/null
    sh logcat -d -v time > "$OUT/21-volante-logcat.txt"
    echo "  hecho: $(grep -c "EV_KEY" "$OUT/20-volante-getevent.txt") eventos de tecla vistos"
fi

echo
echo "PRUEBA 2/2: radio FM."
echo "Abre la app de radio de fábrica. Cuando pulses Intro, tienes 30 segundos: cambia de emisora 2 o 3 veces,"
echo "busca una emisora (scan/seek) y cambia de banda (FM1/FM2/AM) si se puede."
read -r -p "Pulsa Intro para empezar (o escribe s y Intro para saltar)… " r
if [ "$r" != "s" ]; then
    adb logcat -c
    sleep 30
    sh logcat -d -v time > "$OUT/30-radio-logcat.txt"
    sh dumpsys activity activities | grep -E "mResumedActivity|mFocusedActivity" > "$OUT/31-radio-app.txt"
    sh dumpsys media_session > "$OUT/32-media-session.txt"
    sh dumpsys audio | head -200 > "$OUT/33-audio.txt"
    echo "  hecho"
fi

tar -czf "$OUT.tar.gz" "$OUT"
echo
echo "Listo. Informe en $OUT/ y en $OUT.tar.gz"
echo "Pásale $OUT.tar.gz a Claude para planificar la radio FM, las teclas del volante y los datos del coche."
