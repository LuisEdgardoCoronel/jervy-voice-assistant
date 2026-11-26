package com.luiscoronel.halley.audio

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.delay

/**
 * Señal sonora corta al detectar la palabra clave (R2). Sale por el stream de música para que
 * suene por los auriculares. Si el teléfono no puede crear el tono, se omite (no es fatal).
 */
class BeepPlayer : AutoCloseable {
    private val tone: ToneGenerator? =
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se pudo crear el ToneGenerator; sin beep", e)
            null
        }

    /** Suena el beep y vuelve cuando terminó, para que no se superponga con la voz. */
    suspend fun beep() {
        val generator = tone ?: return
        generator.startTone(ToneGenerator.TONE_PROP_BEEP, DURATION_MS)
        delay(DURATION_MS.toLong())
    }

    override fun close() {
        tone?.release()
    }

    private companion object {
        const val VOLUME = 80
        const val DURATION_MS = 150
        const val TAG = "Halley"
    }
}
