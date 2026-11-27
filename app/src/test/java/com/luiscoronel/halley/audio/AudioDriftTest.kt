package com.luiscoronel.halley.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDriftTest {
    @Test
    fun `lecturas al ritmo del reloj dan atraso constante`() {
        val drift = AudioDrift(RATE, BUFFER_FRAMES, 3)
        // Cada lectura de 100 ms vuelve 10 ms después de que el hardware la completó.
        assertNull(drift.add(BLOCK, ms(110), null))
        assertNull(drift.add(BLOCK, ms(210), null))
        val report = drift.add(BLOCK, ms(310), null)!!
        assertEquals(300, report.audioMs)
        assertEquals(310, report.clockMs)
        assertEquals(10, report.minLagMs)
        assertEquals(10, report.maxLagMs)
        assertEquals(3, report.blocks)
        assertNull(report.maxBacklogMs)
        assertFalse(report.overflow)
    }

    @Test
    fun `un bloque lento sube el maximo pero la base vuelve`() {
        val drift = AudioDrift(RATE, BUFFER_FRAMES, 4)
        drift.add(BLOCK, ms(110), null)
        // El consumidor tardó 160 ms: la lectura siguiente sale del búfer, ya atrasada.
        drift.add(BLOCK, ms(370), null)
        drift.add(BLOCK, ms(372), null)
        val report = drift.add(BLOCK, ms(410), null)!!
        assertEquals(10, report.minLagMs)
        assertEquals(170, report.maxLagMs)
    }

    @Test
    fun `si se pierde audio la base sube y queda arriba en el periodo siguiente`() {
        val drift = AudioDrift(RATE, BUFFER_FRAMES, 2)
        drift.add(BLOCK, ms(110), null)
        val first = drift.add(BLOCK, ms(210), null)!!
        assertEquals(10, first.minLagMs)
        // Se perdieron 300 ms: el reloj avanza y el audio leído no.
        drift.add(BLOCK, ms(610), null)
        val second = drift.add(BLOCK, ms(710), null)!!
        assertEquals(310, second.minLagMs)
        assertEquals(400, second.audioMs)
        assertEquals(710, second.clockMs)
    }

    @Test
    fun `pendiente del hardware dentro del bufer no es desborde`() {
        val drift = AudioDrift(RATE, BUFFER_FRAMES, 2)
        drift.add(BLOCK, ms(110), BLOCK + 160L)
        val report = drift.add(BLOCK, ms(400), 2 * BLOCK + 3_000L)!!
        assertEquals(187L, report.maxBacklogMs) // 3.000 muestras a 16 kHz
        assertEquals(200, report.bufferMs)
        assertFalse(report.overflow)
    }

    @Test
    fun `pendiente del hardware mayor que el bufer es desborde`() {
        val drift = AudioDrift(RATE, BUFFER_FRAMES, 1)
        val report = drift.add(BLOCK, ms(500), BLOCK + BUFFER_FRAMES + 16L)!!
        assertEquals(201L, report.maxBacklogMs)
        assertTrue(report.overflow)
    }

    @Test
    fun `el maximo del hardware se reinicia en cada periodo`() {
        val drift = AudioDrift(RATE, BUFFER_FRAMES, 1)
        drift.add(BLOCK, ms(110), BLOCK + 3_000L)
        val second = drift.add(BLOCK, ms(210), 2 * BLOCK + 160L)!!
        assertEquals(10L, second.maxBacklogMs)
        val third = drift.add(BLOCK, ms(310), null)!!
        assertNull(third.maxBacklogMs)
    }

    @Test
    fun `flush da el periodo a medias y null sin lecturas`() {
        val drift = AudioDrift(RATE, BUFFER_FRAMES, 50)
        assertNull(drift.flush())
        drift.add(BLOCK, ms(110), null)
        drift.add(BLOCK / 2, ms(160), null)
        val report = drift.flush()!!
        assertEquals(2, report.blocks)
        assertEquals(150, report.audioMs)
        assertNull(drift.flush())
    }

    private fun ms(value: Long): Long = value * 1_000_000L

    private companion object {
        const val RATE = 16_000
        const val BLOCK = 1_600
        const val BUFFER_FRAMES = 3_200
    }
}
