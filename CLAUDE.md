# CLAUDE.md — Halley

## Forma de trabajo (leer antes de cualquier cambio)

1. **La spec de abajo manda.** Si algo que te pido contradice la spec, o la spec es ambigua, avisame y preguntá antes de seguir. No completes huecos de la spec por tu cuenta.
2. **Antes de cada paso, un plan.** Antes de escribir o modificar código, presentá un plan corto:
   - qué vas a hacer y por qué (qué requisito R# cubre)
   - qué archivos vas a crear o tocar
   - cómo lo vamos a verificar (tests, comando, prueba en el teléfono)
   - riesgos o decisiones abiertas
   Esperá mi OK explícito. Sin OK no se edita nada.
3. **Pasos chicos.** Un paso = un cambio coherente que se pueda revisar y commitear solo. Si un paso crece, partilo y volvé a planear.
4. **Revisión al terminar cada paso.** Cuando termines:
   - resumí qué cambió (archivos y motivo)
   - corré `./gradlew test` y `./gradlew lint ktlintCheck` y mostrá el resultado
   - decime qué tengo que probar a mano en el teléfono y en cuál (Samsung / Motorola)
   - señalá lo que no te convence o quedó pendiente
   - proponé un mensaje de commit (Conventional Commits)
   Al cerrar cada paso, actualizá "Estado actual" y registrá en docs/ los resultados de las pruebas.
   Después frená y esperá. No arranques el paso siguiente.
5. **Los commits los hago yo.** Nunca ejecutes `git commit`, `git push`, `git rebase`, `git reset`, `git merge` ni nada que modifique el historial. Podés usar `git status` y `git diff`.
6. **Nada de scope creep.** No refactorices, renombres ni "mejores" cosas fuera del paso aprobado. Si ves algo, anotalo en "Pendientes" y avisame.
7. **Dependencias nuevas: preguntar siempre.** No agregues librerías sin que lo apruebe en el plan.
8. **Vengo de React Native.** Cuando introduzcas un concepto de Android nativo por primera vez (Service, Intent, permisos en runtime, ciclo de vida, coroutines/Flow, etc.), explicalo en 2-3 líneas y, si aplica, comparalo con su equivalente en RN. No hace falta repetirlo después.
9. **Si la spec cambia** por una decisión que tomamos, proponé la edición a este archivo como parte del plan y registrala en "Decisiones tomadas".
10. **Dispositivos de prueba.** Podés usar adb para instalar, leer logs y consultar información (getprop, dumpsys). Para interactuar con la pantalla, conceder o revocar permisos, cambiar propiedades o configuración del sistema, o borrar datos, primero preguntame. Las pruebas a mano las hago yo.

## Estado actual

El asistente se llama **Halley** (pronunciado "jeili"; antes Jervy, ver Decisiones).

- **Último paso cerrado:** 1.2c.1 (en `main`) — sherpa-onnx 2.0.3 con el modelo KWS en inglés y "HAILEY", junto a Vosk, con el selector temporal `-PwakeEngine=sherpa|vosk` (por defecto sherpa; se compila con `./gradlew installDebug -PwakeEngine=vosk`). `BlockTimer` es común a los dos motores. Con los valores por defecto, sherpa acierta poco (Samsung 2/~10, Motorola 1/~10).
- **En curso: comparación de palabras clave en la rama `spike/wake-word`.** El nombre de la app (Halley o Morgana) se decide con estos resultados. Detalle completo en `docs/spike-wake-word.md`, sección "Comparación de palabras clave".
  - **Forma de trabajo:** cada ronda la commitea el usuario en la rama, con su configuración (gramática, `keywords.txt`, código de prueba) y su resultado en el doc del spike. No se revierte nada a mano ni se guardan diffs aparte. Lo que gane pasa a `main` en un paso aparte.
  - **Protocolo de cada ronda:** Claude instala y lee el log; el usuario habla. Antes de cada prueba, Claude avisa el teléfono y la configuración. El usuario activa Halley, espera ~5 s (Halley no habla al arrancar, solo al detectar) y corre `bash tools/ronda.sh <serial> <morgana|hailey> "<etiqueta>" [intentos]`, que escribe las marcas en el log. Los escalones de ajuste llevan solo 10 intentos; las rondas completas, 10 intentos más las 8 trampas × 5. Se mide primero en el Motorola (`ZT3229NHLB`) y después en el Samsung (`RFCW91LFNTV`). Herramientas (script, vocabulario de Vosk, `text2token`): sección "Herramientas del spike" del doc.
  - **Resultados en el Motorola (2026-11-27):**
    - A: Vosk "morgana" sola, 7/10 y 22/40 falsas, más dobles en silencio.
    - B y escalera sherpa: MORGANA 0/10 con refuerzo 1.0, 2.0 y 3.0; HEY HAILEY con refuerzo 2.0, 1/10 a 30 cm (3/3 de cerca). Sherpa no tuvo ninguna falsa.
    - **D: Vosk "morgana" con 10 distractoras, detección sobre el parcial: 10/10 y 7/40. Es la mejor configuración de Vosk.**
    - E: Vosk sobre el final con confianza por palabra: descartada (la confianza no separa las trampas y suma ~0,8 s de latencia).
  - **Estado de la rama:** el código de Vosk que quedó es el de la E (detección sobre el final). Para medir la D hay que volver a su detección, la del commit `116a1ec` (sobre el parcial, misma gramática). `keywords.txt` quedó en `▁MO R G AN A :3.0 #0.10 @MORGANA`, del E6. Con Vosk no se usa.
- **Próximos pasos, en este orden:**
  1. ~~Revisar el criterio de falsas~~ — **aprobado y aplicado (2026-11-27)** en "Criterios de aceptación" del doc del spike: falsas en 30 min de audio real (10 de música en inglés, 10 de podcast en español, 10 de radio o auto), ≤ 2 por hora (≤ 1 en los 30 min); las frases trampa nuevas son referencia, sin umbral; el máximo por bloque se revisa con el paso 2.
  2. ~~Desbordes del AudioRecord~~ — **cerrado (2026-11-27)**. En el Motorola con sherpa, en 10 min y 4 aperturas del micrófono, no se perdió audio: 0 desbordes, la base del atraso quedó estable y el pendiente del hardware llegó como máximo a 56 de 200 ms, con bloques de hasta 125 ms. El criterio "máximo < 100 ms" pasa a ser "sin pérdida de audio y promedio < 50 ms". Vosk E no se midió (decisión del usuario). La medición queda en el código (`AudioDrift`, log `Mic: audio … de reloj, atraso …, pendiente HW …`) y se decide junto con `AudioLevel` al cerrar el 1.3. Detalle en la sección "Desbordes del AudioRecord" del doc del spike.
  3. ~~Cerrar sherpa bajando el umbral~~ — **cerrado (2026-11-27)**. Resultados en el Motorola:
     - HEY HAILEY `:2.0 #0.15`: 0/10. El control positivo con la voz en inglés del Traductor sí detectó, pero el modelo no reconoce la pronunciación rioplatense. Quedó descartado y el E5 no se hizo.
     - MORGANA `:3.0`: 0/10 con `#0.15` y 2/10 con `#0.10`.
     - Sin falsas en ningún escalón. **Sherpa no cumple: la medición final se hace solo con Vosk D.** Detalle en "Escalera de umbral" del doc del spike.
  4. **Medición final con Vosk D. Plan propuesto (2026-11-27); el usuario lo aprueba al empezar la sesión siguiente.** Claude no edita nada hasta tener ese OK, y lo primero es avisar de las decisiones abiertas de más abajo.
     - **Qué y por qué (R2):** medir Vosk D contra los criterios de aceptación revisados (ver "Criterios de aceptación" en el doc del spike) para decidir el motor y el nombre (Morgana) en el paso 5.
     - **Preparación (un cambio, que el usuario commitea antes de medir):**
       - `VoskKeywordSpotter.kt` vuelve exactamente al del commit `116a1ec` con `git restore --source 116a1ec -- app/src/main/java/com/luiscoronel/halley/audio/VoskKeywordSpotter.kt`. Eso deja la detección sobre el parcial (y sobre el final si el parcial no la mostró), la misma gramática de 10 distractoras y sin `setWords`. El comando no toca el historial, solo el archivo de trabajo.
       - `VoskResultParser.words()` y sus tests (de la ronda E) quedan sin uso pero no molestan. Se borran al pasar a `main`.
       - `tools/ronda.sh` suma dos conjuntos:
         - `final`: clave "morgana", **20 intentos** y las 8 trampas nuevas de abajo × 5.
         - `audio`: solo marca los tramos `MÚSICA`, `PODCAST`, `RADIO` y `FIN` con Enter.
         - El número de intentos pasa a depender del conjunto (10 en los de ajuste, 20 en `final`).
       - Se compila e instala con `./gradlew installDebug -PwakeEngine=vosk`. El log tiene que decir `Motor de palabra clave: vosk` y Vosk tiene que cargar la gramática sin "Ignoring word missing in vocabulary".
     - **Frases trampa nuevas** (5 veces cada una; ninguna se usó para ajustar). Se buscaron sonidos que **no** están entre las distractoras, para no medir solo lo que la gramática ya absorbe:

       | # | Frase | Qué prueba |
       |---|---|---|
       | 1 | "Mi amor, ganaste" | a-mor-ga-nas-te; "ganaste" no es distractora |
       | 2 | "Mora y Ana vienen mañana" | "mo-ra-ya-na", casi "morgana" sin la g |
       | 3 | "Se llama Morena" | "mo-re-na", 3 sílabas con el mismo arranque |
       | 4 | "Mariana ganó ayer" | "ma-ria-na ga-nó" |
       | 5 | "La morgue está cerrada" | "mor-g…", el arranque exacto |
       | 6 | "Vamos a morfar algo" | "mor-f…", rioplatense |
       | 7 | "Me organicé bien" | "or-ga-ni-cé"; "organizamos" es distractora y esta no |
       | 8 | "Una mortadela, por favor" | "mor-ta-…" |
     - **Orden** (con el "Protocolo de cada ronda" de más arriba):
       1. **Motorola (`ZT3229NHLB`):** `bash tools/ronda.sh ZT3229NHLB final "FINAL VOSK D"`, con 20 intentos a ~30 cm y las 8 trampas × 5.
       2. **Motorola, 30 min de audio real:** `bash tools/ronda.sh ZT3229NHLB audio "AUDIO VOSK D"`, con 10 min de música en inglés, 10 de podcast en español y 10 de radio en español (o en el auto). Nadie habla durante los tramos. Cada "hola" de Halley es una falsa.
       3. **Samsung (`RFCW91LFNTV`):** `bash tools/ronda.sh RFCW91LFNTV final "FINAL VOSK D"`, con 20 intentos y las 8 trampas × 5. El audio real no se mide en el Samsung, por decisión del usuario.
       - Antes de cada tramo Claude avisa el teléfono y la configuración, instala y graba el log con `-T` y la fecha del teléfono. **Al terminar cada tramo, Claude corta la captura del log.**
       - El usuario activa Halley y espera a ver `Micrófono abierto` (unos 5 s, o 10 s si el modelo carga en frío) **antes** de correr el script. En el E6 la marca quedó antes de que se abriera el micrófono.
     - **Qué se anota en el doc del spike** (sección nueva "Medición final — Vosk D"):
       - aciertos sobre 20, por teléfono;
       - falsas y dobles en los intentos;
       - falsas por trampa y lo que salió en `Vosk final`;
       - falsas por tramo de audio real, con hora y `Vosk final`;
       - promedio y máximo por bloque;
       - pérdida de audio (`AudioDrift`): base del atraso, pendiente máximo y desbordes.
     - **Criterios:**
       - aciertos ≥ 18/20 en cada teléfono;
       - audio real ≤ 1 falsa en los 30 min (≤ 2 por hora);
       - trampas: referencia, sin umbral;
       - en el Motorola, sin pérdida de audio y promedio por bloque < 50 ms (en la D fue 26–29 ms).
     - **Verificación:** el conteo sale del log entre marcas. Al cerrar, `./gradlew test lint ktlintCheck` y la revisión del paso.
     - **Decisiones abiertas (confirmar al empezar):**
       1. **Fuente del audio real.** Propuesta: un parlante externo (la PC) a ~1 m del teléfono, a volumen de conversación, con Halley escuchando por el micrófono del teléfono. Si la música sale del mismo teléfono, el cancelador de eco (AEC) cambia el caso; eso queda para NF6.
       2. **Las 8 frases trampa** de arriba: cambiar las que no suenen naturales.
       3. **Si Vosk D no cumple:** frenar y traer opciones (plan B "Oye Halley", openWakeWord) sin ajustar la gramática a medida de estas frases.
     - **Riesgos:**
       - La sesión dura más de una hora. El Motorola tiene que estar enchufado por USB.
       - Con la pantalla apagada, la gestión de batería no debería cortar el servicio en primer plano, pero si se corta se ve en el log.
       - `Vosk final` loguea las palabras de la gramática (NF4, aceptado en debug).
  5. Con eso se decide el motor y el nombre. Se pasa a `main` en un paso aparte y se borra el motor que pierde junto con `-PwakeEngine` y `buildConfig`. Si ninguno cumple: plan B "Oye Halley"; openWakeWord queda como última opción. Después, la parte 2 del 1.2b.2b. El renombre del repo y de la carpeta quedó para la etapa 8 (ver Pendientes).
- **Anterior:** Vosk con la gramática de "Halley" quedó descartado por 17/40 falsas (~43 %) en el Samsung. Antes se cerró el diagnóstico del micrófono (VOICE_RECOGNITION fija).

### Datos para el ajuste de la gramática
- Vocabulario del modelo (100.003 palabras, revisado): están `hailey`, `haley`, `hayley`, `halley`, `hey`, `ey`, `ei`, `jay`, `jai`, `li`, `lee`, `ley`, `lei`; **no** están `jeili`, `jei`, `jey`. El modelo chico no trae el diccionario de pronunciaciones (está compilado en el grafo): no se sabe si `hailey` suena "jeili" o "ailei", ni si `hey` suena "ei" o "jei".
- Se prefieren las variantes de una sola palabra; las de dos ("hey li"…) están solo para medirlas (mismo riesgo que "jer vi"). En la prueba del 3b, "hey li" fue la más detectada en los dos teléfonos (16 de 24 aciertos); de una palabra solo apareció `hayley` (4); `hailey`, `haley` y `halley`, nunca.
- Si ninguna variante anda bien: probar sherpa-onnx en inglés (1.2c) antes de pensar otro nombre. "Hailey" es un nombre inglés común.
- El plan B ante activaciones falsas ("Oye Halley") va en el plan del 1.3.

### Acordado para el 1.2b.2b
**Parte 2:**
- Precalentar el TTS al inicializar (utterance con volumen 0; nada de `synthesizeToFile`, que escribe audio a disco).
- Investigar por qué el beep termina a ~370 ms de la detección si dura 150 ms (hipótesis: `first()` espera el `stop()`/`release()` del `AudioRecord`).
- Release firmado con la clave de debug, temporal hasta el APK firmado de la etapa 8; `assembleRelease` con R8 y reglas `-keep` para Vosk/JNA si hacen falta.

**Pruebas:** el borrado del modelo para la prueba de error lo hace el usuario.

## Spec

### Objetivo
Halley es un asistente de voz para Android, manos libres, pensado para usar mientras manejás o hacés actividad física (con auriculares). Se abre, queda corriendo en segundo plano con una notificación fija, espera la palabra clave "Halley" y ejecuta órdenes habladas, respondiendo por voz.

Es un proyecto de portfolio y de uso personal (compartible con amigos vía APK). **No se publica en Play Store.** Prioridad: pocos comandos que funcionen de forma sólida, buena arquitectura, tests y una demo real.

### Alcance
- **Incluye (v1):** los requisitos R1–R7.
- **No incluye (por ahora):** iOS, publicación en Play Store, AccessibilityService / simulación de toques, LLM, llamadas por WhatsApp, integración con apps de delivery/viajes, UI elaborada.

### Requisitos funcionales
1. **R1 — Servicio en segundo plano.** Al abrir la app y tocar "Activar", arranca un foreground service de tipo micrófono con notificación fija ("Halley está escuchando"). La notificación tiene acciones: Pausar/Reanudar y Cerrar.
2. **R2 — Palabra clave local.** Detección de "Halley" 100% en el dispositivo (sin red). Recién al detectarla se escucha la orden. Señal sonora corta al activarse.
3. **R3 — Pausa y cierre.**
   - "Halley, pausa" → libera el micrófono (detector apagado, consumo mínimo). Se reanuda desde la notificación o la app.
   - "Halley, es todo por hoy" → responde una despedida, detiene el servicio y quita la notificación.
4. **R4 — Notificaciones.** "Halley, ¿tengo notificaciones?" → dice cuántas hay y de qué apps; si se pide ("leelas"), lee remitente y texto. Usa NotificationListenerService. Ignora notificaciones propias, persistentes y de sistema.
5. **R5 — WhatsApp.** "Halley, mandale un mensaje a mamá que diga llegaré tarde" →
   - resuelve el contacto (ver R7)
   - **siempre confirma por voz**: "¿Le mando a Mamá: 'llegaré tarde'? Decí sí o no."
   - si hay una notificación reciente de WhatsApp de ese contacto con acción de respuesta → responde por ahí (RemoteInput), funciona con pantalla bloqueada
   - si no → abre el chat con el texto precargado vía intent y avisa que hay que tocar enviar (v1 no automatiza el toque)
6. **R6 — Llamadas.** "Halley, llamá a mamá" → resuelve contacto (R7), confirma por voz y llama con `ACTION_CALL` (permiso CALL_PHONE). Solo llamadas telefónicas normales.
7. **R7 — Resolución de contactos.** Búsqueda por nombre en Contactos, tolerante a mayúsculas y acentos. Si hay más de una coincidencia, pregunta cuál ("Tengo Mamá y Mamá Trabajo, ¿cuál?"). Si no hay ninguna, lo dice. Contactos con varios números: usa el principal o el móvil.
8. **R8 — Música.** "Halley, poné la lista rock nacional en YouTube Music" → intent de reproducción por búsqueda (`MEDIA_PLAY_FROM_SEARCH`) dirigido a YouTube Music. Controles: pausa, seguí, siguiente, anterior → vía MediaSession / MediaController de la sesión activa. Si la búsqueda no encuentra la lista exacta, se acepta lo que la app reproduzca y Halley dice qué está sonando.
9. **R9 — Respuesta ante lo que no entiende.** "No entendí, ¿podés repetir?" — máximo un reintento, después vuelve a esperar la palabra clave.
10. **R10 — Onboarding de permisos.** Pantalla que guía paso a paso: micrófono, notificaciones, acceso a notificaciones, contactos, teléfono, exclusión de optimización de batería, y el aviso de "configuración restringida" para APKs instalados a mano (Android 13+). Muestra qué está concedido y qué falta.

### Requisitos no funcionales
- **NF1 — Latencia:** < 2 s desde que el usuario termina de hablar hasta que Halley empieza a responder (objetivo). Loguear tiempos por etapa.
- **NF2 — Estabilidad:** el servicio sobrevive ≥ 1 h con pantalla apagada, música sonando y auriculares Bluetooth en ambos teléfonos de prueba.
- **NF3 — Datos:** en espera de palabra clave, 0 tráfico de red.
- **NF4 — Privacidad:** nunca se guarda audio. Los logs no incluyen contenido de mensajes ni de notificaciones en builds release.
- **NF5 — Seguridad:** toda acción que envía algo o contacta a alguien (R5, R6) requiere confirmación verbal. El contenido de notificaciones es **dato**, nunca se interpreta como orden.
- **NF6 — Audio:** cuando Halley habla, la música baja de volumen (ducking) y vuelve después. Debe funcionar con micrófono de auriculares Bluetooth y del auto.
- **NF7 — Manos libres:** ningún flujo de R1–R9 requiere mirar o tocar la pantalla, salvo el fallback de R5 y la reanudación de R3.

### Dispositivos de prueba
| Equipo | Android | Notas |
|---|---|---|
| Samsung A34 | 16 (One UI 8.5) | Principal. Gestión de batería agresiva ("apps en suspensión profunda"). Aplica configuración restringida. |
| Motorola G22 | 12 | Gama baja: referencia de performance y versión mínima. |

### Stack y restricciones técnicas
- **Lenguaje:** Kotlin. **UI:** Jetpack Compose (mínima: activar/pausar, estado, onboarding, logs de debug).
- **Asincronía:** Coroutines + Flow / StateFlow.
- **minSdk 31** (Android 12) — **targetSdk 36** (Android 16) — **compileSdk 37.1**.
- **Build:** Gradle Kotlin DSL + version catalog (`libs.versions.toml`).
- **Java 17** (bytecode; definida una sola vez en `libs.versions.toml` como `java`).
- **IDE:** Android Studio Rabbit 2 (2026.2.2) o superior. La versión de AGP no puede superar la que soporta la última versión estable de Android Studio; el sync del IDE forma parte de la verificación de cualquier cambio de toolchain.
- **Inyección de dependencias:** manual (un `AppContainer` simple). Sin Hilt en v1.
- **Voz a texto:** `SpeechRecognizer` de Android (es-AR, fallback es-ES).
- **Texto a voz:** `TextToSpeech` de Android (es-AR si está disponible), voz femenina.
- **Palabra clave:** **decisión abierta**, se resuelve en la etapa 1 con un spike. Candidatos: Vosk (gramática restringida) / sherpa-onnx keyword spotting; openWakeWord vía ONNX Runtime como tercera opción. Pronunciación: "jeili" (como el inglés "Hailey").
- **Modelos:** se descargan al compilar con una tarea de Gradle (URL y SHA-256 fijados en `libs.versions.toml`, caché en `~/.gradle/jervy-models/`) y se empaquetan como assets. No se versionan en git.
- **Intérprete de órdenes v1:** gramática / reglas propias en Kotlin, sin red. Detrás de una interfaz para poder reemplazarlo por un LLM en v2.
- **Dependencias permitidas:** solo las aprobadas en un plan.

### Arquitectura
Pipeline con una interfaz por etapa:

```
WakeWordDetector → SpeechToText → CommandParser → Action → Speaker
```

- `:core` — **módulo Gradle Kotlin/JVM puro (sin dependencia del SDK de Android)**: interfaces de las etapas del pipeline (con `kotlinx-coroutines-core`), modelos de comando (`Command` sealed class), `CommandParser`, máquina de estados, normalización de texto. Totalmente testeable con JUnit y preparado para Kotlin Multiplatform a futuro. El resto (`audio/`, `actions/`, `system/`, `ui/`) son paquetes dentro del módulo `:app`, que depende de `:core`.
- `audio/` — implementaciones de wake word, STT, TTS, audio focus, routing Bluetooth.
- `actions/` — un archivo por comando (`ReadNotificationsAction`, `SendWhatsAppAction`, `CallContactAction`, `PlayMusicAction`, `MediaControlAction`, `StopAction`, `PauseAction`). Sumar un comando no toca el núcleo.
- `system/` — `HalleyService` (foreground service), `HalleyNotificationListener`, repositorio de contactos, permisos.
- `ui/` — pantallas Compose.

**Máquina de estados** (en `core/`, explícita):
`PAUSADO → ESPERANDO_PALABRA → ESCUCHANDO_ORDEN → PROCESANDO → (CONFIRMANDO) → HABLANDO → ESPERANDO_PALABRA`
El micrófono nunca escucha órdenes mientras Halley habla.

### Convenciones
- **Estilo:** ktlint (oficial de Kotlin) + Android Lint. Sin warnings nuevos.
- **Naming:** clases en PascalCase, funciones/variables en camelCase, recursos en snake_case. Textos de usuario en `strings.xml` (español).
- **Tests:**
  - `core/` con JUnit + kotlinx-coroutines-test; cobertura alta del parser y la máquina de estados.
  - **Corpus de frases:** `core/src/test/resources/frases.txt` con frases reales en español rioplatense ("mandale", "poneme", "che Halley fijate si…") y el comando esperado. Es la suite de regresión del parser; se amplía con cada frase que falle en la calle.
  - Lo que depende de Android se prueba a mano en el teléfono, con checklist en la revisión de cada paso.
- **Commits:** Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).
- **Logs:** tag `Halley`, con tiempos por etapa del pipeline (NF1) en nivel Info: algunos teléfonos (el Motorola) ocultan Debug.

### Comandos
```bash
./gradlew assembleDebug        # compilar
./gradlew installDebug         # instalar en el teléfono conectado
./gradlew test                 # tests unitarios
./gradlew lint ktlintCheck     # lint
adb logcat -s Halley           # ver logs
```

## Roadmap v1 (cada etapa = 1 a 3 pasos; cerrar con prueba en la calle)
0. **Scaffolding:** el proyecto base lo crea el usuario con el asistente de Android Studio (Empty Activity, Compose, Kotlin DSL, minSdk 31). Claude Code completa: módulo `:core` y paquetes de la Arquitectura vacíos, ktlint configurado, revisión del `.gitignore` (`local.properties` y `.idea/` fuera del repo), targetSdk 36. Criterio de éxito: `./gradlew assembleDebug`, `./gradlew test` y `./gradlew installDebug` funcionan desde la terminal y la app abre en el Samsung.
1. **Spike de riesgo:** foreground service + notificación + palabra clave + "hola" por TTS, sobreviviendo 1 h con música y Bluetooth en ambos teléfonos. Decidir motor de wake word. (R1, R2, NF2, NF3)
2. **Pipeline completo + máquina de estados + parser v1** con "pausa" y "es todo por hoy". (R3, R9, NF6)
3. **Onboarding de permisos.** (R10)
4. **Notificaciones.** (R4)
5. **Contactos + llamadas.** (R7, R6)
6. **WhatsApp.** (R5)
7. **Música.** (R8)
8. **Pulido para portfolio:** README con arquitectura y decisiones, video demo, APK firmado.

## Decisiones tomadas
- 2026-11-23 — Solo Android; iOS a futuro y reducido. — iOS no permite escucha continua, leer notificaciones ajenas ni automatizar otras apps.
- 2026-11-23 — Sin Play Store, distribución por APK. — Proyecto de portfolio/personal.
- 2026-11-23 — Kotlin nativo, no React Native. — Services, notificaciones y audio son nativos; con RN igual habría que escribirlos en Kotlin.
- 2026-11-23 — Sin AccessibilityService en v1. — Frágil y difícil de distribuir; se priorizan APIs oficiales.
- 2026-11-23 — Parser por reglas en v1, LLM en v2. — Más simple, offline y testeable; el LLM entra detrás de la misma interfaz.
- 2026-11-23 — targetSdk 36 con compileSdk 37.1. — Se compila contra las APIs nuevas sin adoptar todavía los cambios de comportamiento de Android 17, que no están probados. Se usa 37.1 (versión menor) porque Compose 1.13 (BOM 2026.11.01) lo exige. La regla de lint `OldTargetApi` queda desactivada; revisarla al probar en Android 17.
- 2026-11-23 — core/ como módulo Gradle JVM puro (`:core`), no como paquete de app. — El compilador impide imports de Android y deja el camino listo para KMP.
- 2026-11-23 — Gradle 9.9.2 con AGP 9.5.2. — AGP documenta 9.6.0 como mínimo (no lista 9.9 como probada); verificado con el build completo (assemble, test, lint, ktlintCheck), instalación en ambos teléfonos y sync de Android Studio.
- 2026-11-24 — Porcupine descartado como motor de palabra clave. — Según Picovoice, las keys del plan gratuito dejaron de funcionar el 30/06/2026, no hay plan no comercial y valida la AccessKey por red (choca con NF3).
- 2026-11-24 — Modelos descargados por Gradle con SHA-256 fijado y caché local en `~/.gradle/jervy-models/` (fuera de `caches/` para que la limpieza automática de Gradle no la borre). — Repo liviano, build reproducible y offline con la caché llena; la app no necesita permiso INTERNET.
- 2026-11-24 — Interfaces de las etapas del pipeline en `:core`, implementaciones en `audio/` (y demás paquetes de `:app`). `:core` suma `kotlinx-coroutines-core`. — Mantiene `:core` testeable en JVM y listo para KMP.
- 2026-11-24 — **Riesgo aceptado:** modelo KWS de sherpa-onnx con licencia de datos de entrenamiento no aclarada (GigaSpeech / zh-en). Aceptado solo para uso personal y de portfolio. — **Revisar antes de cualquier publicación o distribución más amplia.**
- 2026-11-25 — Logs de tiempos en nivel Info, no Debug. — El Motorola filtra Debug (`log.tag=I`) y obligaba a cambiar una propiedad del sistema en cada prueba.
- 2026-11-25 — "Simular detección" queda como herramienta de debug (solo builds debuggable), como decorador (`TriggerableKeywordSpotter`) sobre el motor real. — Permite probar el pipeline sin hablar; el motor real sigue recibiendo todo el audio. En release no existe.
- 2026-11-25 — Solo ABI `arm64-v8a` (los dos teléfonos de prueba). Regla de lint `ChromeOsAbiSupport` desactivada. — Ahorra ~30 MB de librerías nativas de Vosk/JNA. Consecuencia: la app no corre en emuladores x86_64.
- 2026-11-25 — Renombre interno completo a Halley, incluido el applicationId (`com.luiscoronel.halley`). — Es el momento más barato: después del onboarding (etapa 3) y con APKs compartidos, cambiar el ID obliga a reinstalar y reconfigurar todo. El caché del modelo conserva el nombre `jervy-models` para no volver a descargarlo.
- 2026-11-25 — El asistente se llama Halley (pronunciado "jeili", voz femenina); antes Jervy. — "jer vi" en Vosk daba activaciones falsas con frases comunes ("ayer vi"): una palabra clave armada con dos palabras cortas y frecuentes es frágil. El TTS en español lee "Halley" como "alei": cuando Halley diga su nombre, el texto hablado se escribe "Jeili".

- 2026-11-26 — Vosk con gramática restringida descartado para "Halley"/"jeili": 17 activaciones falsas en 40 frases trampa (~43 %). Vosk sin gramática tampoco: transcribiría y loguearía todo lo que se dice (NF4), y en el Motorola no alcanzaría la CPU. — Se prueba sherpa-onnx en inglés ("HAILEY"); plan B "Oye Halley".
- 2026-11-27 — "HEY HAILEY" con sherpa descartado. — Dio 0/10 con el umbral en 0.15 (y 1/10 con 0.25) con la pronunciación natural del usuario. Con la voz en inglés del Traductor sí detecta: la integración funciona, pero el modelo en inglés no reconoce la pronunciación rioplatense, y el usuario no le va a hablar en inglés. Sherpa sigue solo con MORGANA, que se pronuncia parecido en los dos idiomas.
- 2026-11-27 — Criterio de tiempo por bloque: "sin pérdida de audio (0 desbordes y la base del atraso estable) y promedio < 50 ms", en lugar de "máximo < 100 ms". — En el Motorola, bloques de hasta 125 ms dejaron como máximo 56 ms pendientes en un búfer de 200 ms, sin perder audio: un pico aislado se recupera en las lecturas siguientes. Lo que importa es no perder audio, no el pico.
- 2026-11-26 — El AAR de sherpa-onnx se baja de los releases de GitHub con una tarea de Gradle (URL y SHA-256 fijados en `libs.versions.toml`, caché `~/.gradle/jervy-models/`) y entra como `files(...)`. — No hay un artefacto Maven verificado y no se commitean 52 MB. Consecuencia: Android Studio no ve las clases de sherpa hasta el primer build.

## Pendientes
- v2: LLM con tool calling para lenguaje natural (con defensa contra inyección de prompts desde notificaciones; API key fuera del APK o configurada por el usuario).
- v2: AccessibilityService como fallback (tocar "enviar" en WhatsApp, abrir listas en YouTube Music).
- Llamadas por WhatsApp.
- Atender / rechazar llamadas entrantes por voz (útil manejando).
- "Halley, ¿qué está sonando?" y control de volumen.
- Registrarse en Android developer verification antes de que llegue a Argentina (expansión global prevista para 2027) para poder seguir compartiendo el APK.
- Explorar el caso repartidores/conductores (validar con usuarios reales; no automatizar apps de terceros por sus términos de uso).
- iOS reducido (Siri Shortcuts / App Intents) reutilizando `:core` con Kotlin Multiplatform.
- Limpiar warnings de lint heredados del template: `UnusedResources` (7) y `RedundantLabel` (1).
- Convention plugin (`build-logic/`) para la configuración compartida entre módulos (Java, ktlint), cuando haya más módulos que `:app` y `:core`.
- `ktlintCheck` imprime `WARNING: sun.misc.Unsafe::objectFieldOffset has been called…`: viene del compilador de Kotlin que trae ktlint 1.8.0 corriendo sobre JDK 25 (daemon). No rompe nada; revisar al actualizar ktlint.
- Si el servicio se cierra mientras se inicializan el TTS o el modelo, lo que ya se creó no se cierra (pérdida de memoria nativa hasta que muere el proceso). Revisar con la máquina de estados (etapa 2).
- Etapa 8: renombrar el repo en GitHub (`jervy-voice-assistant`; GitHub redirige la URL vieja) y la carpeta local; actualizar el remote si hace falta y volver a abrir el proyecto en Android Studio. La memoria de Claude Code está guardada por la ruta de la carpeta: la de la ruta vieja queda huérfana.
- Lint `AndroidGradlePluginVersion`: hay AGP 9.5.3 (usamos 9.5.2). Antes de actualizar, verificar que la última versión estable de Android Studio la soporte (ver Stack) y hacer el sync del IDE.
- Bug: pausar mientras carga el modelo deja que el micrófono se abra ~60 ms después de la pausa (Motorola, 15:26:27.828 → 27.888). Hipótesis: la escucha arranca justo antes de la pausa y abrir el `AudioRecord` es una llamada bloqueante que no se interrumpe al cancelar; el micrófono se suelta recién en el primer chequeo del bucle. Revisar con la máquina de estados (etapa 2).
- Si falla un "Hola" no se avisa: si el TTS falla siempre, Halley queda sin voz y sin aviso. Revisar en la etapa 2.
- Al cerrar el 1.3: revisar `AudioLevel` y sus logs de nivel (cada 5 s y al soltar el micrófono). Se dejaron para medir bolsillo y auto en el 1.3; decidir si quedan (¿solo en debug?) o se sacan.
- Release con R8 (parte 2 del 1.2b.2b): el AAR de sherpa-onnx no trae reglas de ProGuard (`proguard.txt` vacío) y su código nativo crea objetos Kotlin (`KeywordSpotterResult`, configs): hacen falta reglas `-keep` para `com.k2fsa.sherpa.onnx.**`, además de las de Vosk/JNA.
- "Simular detección" podría dejar de responder en algún estado. En la prueba de desbordes (Motorola, 2026-11-27) el usuario lo tocó varias veces, pero en el log hay una sola `Palabra clave detectada: simulada` (21:18:15). No se sabe en qué estado estaba Halley en los otros toques: por ejemplo hablando, con el micrófono recién reabierto o mientras sherpa detectaba. Revisar si el botón ignora toques fuera de ESPERANDO_PALABRA. Puede ir con la máquina de estados (etapa 2).
- Evaluar el pre-commit hook de ktlint (`./gradlew addKtlintCheckGitPreCommitHook`) para revisar el formato antes de cada commit.
