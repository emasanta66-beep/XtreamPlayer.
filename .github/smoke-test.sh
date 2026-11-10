#!/usr/bin/env bash
# Prueba de humo dentro del emulador: abre la app dos veces y falla si se cierra o hay un error fatal.
set -u
PKG=app.xtreamplayer
OUT=smoke-out
mkdir -p "$OUT"

fail() {
  echo "::error::$1"
  adb logcat -d > "$OUT/logcat.txt" 2>&1 || true
  adb exec-out screencap -p > "$OUT/fallo.png" 2>/dev/null || true
  grep -A25 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80 || true
  exit 1
}

adb install -r XtreamPlayer.apk || fail "No se pudo instalar el APK"
adb logcat -c

for round in 1 2; do
  adb shell am start -W -n "$PKG/.MainActivity" || fail "No se pudo abrir la app (vuelta $round)"
  sleep 12
  adb exec-out screencap -p > "$OUT/vuelta-$round.png" || true
  adb shell pidof "$PKG" >/dev/null || fail "La app se cerró sola (vuelta $round)"
  adb logcat -d | grep -q "FATAL EXCEPTION" && fail "Error fatal en la app (vuelta $round)"
  # Cierra del todo para que la segunda vuelta sea un arranque en frío.
  adb shell am force-stop "$PKG"
  sleep 2
done

adb logcat -d > "$OUT/logcat.txt" 2>&1 || true
echo "Prueba de humo correcta."
