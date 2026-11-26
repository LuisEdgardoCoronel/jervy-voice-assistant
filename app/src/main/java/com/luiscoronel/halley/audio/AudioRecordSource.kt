package com.luiscoronel.halley.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.content.ContextCompat
import com.luiscoronel.halley.core.pipeline.AudioSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.time.TimeSource

/**
 * Micrófono del teléfono vía [AudioRecord]: PCM 16 bits, mono, 16 kHz, bloques de 100 ms.
 *
 * La fuente ([MicSource]) es VOICE_RECOGNITION salvo en el diagnóstico del 1.2b.3c. No activa SCO:
 * el micrófono Bluetooth no se usa, así la música sigue en estéreo (A2DP). El [AudioRecord] se crea al
 * colectar y se libera al cancelar o fallar. El audio nunca se guarda (NF4): para diagnóstico solo se
 * loguea su nivel ([AudioLevel]) cada [LEVEL_REPORT_FRAMES] bloques.
 */
class AudioRecordSource(
    private val context: Context,
    private val source: MicSource = MicSource.VOICE_RECOGNITION,
) : AudioSource {
    /** Fuentes de audio que se pueden elegir al compilar (`-PmicSource`, diagnóstico del 1.2b.3c). */
    enum class MicSource(
        val audioSource: Int,
    ) {
        VOICE_RECOGNITION(MediaRecorder.AudioSource.VOICE_RECOGNITION),
        MIC(MediaRecorder.AudioSource.MIC),
        UNPROCESSED(MediaRecorder.AudioSource.UNPROCESSED),
    }

    override fun frames(): Flow<ShortArray> =
        flow {
            val record = openRecord()
            try {
                val openedAt = TimeSource.Monotonic.markNow()
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    throw MicrophoneUnavailableException("AudioRecord no empezó a grabar (¿otra app tiene el micrófono?)")
                }
                Log.i(TAG, "Micrófono abierto en ${openedAt.elapsedNow().inWholeMilliseconds} ms")
                logInputInfo(record)
                val level = AudioLevel(LEVEL_REPORT_FRAMES)
                while (currentCoroutineContext().isActive) {
                    val frame = ShortArray(FRAME_SIZE)
                    val read = record.read(frame, 0, FRAME_SIZE)
                    if (read < 0) throw MicrophoneUnavailableException("AudioRecord.read devolvió $read")
                    if (read > 0) {
                        val block = if (read == FRAME_SIZE) frame else frame.copyOf(read)
                        level.add(block)?.let { logLevel(it) }
                        emit(block)
                    }
                }
            } finally {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
                record.release()
                Log.d(TAG, "Micrófono liberado")
            }
        }.flowOn(Dispatchers.IO)

    private fun openRecord(): AudioRecord {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) throw MicrophoneUnavailableException("Falta el permiso RECORD_AUDIO")
        if (source == MicSource.UNPROCESSED && !supportsUnprocessed()) {
            throw MicrophoneUnavailableException("El teléfono no soporta la fuente UNPROCESSED")
        }

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) throw MicrophoneUnavailableException("getMinBufferSize devolvió $minBuffer")

        val record =
            AudioRecord(
                source.audioSource,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                maxOf(minBuffer, FRAME_SIZE * BYTES_PER_SAMPLE * 2),
            )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw MicrophoneUnavailableException("AudioRecord no se inicializó")
        }
        return record
    }

    private fun supportsUnprocessed(): Boolean {
        val audioManager = context.getSystemService(AudioManager::class.java)
        return audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
    }

    /** Diagnóstico (1.2b.3c): fuente, micrófono que la sirve y efectos de preprocesamiento que ofrece el teléfono. */
    private fun logInputInfo(record: AudioRecord) {
        val device = record.routedDevice
        Log.i(
            TAG,
            "Micrófono: fuente $source, dispositivo ${device?.productName} (tipo ${device?.type}), " +
                "UNPROCESSED soportada ${supportsUnprocessed()}, " +
                "disponibles AGC ${AutomaticGainControl.isAvailable()}, NS ${NoiseSuppressor.isAvailable()}, " +
                "AEC ${AcousticEchoCanceler.isAvailable()}",
        )
    }

    private fun logLevel(report: AudioLevel.Report) {
        Log.i(
            TAG,
            String.format(
                Locale.ROOT,
                "Mic: RMS prom %.1f dBFS, máx %.1f dBFS, pico %.1f dBFS",
                report.rmsDbfs,
                report.maxFrameRmsDbfs,
                report.peakDbfs,
            ),
        )
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_SIZE = SAMPLE_RATE / 10
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val BYTES_PER_SAMPLE = 2
        private const val LEVEL_REPORT_FRAMES = 50
        private const val TAG = "Halley"
    }
}
