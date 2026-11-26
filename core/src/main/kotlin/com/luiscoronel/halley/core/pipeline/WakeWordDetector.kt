package com.luiscoronel.halley.core.pipeline

import kotlinx.coroutines.flow.Flow
import kotlin.time.ComparableTimeMark

/** Primera etapa del pipeline: espera la palabra clave (R2). */
fun interface WakeWordDetector {
    /**
     * Emite una [WakeWordDetection] por cada vez que se reconoce la palabra clave.
     *
     * El Flow es frío: el micrófono se usa solo mientras se colecta. Para no escuchar mientras
     * Halley habla, tomar la primera detección (`first()`) y volver a colectar después.
     */
    fun detections(): Flow<WakeWordDetection>
}

/**
 * @property keyword la palabra clave tal como la reportó el motor (p. ej. "jer vi").
 * @property detectedAt momento de la detección, para medir latencias por etapa (NF1).
 */
data class WakeWordDetection(
    val keyword: String,
    val detectedAt: ComparableTimeMark,
)
