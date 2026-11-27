#!/usr/bin/env bash
# Marca en el log de Halley cada tramo de una ronda de comparación de palabras clave.
# Uso: bash tools/ronda.sh <serial> <morgana|hailey> <etiqueta> [intentos]
#   Motorola: ZT3229NHLB    Samsung: RFCW91LFNTV
#   Con "intentos" al final, solo marca los 10 intentos (escalón de ajuste), sin trampas.
# Solo escribe líneas en logcat (adb shell log); no toca nada más del teléfono.
set -u
SERIAL=${1:?serial del teléfono}
CONJUNTO=${2:?morgana o hailey}
ETIQUETA=${3:?etiqueta, por ejemplo "E1 MORGANA"}
MODO=${4:-completo}

case "$CONJUNTO" in
  morgana)
    CLAVE="morgana"
    TRAMPAS=(
      "Amor, ganamos el partido"
      "Lo hice por ganas, no por plata"
      "Mi hermano siempre me gana"
      "No tengo ganas de ir mañana"
      "Mañana organizamos el asado"
      "Pedime una margarita"
      "Hay que donar los órganos"
      "Me encanta Morgan Freeman"
    )
    ;;
  hailey)
    CLAVE="hey jeili"
    TRAMPAS=("hay ley" "ahí lee" "hay líos" "hey, listo" "jalea" "Leila" "Kelly" "Heidi")
    ;;
  *)
    echo "Conjunto desconocido: $CONJUNTO (morgana o hailey)" >&2
    exit 1
    ;;
esac

marcar() {
  adb -s "$SERIAL" shell log -t Halley "'$1'" || { echo "No se pudo escribir la marca: $1" >&2; exit 1; }
  echo "  [marca: $1]"
}

echo "$ETIQUETA en $SERIAL. Con Halley escuchando, apretá Enter para empezar."
read -r
marcar "$ETIQUETA: INTENTOS"
echo "Decí 10 veces \"$CLAVE\" (natural, a ~30 cm, dejando que termine el 'hola'). Enter al terminar."
read -r

if [ "$MODO" != "intentos" ]; then
  for i in "${!TRAMPAS[@]}"; do
    n=$((i + 1))
    marcar "$ETIQUETA: TRAMPA $n"
    echo "Trampa $n/8: decí 5 veces \"${TRAMPAS[$i]}\". Enter al terminar."
    read -r
  done
fi

marcar "$ETIQUETA: FIN"
echo "$ETIQUETA terminado."
