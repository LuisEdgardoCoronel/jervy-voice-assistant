package com.luiscoronel.halley.core.pipeline

import kotlinx.coroutines.flow.Flow

/**
 * Fuente de audio: bloques PCM 16 bits, mono, 16 kHz.
 *
 * El Flow es frío: abre el micrófono al empezar a colectarlo y lo libera al cancelarse,
 * terminar o fallar. El audio nunca se guarda (NF4).
 */
fun interface AudioSource {
    fun frames(): Flow<ShortArray>
}
