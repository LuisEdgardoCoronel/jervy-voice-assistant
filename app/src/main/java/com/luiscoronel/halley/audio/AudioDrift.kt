package com.luiscoronel.halley.audio

/**
 * Diagnóstico de pérdida de audio del micrófono: compara el audio leído contra el reloj real desde que
 * se abrió el micrófono y, si el teléfono lo da, contra lo que capturó el hardware.
 *
 * - **Atraso** = reloj transcurrido − duración del audio leído. Su mínimo en un período es la base
 *   (la latencia de arranque); su máximo, cuánto se acumuló en el búfer mientras el consumidor tardaba.
 *   Si se pierde audio, la base sube y no vuelve a bajar en los períodos siguientes.
 * - **Pendiente en el búfer** = muestras capturadas por el hardware − muestras leídas. Si supera
 *   el tamaño del búfer, hubo desborde.
 *
 * Cada [blocksPerReport] lecturas devuelve un [Report]; [flush] da el del período a medias. Solo
 * cuenta muestras y tiempos, nunca guarda audio (NF4).
 */
class AudioDrift(
    private val sampleRate: Int,
    private val bufferFrames: Int,
    private val blocksPerReport: Int,
) {
    /**
     * @property audioMs audio leído desde que se abrió el micrófono.
     * @property clockMs reloj transcurrido desde que se abrió el micrófono, en la última lectura.
     * @property minLagMs atraso mínimo del período (la base).
     * @property maxLagMs atraso máximo del período.
     * @property maxBacklogMs máximo pendiente en el búfer según el hardware; null si no hubo datos.
     * @property bufferMs tamaño del búfer.
     * @property blocks lecturas que abarca el período.
     */
    data class Report(
        val audioMs: Long,
        val clockMs: Long,
        val minLagMs: Long,
        val maxLagMs: Long,
        val maxBacklogMs: Long?,
        val bufferMs: Long,
        val blocks: Int,
    ) {
        /** El hardware tuvo más pendiente que lo que entra en el búfer: se perdió audio. */
        val overflow: Boolean get() = maxBacklogMs != null && maxBacklogMs > bufferMs
    }

    private var totalFrames = 0L
    private var lastElapsedNanos = 0L
    private var blocks = 0
    private var minLagNanos = Long.MAX_VALUE
    private var maxLagNanos = Long.MIN_VALUE
    private var maxBacklogFrames: Long? = null

    init {
        require(sampleRate > 0) { "sampleRate debe ser positivo" }
        require(bufferFrames > 0) { "bufferFrames debe ser positivo" }
        require(blocksPerReport > 0) { "blocksPerReport debe ser positivo" }
    }

    /**
     * Registra una lectura.
     *
     * @param framesRead muestras que devolvió esta lectura.
     * @param elapsedNanos reloj monotónico desde que se abrió el micrófono, justo después de leer.
     * @param hardwareFrames muestras capturadas por el hardware hasta ese momento; null si no se sabe.
     */
    fun add(
        framesRead: Int,
        elapsedNanos: Long,
        hardwareFrames: Long?,
    ): Report? {
        totalFrames += framesRead
        lastElapsedNanos = elapsedNanos
        val lag = elapsedNanos - framesToNanos(totalFrames)
        minLagNanos = minOf(minLagNanos, lag)
        maxLagNanos = maxOf(maxLagNanos, lag)
        if (hardwareFrames != null) {
            val backlog = hardwareFrames - totalFrames
            maxBacklogFrames = maxBacklogFrames?.let { maxOf(it, backlog) } ?: backlog
        }
        blocks++
        return if (blocks < blocksPerReport) null else report()
    }

    /** Devuelve el resumen del período a medias y empieza otro; null si no hubo lecturas. */
    fun flush(): Report? = if (blocks == 0) null else report()

    private fun report(): Report {
        val report =
            Report(
                audioMs = framesToNanos(totalFrames) / NANOS_PER_MILLI,
                clockMs = lastElapsedNanos / NANOS_PER_MILLI,
                minLagMs = minLagNanos / NANOS_PER_MILLI,
                maxLagMs = maxLagNanos / NANOS_PER_MILLI,
                maxBacklogMs = maxBacklogFrames?.let { framesToNanos(it) / NANOS_PER_MILLI },
                bufferMs = framesToNanos(bufferFrames.toLong()) / NANOS_PER_MILLI,
                blocks = blocks,
            )
        blocks = 0
        minLagNanos = Long.MAX_VALUE
        maxLagNanos = Long.MIN_VALUE
        maxBacklogFrames = null
        return report
    }

    private fun framesToNanos(frames: Long): Long = frames * NANOS_PER_SECOND / sampleRate

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
