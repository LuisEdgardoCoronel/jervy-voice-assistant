package com.luiscoronel.halley.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Nivel del micrófono para diagnóstico: junta bloques de PCM 16 bits y cada [framesPerReport] bloques
 * devuelve un [Report] en dBFS (0 dBFS = escala completa); [flush] da el del período a medias. Solo
 * guarda sumas y máximos, nunca audio (NF4).
 */
class AudioLevel(
    private val framesPerReport: Int,
) {
    /**
     * @property rmsDbfs RMS de todo el período: con silencio, el ruido de fondo.
     * @property maxFrameRmsDbfs RMS del bloque más fuerte: el nivel de la voz.
     * @property peakDbfs muestra más alta: cerca de 0 indica saturación.
     * @property frames bloques que abarca (menos que el período si viene de [flush]).
     */
    data class Report(
        val rmsDbfs: Double,
        val maxFrameRmsDbfs: Double,
        val peakDbfs: Double,
        val frames: Int,
    )

    private var frames = 0
    private var samples = 0L
    private var sumSquares = 0.0
    private var maxFrameRms = 0.0
    private var peak = 0

    init {
        require(framesPerReport > 0) { "framesPerReport debe ser positivo" }
    }

    /** Suma un bloque; devuelve el resumen al completar el período y empieza otro. */
    fun add(frame: ShortArray): Report? {
        var frameSquares = 0.0
        for (sample in frame) {
            val value = sample.toDouble()
            frameSquares += value * value
            peak = max(peak, abs(sample.toInt()))
        }
        if (frame.isNotEmpty()) maxFrameRms = max(maxFrameRms, sqrt(frameSquares / frame.size))
        sumSquares += frameSquares
        samples += frame.size
        frames++
        return if (frames < framesPerReport) null else report()
    }

    /**
     * Devuelve el resumen del período a medias (p. ej. al soltar el micrófono tras una detección) y
     * empieza otro; null si no hubo bloques desde el último resumen.
     */
    fun flush(): Report? = if (frames == 0) null else report()

    private fun report(): Report {
        val overallRms = if (samples > 0) sqrt(sumSquares / samples) else 0.0
        val report = Report(toDbfs(overallRms), toDbfs(maxFrameRms), toDbfs(peak.toDouble()), frames)
        frames = 0
        samples = 0
        sumSquares = 0.0
        maxFrameRms = 0.0
        peak = 0
        return report
    }

    companion object {
        private const val FULL_SCALE = 32_768.0
        private const val DECIBELS = 20.0

        /** Piso de 16 bits (una unidad de amplitud): ≈ −90,3 dBFS. El silencio digital queda acá. */
        val MIN_DBFS = DECIBELS * log10(1 / FULL_SCALE)

        /** Convierte una amplitud (en unidades de PCM 16 bits) a dBFS, con piso en [MIN_DBFS]. */
        fun toDbfs(amplitude: Double): Double = max(MIN_DBFS, DECIBELS * log10(max(amplitude, 1.0) / FULL_SCALE))
    }
}
