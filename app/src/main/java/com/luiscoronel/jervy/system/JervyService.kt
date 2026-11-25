package com.luiscoronel.jervy.system

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.ServiceCompat
import com.luiscoronel.jervy.AppContainer
import com.luiscoronel.jervy.JervyApplication
import com.luiscoronel.jervy.R
import com.luiscoronel.jervy.audio.AndroidSpeaker
import com.luiscoronel.jervy.audio.BeepPlayer
import com.luiscoronel.jervy.audio.MicrophoneUnavailableException
import com.luiscoronel.jervy.audio.SpeakerUnavailableException
import com.luiscoronel.jervy.core.pipeline.KeywordSpotter
import com.luiscoronel.jervy.core.pipeline.SpottingWakeWordDetector
import com.luiscoronel.jervy.core.pipeline.WakeWordDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Foreground service de tipo micrófono (R1, R2).
 *
 * Con estado ACTIVE corre el bucle: esperar la palabra clave → beep → "Hola" → volver a esperar.
 * El micrófono se libera al detectar (no se escucha mientras Jervy habla) y al pausar.
 *
 * Pausar no detiene el servicio: lo deja vivo y suelta el micrófono, porque Android 14+
 * restringe arrancar un servicio de micrófono desde segundo plano.
 *
 * Es el dueño de las piezas del pipeline: las cierra en [onDestroy]. Ante un error fatal avisa
 * con una notificación y por voz ("Error, me apago") y se detiene.
 */
class JervyService : Service() {
    private val container: AppContainer
        get() = (application as JervyApplication).container

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false
    private var pipeline: Pipeline? = null
    private var listeningJob: Job? = null

    private class Pipeline(
        val detector: WakeWordDetector,
        val spotter: KeywordSpotter,
        val speaker: AndroidSpeaker,
        val beep: BeepPlayer,
    ) : AutoCloseable {
        override fun close() {
            speaker.close()
            beep.close()
            spotter.close()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action=$action")
        when (action) {
            ACTION_START -> start()
            ACTION_PAUSE -> ifStarted { pause() }
            ACTION_RESUME -> ifStarted { resume() }
            ACTION_REPOST -> ifStarted { JervyNotification.update(this, container.serviceStatus.value) }
            ACTION_STOP -> stop()
            else -> ifStarted {}
        }
        // Sin reinicio automático: un reinicio desde segundo plano no puede volver a tomar el micrófono
        // en Android 14+. La estrategia de supervivencia se define en el paso 1.3.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        val lastListening = listeningJob
        val lastPipeline = pipeline
        pipeline = null
        scope.cancel()
        // El motor no es thread-safe: se cierra recién cuando terminó el último bloque en proceso.
        if (lastPipeline != null) {
            CoroutineScope(Dispatchers.Main).launch {
                lastListening?.join()
                lastPipeline.close()
                Log.d(TAG, "Pipeline cerrado")
            }
        }
        container.updateServiceStatus(ServiceStatus.STOPPED)
        Log.d(TAG, "Servicio destruido")
        super.onDestroy()
    }

    private fun start() {
        if (started) return
        JervyNotification.createChannels(this)
        ServiceCompat.startForeground(
            this,
            JervyNotification.NOTIFICATION_ID,
            JervyNotification.build(this, ServiceStatus.ACTIVE),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        started = true
        container.updateServiceStatus(ServiceStatus.ACTIVE)
        Log.d(TAG, "Servicio en primer plano")
        scope.launch { setUpPipeline() }
    }

    private suspend fun setUpPipeline() {
        val speaker =
            try {
                container.createSpeaker()
            } catch (e: SpeakerUnavailableException) {
                fail(e, speaker = null)
                return
            }
        val spotter = container.createKeywordSpotter()
        pipeline =
            Pipeline(
                detector = SpottingWakeWordDetector(container.createAudioSource(), spotter),
                spotter = spotter,
                speaker = speaker,
                beep = container.createBeepPlayer(),
            )
        if (container.serviceStatus.value == ServiceStatus.ACTIVE) startListening()
    }

    private fun startListening() {
        // Si el pipeline todavía se está preparando, setUpPipeline() arranca la escucha al terminar.
        val current = pipeline ?: return
        if (listeningJob?.isActive == true) return
        val previous = listeningJob
        listeningJob =
            scope.launch {
                // Pausar y reanudar rápido: esperar a que la escucha anterior suelte el motor.
                previous?.join()
                try {
                    listenLoop(current)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fail(e, current.speaker)
                }
            }
    }

    private suspend fun listenLoop(pipeline: Pipeline) {
        while (true) {
            // first() libera el micrófono apenas detecta. Default: el motor no corre en el hilo principal.
            val detection = withContext(Dispatchers.Default) { pipeline.detector.detections().first() }
            Log.i(TAG, "Palabra clave detectada: ${detection.keyword}")
            pipeline.beep.beep()
            Log.d(TAG, "Beep terminado a ${detection.detectedAt.elapsedNow().inWholeMilliseconds} ms de la detección")
            try {
                pipeline.speaker.speak(getString(R.string.tts_hello))
                Log.d(TAG, "Respuesta terminada a ${detection.detectedAt.elapsedNow().inWholeMilliseconds} ms de la detección")
            } catch (e: SpeakerUnavailableException) {
                // Una respuesta que falla no es fatal: se vuelve a escuchar.
                Log.w(TAG, "No se pudo decir la respuesta", e)
            }
        }
    }

    private fun pause() {
        listeningJob?.cancel()
        setStatus(ServiceStatus.PAUSED)
    }

    private fun resume() {
        setStatus(ServiceStatus.ACTIVE)
        startListening()
    }

    /** Error fatal: avisa con una notificación y por voz (si el TTS anda) y detiene el servicio. */
    private suspend fun fail(
        error: Throwable,
        speaker: AndroidSpeaker?,
    ) {
        Log.e(TAG, "Error fatal, me apago", error)
        JervyNotification.showError(this, getString(messageFor(error)))
        if (speaker != null) {
            try {
                withTimeout(ERROR_SPEECH_TIMEOUT_MS) { speaker.speak(getString(R.string.tts_error)) }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "El aviso de error por voz no terminó a tiempo", e)
            } catch (e: SpeakerUnavailableException) {
                Log.w(TAG, "No se pudo avisar el error por voz", e)
            }
        }
        stop()
    }

    @StringRes
    private fun messageFor(error: Throwable): Int =
        when (error) {
            is MicrophoneUnavailableException -> R.string.error_microphone
            is SpeakerUnavailableException -> R.string.error_speaker
            else -> R.string.error_generic
        }

    private fun setStatus(status: ServiceStatus) {
        container.updateServiceStatus(status)
        JervyNotification.update(this, status)
        Log.d(TAG, "Estado: $status")
    }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Una acción que llega sin que el servicio esté en primer plano (p. ej. el proceso murió) lo cierra. */
    private inline fun ifStarted(block: () -> Unit) {
        if (started) block() else stopSelf()
    }

    companion object {
        private const val TAG = "Jervy"
        private const val ERROR_SPEECH_TIMEOUT_MS = 5_000L
        const val ACTION_START = "com.luiscoronel.jervy.action.START"
        const val ACTION_PAUSE = "com.luiscoronel.jervy.action.PAUSE"
        const val ACTION_RESUME = "com.luiscoronel.jervy.action.RESUME"
        const val ACTION_STOP = "com.luiscoronel.jervy.action.STOP"
        const val ACTION_REPOST = "com.luiscoronel.jervy.action.REPOST"
    }
}
