package com.luiscoronel.halley.audio

/**
 * Tiempo de proceso por bloque de audio de un motor de palabra clave (NF1): junta [blocksPerReport]
 * mediciones y devuelve el promedio y el máximo. No es thread-safe (lo usa un solo motor).
 */
class BlockTimer(
    private val blocksPerReport: Int = DEFAULT_BLOCKS_PER_REPORT,
) {
    data class Report(
        val averageMs: Long,
        val maxMs: Long,
    )

    private var blocks = 0
    private var totalNanos = 0L
    private var maxNanos = 0L

    init {
        require(blocksPerReport > 0) { "blocksPerReport debe ser positivo" }
    }

    /** Suma la medición de un bloque; devuelve el resumen al completar el período y empieza otro. */
    fun record(nanos: Long): Report? {
        blocks++
        totalNanos += nanos
        maxNanos = maxOf(maxNanos, nanos)
        if (blocks < blocksPerReport) return null
        val report = Report(totalNanos / blocks / NANOS_PER_MS, maxNanos / NANOS_PER_MS)
        blocks = 0
        totalNanos = 0
        maxNanos = 0
        return report
    }

    companion object {
        /** 100 bloques de 100 ms: un resumen cada 10 s. */
        const val DEFAULT_BLOCKS_PER_REPORT = 100
        private const val NANOS_PER_MS = 1_000_000L
    }
}
