#!/usr/bin/env bash
# Copia a este ordenador las apps de fábrica de la radio que hay que estudiar para la radio FM, los botones del
# volante y la caja CAN-bus (radios Reglink como la k80_bsp). SOLO LEE: no cambia nada en la radio.
#
# Uso:  tools/extraer-apks-radio.sh 10.79.107.52
#
# Deja apks-radio-<fecha>/ y un .tar.gz. Son apps del fabricante: úsalas solo para estudiar cómo funciona tu radio,
# no las publiques.

set -u
IP="${1:-}"
OUT="apks-radio-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
if [ -n "$IP" ]; then
    case "$IP" in *:*) ;; *) IP="$IP:5555" ;; esac
    adb connect "$IP" >/dev/null
    export ANDROID_SERIAL="$IP"
fi
adb get-state >/dev/null 2>&1 || { echo "No hay radio conectada." >&2; exit 1; }

# Radio FM (pantalla, motor y receptor de órdenes), botones del volante, CAN-bus y servicios de Reglink.
for pkg in com.reglink.apps.radio com.reglink.apps.mtkradio com.android.fmradio com.reglink.apps.serviceui \
           com.reglink.keystudy com.reglink.vehicleinfo com.reglink.services com.reglink.apps.settings; do
    path=$(adb shell pm path "$pkg" 2>/dev/null | tr -d '\r' | sed -n 's/^package://p' | head -1)
    if [ -z "$path" ]; then echo "  (no está) $pkg"; continue; fi
    adb pull "$path" "$OUT/$pkg.apk" >/dev/null && echo "  copiado $pkg"
done
for lib in libfmjni.so libfmcust.so; do
    for dir in /system/lib /vendor/lib; do
        adb pull "$dir/$lib" "$OUT/$lib" >/dev/null 2>&1 && { echo "  copiado $lib"; break; }
    done
done
tar -czf "$OUT.tar.gz" "$OUT"
echo "Listo: $OUT.tar.gz"
