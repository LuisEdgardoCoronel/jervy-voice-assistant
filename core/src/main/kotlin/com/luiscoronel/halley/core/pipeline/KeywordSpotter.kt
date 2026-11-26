package com.luiscoronel.halley.core.pipeline

/**
 * Motor de palabra clave (Vosk, sherpa-onnx, …). Recibe bloques de audio y avisa cuando reconoce la palabra.
 *
 * No es thread-safe: se usa desde una sola colección a la vez.
 */
interface KeywordSpotter : AutoCloseable {
    /** Procesa un bloque de audio. Devuelve la palabra clave reconocida, o null si no hubo detección. */
    fun accept(samples: ShortArray): String?

    /** Descarta el estado interno (el audio acumulado), p. ej. después de una detección. */
    fun reset()
}
