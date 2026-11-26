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

## Vosk con "Halley" / "jeili" (1.2b.3b)

**Gramática de diagnóstico:** `["hailey","haley","hayley","halley","hey li","hey lee","hey ley","[unk]"]`. El log (`Palabra clave detectada: <variante>`) dice cuál detectó: se anota la variante de cada acierto para achicar la gramática después.

**Aciertos de "jeili", en silencio.** Se hicieron menos intentos de los 20 previstos y no se separaron por distancia:

| Prueba | Samsung A34 | Motorola G22 |
|---|---|---|
| Aciertos | 14 de al menos 17 intentos (≤ 82 %) | 10 / 11 (91 %) |
| Variantes detectadas | hey li 10, hey ley 2, hey lee 1, hayley 1 | hey li 6, hayley 3, hey lee 1 |
| Variantes que nunca aparecieron | hailey, haley, halley | hailey, haley, halley |
| Fallos (`Vosk final`) | 3: "li", "lee", "hey" sueltos | 1: "ley" |
| Tiempo por bloque, prom / máx | 7–9 / 38–72 ms | 19–26 / 77–135 ms (varias ventanas arriba de 100 ms; con la gramática de 4 variantes fue 104 una sola vez) |

Impresión del usuario: el Samsung se notó menos sensible que el Motorola.

**Frases trampa (5 veces cada una, Samsung):**

| Frase | Activaciones / 5 | Variante detectada |
|---|---|---|
| "hay ley" | | |
| "ahí lee" | | |
| "hay líos" | | |
| "hey, listo" | | |
| "jalea" | | |
| "Leila" | | |
| "Kelly" | | |
| "Heidi" | | |

**Música (Samsung):**

| Prueba | Resultado |
|---|---|
| Activaciones falsas en 10 min de música en inglés | |

Frases trampa y música: sin medir. Quedan para después del diagnóstico del micrófono.

**Conclusión parcial:** Vosk escucha "jeili" sobre todo como "hey li", que es una variante de dos palabras. De las de una palabra solo aparece `hayley`, y pocas veces. Los fallos son mitades sueltas de la clave ("hey", "li", "lee", "ley"). Antes de medir las trampas se investiga por qué el Samsung escucha peor.

## Diagnóstico del micrófono (1.2b.3c)

**Pregunta:** ¿el Samsung escucha peor porque le llega menos señal, o porque la señal viene más procesada?

**Configuración (temporal; se borra al cerrar el diagnóstico):**
- La fuente se elige al compilar: `./gradlew installDebug -PmicSource=MIC`. Sin la propiedad se usa `VOICE_RECOGNITION`. También acepta `UNPROCESSED`, que falla con error si el teléfono no la soporta.
- Al abrir el micrófono se loguea `Micrófono: fuente …, dispositivo …, UNPROCESSED soportada …, disponibles AGC/NS/AEC …`.
- Cada 5 s se loguea `Mic: RMS prom X dBFS, máx Y dBFS, pico Z dBFS`:
  - "prom" es el RMS de los 5 s; en silencio, el ruido de fondo;
  - "máx" es el bloque de 100 ms más fuerte, o sea la voz;
  - "pico" es la muestra más alta; cerca de 0 indica que satura.
- Las referencias de dBFS: 0 es la escala completa, −90 el silencio digital y cada −6 dB es la mitad de la amplitud.

**Protocolo:** misma habitación, misma voz, teléfono a 30 cm. En cada combinación:
1. 10 s callado.
2. 10 × "jeili", con una pausa de unos 3 s entre cada uno.

**Resultados:**

| Teléfono / fuente | Dispositivo y efectos | RMS de fondo | RMS máx al hablar | Pico | Aciertos / 10 | Variantes |
|---|---|---|---|---|---|---|
| Samsung / VOICE_RECOGNITION | SM-A346M (tipo 15, integrado); UNPROCESSED no soportada; AGC no, NS sí, AEC sí | −58,4 / −59,9 dBFS | sin medir (ver nota) | −32,1 / −40,4 dBFS en silencio | 11 detecciones, 0 fallos en el log | hey li 7, hayley 2, hey lee 2 |
| Samsung / MIC | Igual que VOICE_RECOGNITION | −57,9 / −58,6 dBFS | −23,2 a −36,5 dBFS (en los resúmenes que agarraron voz) | −11,3 a −18,0 dBFS al hablar | 8 / 10; 3 fallos en el log ("lee", "li", "hey" sueltos) | hey li 7, hayley 1 |
| Motorola / VOICE_RECOGNITION | moto g22 (tipo 15, integrado); UNPROCESSED no soportada; AGC sí, NS sí, AEC sí | −64,2 dBFS | −33,4 / −34,9 dBFS | −18,1 / −20,2 dBFS al hablar | 9 / 10; 1 fallo ("hey" suelto) | hey li 5, hayley 3, hey lee 1 |
| Motorola / MIC | Igual que VOICE_RECOGNITION | −56,7 / −53,5 dBFS | −31,6 a −33,9 dBFS | −20,3 a −23,3 dBFS al hablar | 8 / 10; 1 fallo ("li" suelto), 1 sin rastro en el log | hey li 3, hey lee 3, hayley 2 |

**Nota de medición:** cada detección cierra y vuelve a abrir el micrófono, y eso reinicia el resumen de nivel. Con un "jeili" cada 3–4 s casi no se completan los 5 s, así que el nivel de la voz quedó sin medir y solo hay resúmenes de los períodos de silencio. El usuario decidió seguir sin corregirlo: la comparación entre fuentes se hace por aciertos y por el ruido de fondo.

Tiempo por bloque en el Motorola: prom 19–29 ms; máx 175–196 ms en la primera ventana (arranque) y 66–106 ms después.

**Conclusión:**
- **VOICE_RECOGNITION anda igual o mejor que MIC en los dos teléfonos:** Samsung 11 detecciones sin fallos contra 8/10; Motorola 9/10 contra 8/10. Con 10 intentos la diferencia es chica, pero va siempre en la misma dirección.
- **El nivel de la voz es parecido en los dos teléfonos:** el bloque más fuerte queda entre −31 y −36 dBFS y el pico entre −11 y −23. Ninguno satura ni llega tan bajo como para perder la voz. La diferencia de sensibilidad del 3b no se explica por el nivel de entrada.
- **Con VOICE_RECOGNITION baja el ruido de fondo:** en el Motorola pasa de −54/−57 dBFS con MIC a −64 dBFS, por la supresión de ruido. En el Samsung casi no cambia (−58/−60 contra −58/−59).
- **El Samsung no ofrece control automático de ganancia y el Motorola sí.** Ninguno de los dos soporta UNPROCESSED.
- **En esta tanda el Samsung acertó más que en el 3b**, con 11 detecciones y 0 fallos. La "menor sensibilidad" de aquella prueba parece haber sido variación entre tandas (distancia, forma de decirlo) y no el micrófono.
- **Los fallos siguen siendo mitades sueltas** ("hey", "li", "lee"), en los dos teléfonos y con las dos fuentes. Eso apunta a la gramática o al modelo, no al audio.

## sherpa-onnx (1.2c)

_Pendiente._
