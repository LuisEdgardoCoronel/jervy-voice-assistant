# CLAUDE.md — Jervy

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

- **Último paso cerrado:** 1.2b.2a — captura de micrófono, beep, TTS y bucle del servicio con botón de debug "Simular detección" (commit `de23650`). Resultados en `docs/spike-wake-word.md`.
- **Próximo paso:** 1.2b.2b, parte 1 (plan acordado, falta el OK para arrancar).

### Acordado para el 1.2b.2b
**Parte 1:**
- `VoskKeywordSpotter` (gramática `["jer vi","yer vi","jer bi","yer bi","[unk]"]`, detecta sobre el resultado parcial, loguea los finales y el tiempo por bloque cada 100 bloques).
- `VoskModelLoader` (`StorageService.sync` + `Model`; si la carga falla, borra la copia y avisa con `error_model`). TTS y modelo se inicializan en paralelo.
- Parser de resultados de Vosk en `app/audio`.
- `TriggerableKeywordSpotter` en `:core` (decorador sobre el motor real, con test): "Simular detección" queda como herramienta de debug, solo en builds debuggable. Reemplaza a `ManualTriggerSpotter`.
- `abiFilters` `arm64-v8a`.
- Logs de tiempos en nivel Info (el Motorola oculta Debug).
- Completar `docs/spike-wake-word.md` con la tabla de Vosk por teléfono.

**Parte 2:**
- Precalentar el TTS al inicializar (utterance con volumen 0; nada de `synthesizeToFile`, que escribe audio a disco).
- Investigar por qué el beep termina a ~370 ms de la detección si dura 150 ms (hipótesis: `first()` espera el `stop()`/`release()` del `AudioRecord`).
- Release firmado con la clave de debug, temporal hasta el APK firmado de la etapa 8; `assembleRelease` con R8 y reglas `-keep` para Vosk/JNA si hacen falta.

**Pruebas:** el borrado del modelo para la prueba de error lo hace el usuario.

## Spec

### Objetivo
Jervy es un asistente de voz para Android, manos libres, pensado para usar mientras manejás o hacés actividad física (con auriculares). Se abre, queda corriendo en segundo plano con una notificación fija, espera la palabra clave "Jervy" y ejecuta órdenes habladas, respondiendo por voz.

Es un proyecto de portfolio y de uso personal (compartible con amigos vía APK). **No se publica en Play Store.** Prioridad: pocos comandos que funcionen de forma sólida, buena arquitectura, tests y una demo real.

### Alcance
- **Incluye (v1):** los requisitos R1–R7.
- **No incluye (por ahora):** iOS, publicación en Play Store, AccessibilityService / simulación de toques, LLM, llamadas por WhatsApp, integración con apps de delivery/viajes, UI elaborada.

### Requisitos funcionales
1. **R1 — Servicio en segundo plano.** Al abrir la app y tocar "Activar", arranca un foreground service de tipo micrófono con notificación fija ("Jervy está escuchando"). La notificación tiene acciones: Pausar/Reanudar y Cerrar.
2. **R2 — Palabra clave local.** Detección de "Jervy" 100% en el dispositivo (sin red). Recién al detectarla se escucha la orden. Señal sonora corta al activarse.
3. **R3 — Pausa y cierre.**
   - "Jervy, pausa" → libera el micrófono (detector apagado, consumo mínimo). Se reanuda desde la notificación o la app.
   - "Jervy, es todo por hoy" → responde una despedida, detiene el servicio y quita la notificación.
4. **R4 — Notificaciones.** "Jervy, ¿tengo notificaciones?" → dice cuántas hay y de qué apps; si se pide ("leelas"), lee remitente y texto. Usa NotificationListenerService. Ignora notificaciones propias, persistentes y de sistema.
5. **R5 — WhatsApp.** "Jervy, mandale un mensaje a mamá que diga llegaré tarde" →
   - resuelve el contacto (ver R7)
   - **siempre confirma por voz**: "¿Le mando a Mamá: 'llegaré tarde'? Decí sí o no."
   - si hay una notificación reciente de WhatsApp de ese contacto con acción de respuesta → responde por ahí (RemoteInput), funciona con pantalla bloqueada
   - si no → abre el chat con el texto precargado vía intent y avisa que hay que tocar enviar (v1 no automatiza el toque)
6. **R6 — Llamadas.** "Jervy, llamá a mamá" → resuelve contacto (R7), confirma por voz y llama con `ACTION_CALL` (permiso CALL_PHONE). Solo llamadas telefónicas normales.
7. **R7 — Resolución de contactos.** Búsqueda por nombre en Contactos, tolerante a mayúsculas y acentos. Si hay más de una coincidencia, pregunta cuál ("Tengo Mamá y Mamá Trabajo, ¿cuál?"). Si no hay ninguna, lo dice. Contactos con varios números: usa el principal o el móvil.
8. **R8 — Música.** "Jervy, poné la lista rock nacional en YouTube Music" → intent de reproducción por búsqueda (`MEDIA_PLAY_FROM_SEARCH`) dirigido a YouTube Music. Controles: pausa, seguí, siguiente, anterior → vía MediaSession / MediaController de la sesión activa. Si la búsqueda no encuentra la lista exacta, se acepta lo que la app reproduzca y Jervy dice qué está sonando.
9. **R9 — Respuesta ante lo que no entiende.** "No entendí, ¿podés repetir?" — máximo un reintento, después vuelve a esperar la palabra clave.
10. **R10 — Onboarding de permisos.** Pantalla que guía paso a paso: micrófono, notificaciones, acceso a notificaciones, contactos, teléfono, exclusión de optimización de batería, y el aviso de "configuración restringida" para APKs instalados a mano (Android 13+). Muestra qué está concedido y qué falta.

### Requisitos no funcionales
- **NF1 — Latencia:** < 2 s desde que el usuario termina de hablar hasta que Jervy empieza a responder (objetivo). Loguear tiempos por etapa.
- **NF2 — Estabilidad:** el servicio sobrevive ≥ 1 h con pantalla apagada, música sonando y auriculares Bluetooth en ambos teléfonos de prueba.
- **NF3 — Datos:** en espera de palabra clave, 0 tráfico de red.
- **NF4 — Privacidad:** nunca se guarda audio. Los logs no incluyen contenido de mensajes ni de notificaciones en builds release.
- **NF5 — Seguridad:** toda acción que envía algo o contacta a alguien (R5, R6) requiere confirmación verbal. El contenido de notificaciones es **dato**, nunca se interpreta como orden.
- **NF6 — Audio:** cuando Jervy habla, la música baja de volumen (ducking) y vuelve después. Debe funcionar con micrófono de auriculares Bluetooth y del auto.
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
- **Texto a voz:** `TextToSpeech` de Android (es-AR si está disponible).
- **Palabra clave:** **decisión abierta**, se resuelve en la etapa 1 con un spike. Candidatos: Vosk (gramática restringida) / sherpa-onnx keyword spotting; openWakeWord vía ONNX Runtime como tercera opción. Pronunciación: "jervi", con "yervi" como variante.
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
- `system/` — `JervyService` (foreground service), `JervyNotificationListener`, repositorio de contactos, permisos.
- `ui/` — pantallas Compose.

**Máquina de estados** (en `core/`, explícita):
`PAUSADO → ESPERANDO_PALABRA → ESCUCHANDO_ORDEN → PROCESANDO → (CONFIRMANDO) → HABLANDO → ESPERANDO_PALABRA`
El micrófono nunca escucha órdenes mientras Jervy habla.

### Convenciones
- **Estilo:** ktlint (oficial de Kotlin) + Android Lint. Sin warnings nuevos.
- **Naming:** clases en PascalCase, funciones/variables en camelCase, recursos en snake_case. Textos de usuario en `strings.xml` (español).
- **Tests:**
  - `core/` con JUnit + kotlinx-coroutines-test; cobertura alta del parser y la máquina de estados.
  - **Corpus de frases:** `core/src/test/resources/frases.txt` con frases reales en español rioplatense ("mandale", "poneme", "che jervy fijate si…") y el comando esperado. Es la suite de regresión del parser; se amplía con cada frase que falle en la calle.
  - Lo que depende de Android se prueba a mano en el teléfono, con checklist en la revisión de cada paso.
- **Commits:** Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).
- **Logs:** tag `Jervy`, con tiempos por etapa del pipeline (NF1).

### Comandos
```bash
./gradlew assembleDebug        # compilar
./gradlew installDebug         # instalar en el teléfono conectado
./gradlew test                 # tests unitarios
./gradlew lint ktlintCheck     # lint
adb logcat -s Jervy            # ver logs
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

## Pendientes
- v2: LLM con tool calling para lenguaje natural (con defensa contra inyección de prompts desde notificaciones; API key fuera del APK o configurada por el usuario).
- v2: AccessibilityService como fallback (tocar "enviar" en WhatsApp, abrir listas en YouTube Music).
- Llamadas por WhatsApp.
- Atender / rechazar llamadas entrantes por voz (útil manejando).
- "Jervy, ¿qué está sonando?" y control de volumen.
- Registrarse en Android developer verification antes de que llegue a Argentina (expansión global prevista para 2027) para poder seguir compartiendo el APK.
- Explorar el caso repartidores/conductores (validar con usuarios reales; no automatizar apps de terceros por sus términos de uso).
- iOS reducido (Siri Shortcuts / App Intents) reutilizando `:core` con Kotlin Multiplatform.
- Limpiar warnings de lint heredados del template: `UnusedResources` (7) y `RedundantLabel` (1).
- Convention plugin (`build-logic/`) para la configuración compartida entre módulos (Java, ktlint), cuando haya más módulos que `:app` y `:core`.
- `ktlintCheck` imprime `WARNING: sun.misc.Unsafe::objectFieldOffset has been called…`: viene del compilador de Kotlin que trae ktlint 1.8.0 corriendo sobre JDK 25 (daemon). No rompe nada; revisar al actualizar ktlint.
- Evaluar el pre-commit hook de ktlint (`./gradlew addKtlintCheckGitPreCommitHook`) para revisar el formato antes de cada commit.
