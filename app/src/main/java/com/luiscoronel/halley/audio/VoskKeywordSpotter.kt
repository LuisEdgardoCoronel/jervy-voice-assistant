package com.luiscoronel.halley.audio

import android.util.Log
import com.luiscoronel.halley.core.pipeline.KeywordSpotter
import org.vosk.Model
import org.vosk.Recognizer
import java.io.IOException
import java.util.Locale

/**
 * [KeywordSpotter] con Vosk y una gramática restringida a la palabra clave.
 *
 * "Halley" se pronuncia "jeili". El vocabulario del modelo tiene `hailey`, `haley`, `hayley` y
 * `halley`, pero no trae sus pronunciaciones (no se sabe si suenan "jeili" o "ailei"): la gramática
 * de diagnóstico ([KEYWORDS]) prueba esas cuatro y tres pares de palabras ("hey li"…), y el log dice
 * cuál detectó; después se achica a las que funcionen. `[unk]` absorbe todo lo demás.
 *
 * Spike (ronda E, rama spike/wake-word): palabra clave "morgana" con palabras distractoras en la
 * gramática, para que las frases parecidas tengan a dónde ir. Detecta solo sobre el resultado final,
 * con la confianza por palabra (`setWords(true)`) y [MIN_CONFIDENCE].
 *
 * Con gramática restringida Vosk solo puede devolver esas palabras o `[unk]`: los logs no traen
 * otra cosa de lo que dijo el usuario (NF4).
 *
 * Es dueño del [Model]: [close] cierra el reconocedor y el modelo. No es thread-safe: [accept],
 * [reset] y [close] no pueden correr a la vez.
 */
class VoskKeywordSpotter private constructor(
    private val model: Model,
    private val recognizer: Recognizer,
    private val sampleRate: Int,
) : KeywordSpotter {
    private val timer = BlockTimer()

    // Audio entregado a Vosk, para medir la latencia del final (ronda E). Los tiempos de las palabras
    // pueden contar desde el último reset o desde que se creó el reconocedor: se loguean los dos.
    private var samplesSinceReset = 0L
    private var samplesTotal = 0L

    override fun accept(samples: ShortArray): String? {
        val startedAt = System.nanoTime()
        samplesSinceReset += samples.size
        samplesTotal += samples.size
        // Ronda E: se detecta solo sobre el final, que trae la confianza de cada palabra.
        val keyword = if (recognizer.acceptWaveForm(samples, samples.size)) onFinal(recognizer.result) else null
        timer.record(System.nanoTime() - startedAt)?.let {
            Log.i(TAG, "Vosk: bloque prom ${it.averageMs} ms, máx ${it.maxMs} ms")
        }
        return keyword
    }

    private fun onFinal(json: String): String? {
        val text = VoskResultParser.text(json)
        if (text.isEmpty()) return null
        val words = VoskResultParser.words(json)
        val confs = words.joinToString(", ") { "${it.word} ${"%.2f".format(Locale.ROOT, it.conf)}" }
        Log.i(TAG, "Vosk final: \"$text\" [$confs]")
        val hit = words.firstOrNull { it.word in KEYWORDS } ?: return null
        val endMs = (hit.end * 1000).toLong()
        Log.i(
            TAG,
            "Vosk: ${hit.word} conf ${"%.2f".format(Locale.ROOT, hit.conf)}, fin ${hit.end} s, " +
                "latencia ${samplesSinceReset * 1000 / sampleRate - endMs} ms (desde reset) / " +
                "${samplesTotal * 1000 / sampleRate - endMs} ms (total)",
        )
        return if (hit.conf >= MIN_CONFIDENCE) hit.word else null
    }

    override fun reset() {
        recognizer.reset()
        samplesSinceReset = 0
    }

    override fun close() {
        recognizer.close()
        model.close()
    }

    companion object {
        private const val TAG = "Halley"

        val KEYWORDS = listOf("morgana")

        /** Ronda E: confianza mínima de la palabra clave en el final. 0 = calibración (todo dispara). */
        const val MIN_CONFIDENCE = 0f
        private val DISTRACTORS =
            listOf(
                "morgan",
                "mañana",
                "gana",
                "ganas",
                "ganamos",
                "amor",
                "órgano",
                "órganos",
                "organizamos",
                "margarita",
            )
        private val GRAMMAR = (KEYWORDS + DISTRACTORS + "[unk]").joinToString(",", "[", "]") { "\"$it\"" }

        /** Crea el reconocedor sobre [model]. Si falla, cierra el modelo y lanza [ModelUnavailableException]. */
        fun create(
            model: Model,
            sampleRate: Int,
        ): VoskKeywordSpotter =
            try {
                val recognizer = Recognizer(model, sampleRate.toFloat(), GRAMMAR)
                recognizer.setWords(true)
                VoskKeywordSpotter(model, recognizer, sampleRate)
            } catch (e: IOException) {
                model.close()
                throw ModelUnavailableException("No se pudo crear el reconocedor de Vosk", e)
            }
    }
}
