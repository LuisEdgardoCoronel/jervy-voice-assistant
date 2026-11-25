package com.luiscoronel.jervy.audio

import com.luiscoronel.jervy.core.pipeline.KeywordSpotter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Motor de mentira del paso 1.2b.2a: "detecta" en el próximo bloque de audio después de [trigger]
 * (botón de debug "Simular detección"). Recibe audio real del micrófono, así que el resto del
 * pipeline se prueba igual que con un motor de verdad. Se reemplaza por Vosk en el 1.2b.2b.
 *
 * Un toque mientras no se escucha (pausa, o mientras Jervy habla) se descarta en el [reset] del
 * inicio de la escucha siguiente.
 */
class ManualTriggerSpotter : KeywordSpotter {
    private val triggered = AtomicBoolean(false)

    fun trigger() {
        triggered.set(true)
    }

    override fun accept(samples: ShortArray): String? = if (triggered.getAndSet(false)) KEYWORD else null

    override fun reset() {
        triggered.set(false)
    }

    override fun close() = Unit

    private companion object {
        const val KEYWORD = "simulada"
    }
}
