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

**Frases trampa, resultado global (Samsung, 2026-11-26, fuente VOICE_RECOGNITION):** 2 "jeili" de control y después las 8 frases, 5 veces cada una (40 en total).
- **19 detecciones en total.** Descontando las 2 de control, hubo **17 activaciones falsas en 40 frases (~43 %)**.
  - Variantes, contando todas las detecciones: hey li 7, hayley 6, hey lee 4, hey ley 1, halley 1.
  - Disparan también las de una sola palabra (`hayley`, `halley`), no solo las de dos.
- **Otros 7 resultados finales sin detección:** "li" ×3, "lee", "ley" y "ley lee" ×2. Hay además 2 detecciones ("hey li", "hey lee") que salieron del resultado final y no del parcial.
- No se pudieron asignar las detecciones a cada frase: las pausas de 10 s entre frases no se distinguen en el log. La tabla de arriba queda sin completar por frase.
- Nivel de cada detección (`Mic (parcial…)`): máx −32 a −45 dBFS, igual que con "jeili". Por el nivel no se distinguen.

Música: sin medir. Con este resultado, se decide antes si vale la pena medirla.

**Conclusión parcial:** Vosk escucha "jeili" sobre todo como "hey li", que es una variante de dos palabras. De las de una palabra solo aparece `hayley`, y pocas veces. Los fallos son mitades sueltas de la clave ("hey", "li", "lee", "ley"). Antes de medir las trampas se investiga por qué el Samsung escucha peor.

## Diagnóstico del micrófono (1.2b.3c)

**Pregunta:** ¿el Samsung escucha peor porque le llega menos señal, o porque la señal viene más procesada?

**Cierre:** la fuente queda fija en VOICE_RECOGNITION y se borró la propiedad `micSource`. El log de nivel se queda y suma una línea `Mic (parcial, N bloques): …` al soltar el micrófono, así cada detección deja el nivel de esa palabra clave.

**Configuración durante el diagnóstico (ya borrada):**
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

**Configuración (1.2c.1):**
- Librería sherpa-onnx 2.0.3 (Apache 2.0), que trae ONNX Runtime (MIT).
  - No está en un repositorio Maven verificado: el AAR (`sherpa-onnx-2.0.3.aar`, 52 MB) se baja de los releases de GitHub con la tarea `downloadSherpaAar`.
  - El SHA-256 fijado es el que muestra GitHub, verificado en la descarga.
  - Trae 4 ABIs; con `abiFilters` queda solo `arm64-v8a`.
- Modelo `sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01`, solo inglés.
  - Su README dice Apache 2.0. Está entrenado con GigaSpeech XL (10.000 h), ver el riesgo aceptado en `CLAUDE.md`.
  - El archivo pesa 17,6 MB y su SHA-256 se calculó en la primera descarga.
  - Se empaquetan solo encoder, decoder y joiner `int8` más `tokens.txt` (5,2 MB).
- El modelo y las keywords se leen directo de assets con el `AssetManager`, sin copiarlos a disco.
- Configuración del motor: 1 hilo, `max_active_paths` 4, `num_trailing_blanks` 1, 80 features a 16 kHz.
- Selector de motor temporal: `./gradlew installDebug -PwakeEngine=sherpa|vosk`, por defecto `sherpa`. El log dice `Motor de palabra clave: …`.
- **Tamaño del APK debug:** 85,2 MB (solo Vosk) → **122,1 MB** (Vosk + sherpa).
  - Las `.so` van sin comprimir en el APK.
  - ONNX Runtime ocupa 22 MB y sherpa 10,7 MB.

**Palabra clave** (`app/src/main/assets/models/sherpa-kws-en/keywords.txt`):
```
▁HA IL E Y :1.0 #0.25 @HAILEY
```
- `:` es el refuerzo (más alto detecta más fácil y también da más falsas). `#` es el umbral (más alto es más exigente). `@` es el nombre que devuelve.
- Las líneas sin `:` ni `#` usan `KEYWORDS_SCORE` (1.0) y `KEYWORDS_THRESHOLD` (0.25) de `SherpaKeywordSpotter`.
- Para ajustarlos alcanza con editar el archivo y reinstalar.
- **Cómo se generaron los tokens:** con la herramienta oficial, que corre en Python como herramienta de desarrollo y no forma parte del build.
  - Entorno: Python 3.12.10, en un entorno virtual aparte con `pip install sherpa-onnx sentencepiece pypinyin click`. Instaló sherpa-onnx 2.0.3, sentencepiece 0.2.3 y pypinyin 0.55.0.
  - Comando: `sherpa-onnx-cli text2token --tokens <modelo>/tokens.txt --tokens-type bpe --bpe-model <modelo>/bpe.model raw.txt out.txt`, con `raw.txt` = `HAILEY :1.0 #0.25 @HAILEY`.
  - Salida: `▁HA IL E Y`.
  - Control: la misma corrida da `▁HE LL O ▁WORLD` y `▁HE Y ▁S I RI`, iguales al `keywords.txt` de ejemplo del modelo.
  - Para el plan B, la misma corrida da `HEY HAILEY` → `▁HE Y ▁HA IL E Y`. También `HAYLEY` → `▁HA Y LE Y`.
  - **La deducción a mano estaba mal:** daba `▁HA I LE Y`. Sus 4 tokens existían en `tokens.txt`, pero el orden de fusión que supuse por el id no es el real. Se usa la salida oficial.

**Logs de referencia:** `Modelo de sherpa-onnx cargado en X ms`, `Sherpa: "HAILEY" (tokens …)`, `Sherpa: bloque prom X ms, máx Y ms` (cada 10 s), `Palabra clave detectada: HAILEY`. sherpa no transcribe, así que no hay equivalente de `Vosk final` (NF4).

**Humo (1.2c.1):**

| Prueba | Samsung A34 | Motorola G22 |
|---|---|---|
| Carga del modelo | 1,26 s (en paralelo con el TTS, 0,83 s) | 3,83 s (TTS 1,76 s) |
| Lee `keywords.txt` desde assets | Sí: el motor se creó sin error y detectó `HAILEY` con tokens `▁HA IL E Y` | Sí |
| Detecta "jeili" | **2 / ~10** con refuerzo 1.0 y umbral 0.25 (valores por defecto). Los demás intentos se ven en el nivel (máx −36 a −42 dBFS), sin detección | **1 / ~10** con los mismos valores |
| Tiempo por bloque, prom / máx | 10–12 / 43–76 ms | 21–29 / 82–134 ms (el máximo supera los 100 ms en 5 de 7 ventanas) |

Sobre el máximo en el Motorola, una hipótesis a confirmar: el modelo decodifica por tramos de varios bloques (`chunk-16`). Así el costo se concentra en un bloque de cada pocos, y el promedio queda bajo (21–29 ms). Con un búfer de `AudioRecord` de 200 ms, un pico de 134 ms no pierde audio. Igual no cumple el criterio "máximo < 100 ms" tal como está escrito.

**Criterios de aceptación (1.2c.2):**

| Criterio | Umbral |
|---|---|
| Aciertos de "jeili" a 30 cm | ≥ 18 / 20 |
| Activaciones con las frases trampa (8 × 5) | ≤ 2 / 40 |
| Activaciones falsas en 10 min de música en inglés | ≤ 1 |
| Tiempo por bloque en el Motorola | promedio < 50 ms y máximo < 100 ms |

Si sherpa no los cumple después de ajustar umbral y refuerzo, el siguiente paso es probar "HEY HAILEY" (`▁HE Y ▁HA IL E Y`).

La medición completa va en el 1.2c.2: 20 × "jeili", las 8 frases trampa 5 veces cada una (marcadas con `adb shell log -t Halley "TRAMPA: …"`) y 10 min de música en inglés.

## Comparación de palabras clave: "Morgana" y "HEY HAILEY" (rama `spike/wake-word`)

**Pregunta:** antes de seguir ajustando "Hailey", ¿anda mejor "Morgana" (3 sílabas, pronunciación en español)? El nombre de la app se decide con estos resultados. Todo en el **Motorola**, a ~30 cm, fuente VOICE_RECOGNITION, 2026-11-27.

**Preparación:**
- **Vosk:** `morgana` está en el vocabulario de `vosk-model-small-es-0.42` como una sola palabra (tabla de 100.003 palabras extraída del modelo). También están `morgan`, `mañana`, `gana`, `ganas`, `ganamos`, `amor`, `órgano`, `órganos`, `organizamos`, `margarita`, `freeman` y `por`.
- **sherpa:** tokens oficiales con `text2token` (mismo comando que en el 1.2c.1; control `HELLO WORLD` → `▁HE LL O ▁WORLD`): `MORGANA` → `▁MO R G AN A`; `HEY HAILEY` → `▁HE Y ▁HA IL E Y`; `MORGAN` → `▁MO R G AN` (prefijo exacto de la clave).
- **Marcas:** un script (fuera del repo) escribe `adb shell log -t Halley "<RONDA>: INTENTOS | TRAMPA n | FIN"` cuando el usuario aprieta Enter. Las detecciones se asignan al tramo en que ocurren.

**Frases trampa de ajuste para "Morgana"** (5 veces cada una). No se usan en la medición final, que lleva un juego nuevo:

| # | Frase | Qué prueba |
|---|---|---|
| 1 | "Amor, ganamos el partido" | a-mor-ga-na-mos, casi la secuencia completa |
| 2 | "Lo hice por ganas, no por plata" | "por ganas" |
| 3 | "Mi hermano siempre me gana" | "me gana" |
| 4 | "No tengo ganas de ir mañana" | "ganas" y "mañana" |
| 5 | "Mañana organizamos el asado" | "or-ga-ni-…" |
| 6 | "Pedime una margarita" | "mar-ga-…" |
| 7 | "Hay que donar los órganos" | "ór-ga-nos" |
| 8 | "Me encanta Morgan Freeman" | `▁MO R G AN`, prefijo de la clave |

Para "HEY HAILEY" se usan las 8 trampas de "Halley" (1.2b.3b).

### Resumen

| Ronda | Motor y configuración | Aciertos | Falsas en trampas | Por bloque, prom / máx |
|---|---|---|---|---|
| A | Vosk, gramática `["morgana","[unk]"]`, detección sobre el parcial | 7 / 10 | **22 / 40**, más 7 dobles en silencio | 17–19 / 46–73 ms |
| B | sherpa `▁MO R G AN A :1.0 #0.25` | 0 / 10 | 0 / 40 | 20–23 / 68–120 ms |
| Escalera E1 | sherpa MORGANA `:2.0 #0.25` | 0 / 10 | 0 durante los intentos | 21–24 / 68–125 ms |
| Escalera E1 | sherpa HEY HAILEY `:2.0 #0.25` | 1 / 10 (3 / 3 de cerca, máx −34 a −36 dBFS) | 0 durante los intentos | 21–27 / 87–125 ms |
| Escalera E2 | sherpa MORGANA `:3.0 #0.25` | 0 / 10 | 0 durante los intentos | — |
| D | Vosk, "morgana" + 10 distractoras, detección sobre el parcial | **10 / 10** | **7 / 40**, sin dobles | 26–29 / 79–138 ms |
| E | Vosk, gramática de la D, detección sobre el final con confianza (umbral 0, calibración) | 9 / 10 | 6 / 40 con umbral 0 | 18–21 / 55–161 ms |

Criterios: aciertos ≥ 18 / 20, trampas ≤ 2 / 40, máximo por bloque < 100 ms en el Motorola.

### Ronda A — Vosk, "morgana" sola

- La primera corrida se descartó: no tenía marcas y la sesión duró ~110 s.
- **Dobles en silencio:** 7 detecciones entre 0,46 y 0,56 s después de reabrir el micrófono, con nivel de fondo (−54 a −70 dBFS). Suenan dos "hola" seguidos. Hipótesis: con una sola palabra en la gramática, el primer parcial después de `reset()` elige esa palabra aunque no haya voz.

| # | Falsas con voz / 5 | Dobles en silencio |
|---|---|---|
| 1 | 2 | 2 |
| 2 | 3 | — |
| 3 | 2 | — |
| 4 | 2 | — |
| 5 | **5** | — |
| 6 | 2 | 1 |
| 7 | 3 | — |
| 8 | 3 | 2 |
| **Total** | **22 / 40** | 5 (más 2 durante los intentos) |

### Ronda B y escalera — sherpa

- El modelo carga en 3,4–3,8 s y lee `keywords.txt` sin error; el audio llega con nivel de voz (máx −41 a −47 dBFS).
- **MORGANA:** 0 / 10 con refuerzo 1.0, 2.0 y 3.0. El modelo en inglés no lleva el "morgana" del usuario a esos tokens.
- **HEY HAILEY :2.0:** 1 / 10 a 30 cm, pero 3 / 3 hablando cerca del teléfono. El nivel pesa mucho.
- **Ninguna activación falsa** en ningún escalón.
- El máximo por bloque supera los 100 ms en casi todas las sesiones.

### Ronda D — Vosk con palabras distractoras

Gramática `["morgana","morgan","mañana","gana","ganas","ganamos","amor","órgano","órganos","organizamos","margarita","[unk]"]`; solo `morgana` dispara. Vosk cargó la gramática completa, sin "Ignoring word missing in vocabulary".

| # | Falsas / 5 (A → D) | A dónde se fue (`Vosk final`) |
|---|---|---|
| 1 | 2 → 0 | "amor ganamos" ×5 |
| 2 | 3 → 2 | "órganos" ×3 |
| 3 | 2 → 1 | "gana", "mañana", "amor"… |
| 4 | 2 → 1 | "ganas mañana", "órgano mañana"… |
| 5 | 5 → 0 | "mañana organizamos" ×5 |
| 6 | 2 → 0 | "margarita" ×5 |
| 7 | 3 → 0 | "órganos", "ganas órganos"… |
| 8 | 3 → 3 | "morgan" ×2; las otras 3 dispararon |
| **Total** | **22 → 7 / 40** | |

- **Sin dobles en silencio:** ninguna detección antes de 1,4 s desde que se abre el micrófono.
- **Más CPU:** el máximo por bloque sube a 79–138 ms y supera 100 en 10 de 17 ventanas.
- **NF4:** con distractoras, `Vosk final` loguea esas palabras (solo las de la gramática). Es un build de debug; se revisa antes de un release.
- **No se hizo D2** (sumar `freeman` y `por`), para no ajustar la gramática a medida de estas 8 frases.

### Ronda E — Vosk, detección sobre el final con confianza por palabra

- `setWords(true)`; detecta solo sobre el final si `morgana` tiene confianza ≥ `MIN_CONFIDENCE` (0 en esta corrida, para calibrar). El parcial ya no se consulta.
- **Reloj:** los tiempos de las palabras cuentan desde que se creó el reconocedor; `reset()` no los reinicia. La latencia válida es la del reloj total.
- **Latencia del final:** 700–940 ms desde que termina "morgana" (una de 1.820 ms en "morgana mañana", porque el final esperó a la segunda palabra). En la D la detección salía del parcial, mientras se hablaba: la E suma ~0,8 s antes del beep.

**Confianza de cada "morgana":**

| | Confianzas |
|---|---|
| Aciertos (9; los otros 2 finales de los intentos fueron "morgan" 0.86 y "órgano" 0.58) | 0.48, 0.54, 0.83, 0.85, 0.86, 0.89, 0.94, 0.95, 1.00 |
| Falsas en trampas (6) | T1: 0.88, 0.48, 0.38, 0.94 · T2: 0.60 · T4: 0.93 ("morgana mañana") |

**Con distintos umbrales** (calculado sobre el mismo log):

| Umbral | Aciertos | Falsas / 40 |
|---|---|---|
| 0 | 9 / 10 | 6 |
| 0.50 | 8 / 10 | 4 |
| 0.70 | 7 / 10 | 3 |
| 0.90 | 3 / 10 | 2 |

- Por frase: T1 4, T2 1, T4 1; las demás 0. "Morgan Freeman" dio 0, contra 3 en la D; "Amor, ganamos" dio 4, contra 0 en la D. Con 5 repeticiones por frase, la variación entre rondas es grande; el total (6–7 / 40) es lo estable.
- **La confianza no separa:** hay falsas con 0.93–0.94, como los mejores aciertos. Ningún umbral llega a ≤ 2 / 40 sin perder la mayoría de los aciertos.
- Por bloque: prom 18–21 ms; máx 55–161 ms (supera 100 en 4 de 17 ventanas, con picos al calcular el final).

**Conclusión parcial:** Vosk con distractoras baja las falsas de ~50 % a ~15–18 %, y la confianza por palabra no mejora eso. Queda lejos de ≤ 2 / 40. Sigue: el paso de los desbordes del AudioRecord y cerrar sherpa con HEY HAILEY bajando solo el umbral (0.15 y 0.10).
