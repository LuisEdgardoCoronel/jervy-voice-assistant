package com.luiscoronel.jervy.audio

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
