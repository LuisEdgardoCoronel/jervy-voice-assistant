package com.luiscoronel.halley.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockTimerTest {
    @Test
    fun `resume cada N bloques con promedio y maximo en ms`() {
        val timer = BlockTimer(3)
        assertNull(timer.record(10 * MS))
        assertNull(timer.record(20 * MS))
        assertEquals(BlockTimer.Report(averageMs = 20, maxMs = 30), timer.record(30 * MS))
    }

    @Test
    fun `despues del resumen empieza un periodo nuevo`() {
        val timer = BlockTimer(1)
        timer.record(90 * MS)
        assertEquals(BlockTimer.Report(averageMs = 5, maxMs = 5), timer.record(5 * MS))
    }

    @Test
    fun `por defecto resume cada 100 bloques`() {
        val timer = BlockTimer()
        repeat(BlockTimer.DEFAULT_BLOCKS_PER_REPORT - 1) { assertNull(timer.record(MS)) }
        assertEquals(BlockTimer.Report(averageMs = 1, maxMs = 1), timer.record(MS))
    }

    private companion object {
        const val MS = 1_000_000L
    }
}
