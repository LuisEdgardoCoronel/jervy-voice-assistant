package com.luiscoronel.jervy.audio

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.luiscoronel.jervy.core.pipeline.Speaker
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * [Speaker] con el [TextToSpeech] de Android: es-AR si está, si no es-ES, si no cualquier español,
 * si no el idioma por defecto del teléfono. Se crea con [create] (espera la inicialización).
 *
 * Sin ducking todavía (NF6, etapa 2).
 */
class AndroidSpeaker private constructor(
    private val tts: TextToSpeech,
) : Speaker,
    AutoCloseable {
    private class Pending(
        val continuation: CancellableContinuation<Unit>,
        val requestedAt: TimeMark,
    )

    private val pending = ConcurrentHashMap<String, Pending>()

    init {
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {
                    val elapsed = pending[utteranceId]?.requestedAt?.elapsedNow()?.inWholeMilliseconds
                    Log.i(TAG, "TTS empezó a hablar a $elapsed ms de pedirlo")
                }

                override fun onDone(utteranceId: String) {
                    pending.remove(utteranceId)?.continuation?.resume(Unit)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String) {
                    onError(utteranceId, TextToSpeech.ERROR)
                }

                override fun onError(
                    utteranceId: String,
                    errorCode: Int,
                ) {
                    pending
                        .remove(utteranceId)
                        ?.continuation
                        ?.resumeWithException(SpeakerUnavailableException("Error de TTS $errorCode"))
                }
            },
        )
    }

    override suspend fun speak(text: String) =
        suspendCancellableCoroutine { continuation ->
            val id = UUID.randomUUID().toString()
            pending[id] = Pending(continuation, TimeSource.Monotonic.markNow())
            continuation.invokeOnCancellation {
                pending.remove(id)
                tts.stop()
            }
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            if (result != TextToSpeech.SUCCESS) {
                pending.remove(id)
                continuation.resumeWithException(SpeakerUnavailableException("TextToSpeech.speak devolvió $result"))
            }
        }

    override fun close() {
        tts.stop()
        tts.shutdown()
    }

    private fun configure() {
        tts.setAudioAttributes(
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        val locale =
            PREFERRED_LOCALES.firstOrNull { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_COUNTRY_AVAILABLE }
                ?: SPANISH.takeIf { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
        if (locale != null) tts.language = locale
        Log.i(TAG, "TTS: motor=${tts.defaultEngine} idioma=${locale ?: "por defecto"} voz=${tts.voice?.name}")
    }

    companion object {
        private const val TAG = "Jervy"
        private const val INIT_TIMEOUT_MS = 10_000L
        private val PREFERRED_LOCALES = listOf(Locale.forLanguageTag("es-AR"), Locale.forLanguageTag("es-ES"))
        private val SPANISH = Locale.forLanguageTag("es")

        /** Crea el TTS y espera su inicialización. Falla con [SpeakerUnavailableException]. */
        suspend fun create(context: Context): AndroidSpeaker {
            val startedAt = TimeSource.Monotonic.markNow()
            val tts =
                try {
                    withTimeout(INIT_TIMEOUT_MS) { initTextToSpeech(context.applicationContext) }
                } catch (e: TimeoutCancellationException) {
                    throw SpeakerUnavailableException("El TTS no se inicializó en $INIT_TIMEOUT_MS ms", e)
                }
            Log.i(TAG, "TTS inicializado en ${startedAt.elapsedNow().inWholeMilliseconds} ms")
            return AndroidSpeaker(tts).also { it.configure() }
        }

        private suspend fun initTextToSpeech(context: Context): TextToSpeech =
            suspendCancellableCoroutine { continuation ->
                var created: TextToSpeech? = null
                val listener =
                    TextToSpeech.OnInitListener { status ->
                        // Sin motor de TTS, Android llama a este listener dentro del constructor
                        // (created todavía es null): en ese caso solo puede ser un error.
                        val tts = created
                        if (status == TextToSpeech.SUCCESS && tts != null) {
                            continuation.resume(tts)
                        } else {
                            tts?.shutdown()
                            continuation.resumeWithException(
                                SpeakerUnavailableException("TextToSpeech no se inicializó (status=$status)"),
                            )
                        }
                    }
                val tts = TextToSpeech(context, listener)
                created = tts
                continuation.invokeOnCancellation { tts.shutdown() }
            }
    }
}
