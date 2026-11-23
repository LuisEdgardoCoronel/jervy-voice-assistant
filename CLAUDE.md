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
   Después frená y esperá. No arranques el paso siguiente.
5. **Los commits los hago yo.** Nunca ejecutes `git commit`, `git push`, `git rebase`, `git reset`, `git merge` ni nada que modifique el historial. Podés usar `git status` y `git diff`.
6. **Nada de scope creep.** No refactorices, renombres ni "mejores" cosas fuera del paso aprobado. Si ves algo, anotalo en "Pendientes" y avisame.
7. **Dependencias nuevas: preguntar siempre.** No agregues librerías sin que lo apruebe en el plan.
8. **Vengo de React Native.** Cuando introduzcas un concepto de Android nativo por primera vez (Service, Intent, permisos en runtime, ciclo de vida, coroutines/Flow, etc.), explicalo en 2-3 líneas y, si aplica, comparalo con su equivalente en RN. No hace falta repetirlo después.
9. **Si la spec cambia** por una decisión que tomamos, proponé la edición a este archivo como parte del plan y registrala en "Decisiones tomadas".

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
- **minSdk 31** (Android 12) — **targetSdk 36** (Android 16) — **compileSdk 37**.
- **Build:** Gradle Kotlin DSL + version catalog (`libs.versions.toml`).
- **Inyección de dependencias:** manual (un `AppContainer` simple). Sin Hilt en v1.
- **Voz a texto:** `SpeechRecognizer` de Android (es-AR, fallback es-ES).
- **Texto a voz:** `TextToSpeech` de Android (es-AR si está disponible).
- **Palabra clave:** **decisión abierta**, se resuelve en el paso 1 con un spike. Candidatos: Porcupine (Picovoice; verificar licencia y límites del plan gratuito) / Vosk en modo keyword spotting / openWakeWord vía ONNX Runtime.
- **Intérprete de órdenes v1:** gramática / reglas propias en Kotlin, sin red. Detrás de una interfaz para poder reemplazarlo por un LLM en v2.
- **Dependencias permitidas:** solo las aprobadas en un plan.

### Arquitectura
Pipeline con una interfaz por etapa:

```
WakeWordDetector → SpeechToText → CommandParser → Action → Speaker
```

- `:core` — **módulo Gradle Kotlin/JVM puro (sin dependencia del SDK de Android)**: modelos de comando (`Command` sealed class), `CommandParser`, máquina de estados, normalización de texto. Totalmente testeable con JUnit y preparado para Kotlin Multiplatform a futuro. El resto (`audio/`, `actions/`, `system/`, `ui/`) son paquetes dentro del módulo `:app`, que depende de `:core`.
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
- 2026-11-23 — targetSdk 36 con compileSdk 37. — Se compila contra las APIs nuevas sin adoptar todavía los cambios de comportamiento de Android 17, que no están probados. La regla de lint `OldTargetApi` queda desactivada; revisarla al probar en Android 17.
- 2026-11-23 — core/ como módulo Gradle JVM puro (`:core`), no como paquete de app. — El compilador impide imports de Android y deja el camino listo para KMP.

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
- Paso 0.4: definir la versión de Java en un solo lugar para `:app` y `:core` (hoy cada módulo fija Java 11 por separado).
