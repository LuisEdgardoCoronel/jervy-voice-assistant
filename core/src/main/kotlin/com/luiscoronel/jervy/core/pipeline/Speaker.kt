package com.luiscoronel.jervy.core.pipeline

/** Última etapa del pipeline: respuesta por voz. */
fun interface Speaker {
    /** Dice [text] y vuelve recién cuando terminó de hablar. Si se cancela, deja de hablar. */
    suspend fun speak(text: String)
}
