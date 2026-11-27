package com.luiscoronel.halley.audio

/**
 * Lee los resultados JSON de Vosk (`{"partial" : "..."}` o `{"text" : "..."}`) y busca la palabra clave.
 *
 * Parseo mínimo propio: solo hacen falta esos dos campos, y el `org.json` de Android no funciona
 * en los tests de JVM.
 */
object VoskResultParser {
    private val FIELD = Regex(""""(?:partial|text)"\s*:\s*"((?:[^"\\]|\\.)*)"""")
    private const val UNKNOWN = "[unk]"

    /** El texto reconocido (parcial o final), o "" si el JSON no lo trae. */
    fun text(json: String): String =
        FIELD
            .find(json)
            ?.groupValues
            ?.get(1)
            ?.trim()
            .orEmpty()

    /** Una palabra del resultado final con `setWords(true)`: confianza 0–1 y tiempos en segundos. */
    data class Word(
        val word: String,
        val conf: Float,
        val start: Float,
        val end: Float,
    )

    // Cada palabra es un objeto sin llaves adentro; el objeto de afuera no matchea porque las contiene.
    private val WORD_OBJECT = Regex("""\{[^{}]*\}""")
    private val WORD_FIELD = Regex(""""word"\s*:\s*"((?:[^"\\]|\\.)*)"""")

    private fun number(
        obj: String,
        name: String,
    ): Float? =
        Regex(""""$name"\s*:\s*(-?[0-9.]+(?:[eE][-+]?[0-9]+)?)""")
            .find(obj)
            ?.groupValues
            ?.get(1)
            ?.toFloatOrNull()

    /** Las palabras del arreglo `result` de un final. Las que no traen los cuatro campos se ignoran. */
    fun words(json: String): List<Word> =
        WORD_OBJECT
            .findAll(json)
            .mapNotNull { match ->
                val obj = match.value
                val word = WORD_FIELD.find(obj)?.groupValues?.get(1) ?: return@mapNotNull null
                val conf = number(obj, "conf") ?: return@mapNotNull null
                val start = number(obj, "start") ?: return@mapNotNull null
                val end = number(obj, "end") ?: return@mapNotNull null
                Word(word, conf, start, end)
            }.toList()

    /**
     * La primera de [keywords] que aparece en [text] como palabras completas y seguidas,
     * ignorando `[unk]` entre medio. Null si no hay ninguna.
     */
    fun findKeyword(
        text: String,
        keywords: List<String>,
    ): String? {
        val words = text.split(' ').filter { it.isNotEmpty() && it != UNKNOWN }
        return keywords.firstOrNull { keyword ->
            val target = keyword.split(' ').filter { it.isNotEmpty() }
            target.isNotEmpty() && words.windowed(target.size).any { it == target }
        }
    }
}
