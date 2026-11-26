# Spike de palabra clave (etapa 1)

Registro de las pruebas del spike: foreground service + palabra clave + "Hola" por TTS (R1, R2, NF1, NF2, NF3).
Teléfonos: Samsung A34 (Android 16) y Motorola G22 (Android 12), ambos `arm64-v8a`.

## Cómo medir

- Logs: `adb logcat -s Halley` (hasta el 1.2b.2b el tag era `Jervy`).
- Audio Bluetooth: `adb shell dumpsys audio` (esperado `MODE_NORMAL`, sin SCO: la música sigue en A2DP).
- Los logs de tiempos están en nivel Info desde el 1.2b.2b. En el 1.2b.2a estaban en Debug y el Motorola los ocultaba (`log.tag=I`).
- Las detecciones simuladas con el botón de debug aparecen como `Palabra clave detectada: simulada`: no se cuentan en las métricas.

## 1.2b.2a — Audio, beep, TTS y bucle del servicio (sin motor real)

Detección simulada con el botón de debug "Simular detección".

| Prueba | Samsung A34 | Motorola G22 |
|---|---|---|
| Música por auriculares Bluetooth con Jervy escuchando | Sigue en estéreo (A2DP) | Sigue en estéreo (A2DP) |
| Beep y "Hola" por los auriculares | OK | OK |
| Motor y voz de TTS | Google, es-ES (no hay es-AR) | Google, es-ES (no hay es-AR) |
| Pausar mientras habla | Corta la voz | Corta la voz |
| Pausar y reanudar repetido | Sin errores | Sin errores |

**Tiempos:**

| Medición | Valor |
|---|---|
| Primera respuesta: TTS empieza a hablar (en frío) | ~900 ms |
| Respuestas siguientes (en caliente) | ~5 ms |
| Beep termina, desde la detección (dura 150 ms) | ~370 ms |

**Pendiente para el 1.2b.2b:**
- Precalentar el TTS para evitar los ~900 ms de la primera respuesta.
- Investigar los ~370 ms del beep.
- El error de micrófono (con la grabadora de voz grabando) queda para el 1.2b.2b.

## Vosk (1.2b.2b)

**Configuración:**
- Modelo `vosk-model-small-es-0.42` (Apache 2.0), librería `vosk-android` 0.3.75 (JNA 5.18.1).
- "jervi" y "yervi" no están en el vocabulario: gramática `["jer vi","yer vi","jer bi","yer bi","[unk]"]`.
- Detecta sobre el resultado parcial; los finales se loguean (`Vosk final: "..."`) para analizar las activaciones falsas.
- Solo ABI `arm64-v8a`.

**Logs de referencia:** `Modelo de Vosk copiado/verificado en X ms`, `Modelo de Vosk cargado en X ms`, `Vosk: bloque prom X ms, máx Y ms` (cada 10 s), `Palabra clave detectada: <palabra>`, `Beep terminado a X ms`, `TTS empezó a hablar a X ms`.

**Tamaño del APK debug:** 115 MB sin filtro de ABI → 84,7 MB con `arm64-v8a` (el modelo ocupa ~41 MB comprimido y `libvosk.so` ~10 MB).

Resultados parciales: la prueba se cortó al decidir el cambio de nombre (ver Conclusión). "Sin medir" = no se llegó a probar.

| Prueba | Samsung A34 | Motorola G22 |
|---|---|---|
| Copia del modelo, primer arranque | 0,7 s | 1,8 s |
| Copia/verificación, arranques siguientes | sin medir | 10 ms |
| Carga del modelo | 1,2 s | ~2 s |
| Tiempo por bloque de 100 ms, prom / máx (criterio: < 100 ms en el Motorola) | 7–9 / 33–89 ms | 26–29 / 91–104 ms (el máximo superó una vez los 100 ms; el promedio sobra) |
| Aciertos de "jervi"/"yervi" | Un fallo observado: Vosk reconoció "jer yer" y "vi" por separado | sin medir en detalle |
| Frases trampa | sin medir | **"ayer vi" disparó una detección** |
| `Vosk final: "yer"` | — | Aparece con otras palabras (no era la palabra clave) |
| Beep terminado, desde la detección | 325–537 ms (variable) | sin medir |
| Música, pantalla apagada, Bluetooth, pausa, errores | sin medir | sin medir |

**Bug observado:** pausar mientras carga el modelo deja que el micrófono se abra ~60 ms después de la pausa (Motorola, 15:26:27.828 → 27.888). Anotado en Pendientes de `CLAUDE.md`.

**Conclusión:** el rendimiento de Vosk alcanza en los dos teléfonos (incluido el Motorola de gama baja), pero "jer vi" da activaciones falsas con frases comunes ("ayer vi"): con una palabra clave armada con dos palabras cortas y frecuentes del español, la gramática no alcanza para descartarlas. **Por eso el asistente cambia de nombre a Halley** (pronunciado "jeili"). La evaluación de Vosk sigue con la palabra clave nueva.

## sherpa-onnx (1.2c)

_Pendiente._
