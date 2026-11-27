package com.luiscoronel.halley.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmTest {
    @Test
    fun `convierte los extremos y el cero al rango -1 a 1`() {
        val samples = shortArrayOf(0, Short.MIN_VALUE, Short.MAX_VALUE, 16_384, -16_384)
        assertArrayEquals(floatArrayOf(0f, -1f, 32_767f / 32_768f, 0.5f, -0.5f), samples.toFloatPcm(), 0f)
    }

    @Test
    fun `bloque vacio da arreglo vacio`() {
        assertEquals(0, ShortArray(0).toFloatPcm().size)
    }
}
