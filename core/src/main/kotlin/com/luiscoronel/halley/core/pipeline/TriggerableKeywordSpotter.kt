package com.luiscoronel.halley.core.pipeline

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decorador de depuración: envuelve el motor real y permite forzar una detección con [trigger]
 * (botón "Simular detección", solo en builds debuggable).
 *
 * El audio siempre pasa al motor real, también en el bloque de la detección simulada, para que su
 * estado interno no se desfase. La detección simulada se informa como [SIMULATED_KEYWORD], así los
 * logs la distinguen de una real.
 *
 * Un toque mientras no se escucha (pausa, o mientras Halley habla) se descarta en el [reset] del
 * inicio de la escucha siguiente. [trigger] se puede llamar desde cualquier hilo.
 */
class TriggerableKeywordSpotter(
    private val delegate: KeywordSpotter,
) : KeywordSpotter {
    private val triggered = AtomicBoolean(false)

    fun trigger() {
        triggered.set(true)
    }

    override fun accept(samples: ShortArray): String? {
        val keyword = delegate.accept(samples)
        return if (triggered.getAndSet(false)) SIMULATED_KEYWORD else keyword
    }

    override fun reset() {
        triggered.set(false)
        delegate.reset()
    }

    override fun close() {
        delegate.close()
    }

    companion object {
        const val SIMULATED_KEYWORD = "simulada"
    }
}
