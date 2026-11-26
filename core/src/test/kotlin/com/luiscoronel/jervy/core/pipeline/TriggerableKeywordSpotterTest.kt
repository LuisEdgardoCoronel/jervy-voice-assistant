package com.luiscoronel.jervy.core.pipeline

import com.luiscoronel.jervy.core.pipeline.TriggerableKeywordSpotter.Companion.SIMULATED_KEYWORD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerableKeywordSpotterTest {
    private val delegate = RecordingSpotter()
    private val spotter = TriggerableKeywordSpotter(delegate)

    @Test
    fun `sin disparo devuelve lo que detecta el motor real`() {
        assertNull(spotter.accept(SILENCE))
        assertEquals(KEYWORD, spotter.accept(MATCH))
    }

    @Test
    fun `el disparo detecta en el bloque siguiente una sola vez`() {
        spotter.trigger()

        assertEquals(SIMULATED_KEYWORD, spotter.accept(SILENCE))
        assertNull(spotter.accept(SILENCE))
    }

    @Test
    fun `el audio pasa al motor real tambien en el bloque simulado`() {
        spotter.trigger()
        spotter.accept(SILENCE)
        spotter.accept(MATCH)

        assertEquals(listOf(SILENCE, MATCH), delegate.accepted)
    }

    @Test
    fun `reset descarta un disparo pendiente y resetea el motor real`() {
        spotter.trigger()
        spotter.reset()

        assertNull(spotter.accept(SILENCE))
        assertEquals(1, delegate.resets)
    }

    @Test
    fun `close cierra el motor real`() {
        spotter.close()

        assertTrue(delegate.closed)
    }

    private class RecordingSpotter : KeywordSpotter {
        val accepted = mutableListOf<ShortArray>()
        var resets = 0
        var closed = false

        override fun accept(samples: ShortArray): String? {
            accepted += samples
            return if (samples === MATCH) KEYWORD else null
        }

        override fun reset() {
            resets++
        }

        override fun close() {
            closed = true
        }
    }

    private companion object {
        const val KEYWORD = "jer vi"
        val SILENCE = shortArrayOf(0)
        val MATCH = shortArrayOf(1)
    }
}
