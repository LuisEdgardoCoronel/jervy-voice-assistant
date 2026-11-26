package com.luiscoronel.halley.audio

import android.util.Log
import com.luiscoronel.halley.core.pipeline.KeywordSpotter
import org.vosk.Model
import org.vosk.Recognizer
import java.io.IOException

/**
 * [KeywordSpotter] con Vosk y una gramática restringida a la palabra clave.
 *
 * "Halley" se pronuncia "jeili". El vocabulario del modelo tiene `hailey`, `haley`, `hayley` y
 * `halley`, pero no trae sus pronunciaciones (no se sabe si suenan "jeili" o "ailei"): la gramática
 * de diagnóstico ([KEYWORDS]) prueba esas cuatro y tres pares de palabras ("hey li"…), y el log dice
 * cuál detectó; después se achica a las que funcionen. `[unk]` absorbe todo lo demás. Detecta sobre
 * el resultado parcial (menos latencia) y también sobre el final, si el parcial no la mostró.
 *
 * Con esa gramática Vosk solo puede devolver esas palabras o `[unk]`: los logs no traen lo que
 * dijo el usuario (NF4).
 *
 * Es dueño del [Model]: [close] cierra el reconocedor y el modelo. No es thread-safe: [accept],
 * [reset] y [close] no pueden correr a la vez.
 */
class VoskKeywordSpotter private constructor(
    private val model: Model,
    private val recognizer: Recognizer,
) : KeywordSpotter {
    private var blocks = 0
    private var totalNanos = 0L
    private var maxNanos = 0L

    override fun accept(samples: ShortArray): String? {
        val startedAt = System.nanoTime()
        val keyword =
            if (recognizer.acceptWaveForm(samples, samples.size)) {
                val text = VoskResultParser.text(recognizer.result)
                if (text.isNotEmpty()) Log.i(TAG, "Vosk final: \"$text\"")
                VoskResultParser.findKeyword(text, KEYWORDS)
            } else {
                VoskResultParser.findKeyword(VoskResultParser.text(recognizer.partialResult), KEYWORDS)
            }
        recordBlockTime(System.nanoTime() - startedAt)
        return keyword
    }

    override fun reset() {
        recognizer.reset()
    }

    override fun close() {
        recognizer.close()
        model.close()
    }

    /** Tiempo de proceso por bloque de 100 ms, resumido cada [BLOCKS_PER_REPORT] bloques (NF1). */
    private fun recordBlockTime(nanos: Long) {
        blocks++
        totalNanos += nanos
        maxNanos = maxOf(maxNanos, nanos)
        if (blocks == BLOCKS_PER_REPORT) {
            Log.i(TAG, "Vosk: bloque prom ${totalNanos / blocks / NANOS_PER_MS} ms, máx ${maxNanos / NANOS_PER_MS} ms")
            blocks = 0
            totalNanos = 0
            maxNanos = 0
        }
    }

    companion object {
        private const val TAG = "Halley"
        private const val BLOCKS_PER_REPORT = 100
        private const val NANOS_PER_MS = 1_000_000L

        // Diagnóstico: las de una palabra son las preferidas; las de dos se miden (mismo riesgo que "jer vi").
        val KEYWORDS = listOf("hailey", "haley", "hayley", "halley", "hey li", "hey lee", "hey ley")
        private val GRAMMAR = (KEYWORDS + "[unk]").joinToString(",", "[", "]") { "\"$it\"" }

        /** Crea el reconocedor sobre [model]. Si falla, cierra el modelo y lanza [ModelUnavailableException]. */
        fun create(
            model: Model,
            sampleRate: Int,
        ): VoskKeywordSpotter =
            try {
                VoskKeywordSpotter(model, Recognizer(model, sampleRate.toFloat(), GRAMMAR))
            } catch (e: IOException) {
                model.close()
                throw ModelUnavailableException("No se pudo crear el reconocedor de Vosk", e)
            }
    }
}
