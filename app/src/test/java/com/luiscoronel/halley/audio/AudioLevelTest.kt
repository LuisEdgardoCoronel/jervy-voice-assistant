package com.luiscoronel.halley.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class AudioLevelTest {
    @Test
    fun `silencio digital queda en el piso`() {
        val report = AudioLevel(1).add(ShortArray(1600))!!
        assertEquals(AudioLevel.MIN_DBFS, report.rmsDbfs, DELTA)
        assertEquals(AudioLevel.MIN_DBFS, report.maxFrameRmsDbfs, DELTA)
        assertEquals(AudioLevel.MIN_DBFS, report.peakDbfs, DELTA)
        assertEquals(-90.3, AudioLevel.MIN_DBFS, 0.1)
    }

    @Test
    fun `senoidal a escala completa da unos -3 dBFS de RMS y 0 de pico`() {
        val report = AudioLevel(1).add(sine(Short.MAX_VALUE.toInt()))!!
        assertEquals(-3.0, report.rmsDbfs, 0.05)
        assertEquals(0.0, report.peakDbfs, 0.01)
    }

    @Test
    fun `senoidal a la decima parte baja 20 dB`() {
        val report = AudioLevel(1).add(sine(3277))!!
        assertEquals(-23.0, report.rmsDbfs, 0.05)
        assertEquals(-20.0, report.peakDbfs, 0.01)
    }

    @Test
    fun `saturacion negativa cuenta como pico de 0 dBFS`() {
        val report = AudioLevel(1).add(ShortArray(1600) { Short.MIN_VALUE })!!
        assertEquals(0.0, report.rmsDbfs, DELTA)
        assertEquals(0.0, report.peakDbfs, DELTA)
    }

    @Test
    fun `resume cada N bloques y separa el fondo del bloque mas fuerte`() {
        val level = AudioLevel(3)
        assertNull(level.add(ShortArray(1600)))
        assertNull(level.add(sine(3277)))
        val report = level.add(ShortArray(1600))!!
        // Un bloque de voz entre dos de silencio: el promedio baja 10·log10(3) ≈ 4,8 dB.
        assertEquals(-27.8, report.rmsDbfs, 0.05)
        assertEquals(-23.0, report.maxFrameRmsDbfs, 0.05)
    }

    @Test
    fun `despues del resumen empieza un periodo nuevo`() {
        val level = AudioLevel(1)
        level.add(sine(Short.MAX_VALUE.toInt()))
        val report = level.add(ShortArray(1600))!!
        assertEquals(AudioLevel.MIN_DBFS, report.peakDbfs, DELTA)
    }

    @Test
    fun `flush da el periodo a medias y empieza otro`() {
        val level = AudioLevel(50)
        level.add(ShortArray(1600))
        level.add(sine(3277))
        val report = level.flush()!!
        assertEquals(2, report.frames)
        assertEquals(-23.0, report.maxFrameRmsDbfs, 0.05)
        assertNull(level.flush())
    }

    @Test
    fun `flush sin bloques o justo despues de un resumen devuelve null`() {
        val level = AudioLevel(1)
        assertNull(level.flush())
        val report = level.add(sine(3277))!!
        assertEquals(1, report.frames)
        assertNull(level.flush())
    }

    @Test
    fun `bloque vacio no rompe`() {
        val report = AudioLevel(1).add(ShortArray(0))!!
        assertEquals(AudioLevel.MIN_DBFS, report.rmsDbfs, DELTA)
    }

    private fun sine(amplitude: Int): ShortArray =
        // 400 Hz a 16 kHz: 40 muestras por ciclo, 40 ciclos completos en 1600 muestras.
        ShortArray(1600) { (amplitude * sin(2 * PI * it / 40)).roundToInt().toShort() }

    private companion object {
        const val DELTA = 1e-6
    }
}
