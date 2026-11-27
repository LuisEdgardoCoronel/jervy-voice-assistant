package com.luiscoronel.halley.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
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
 * Usa la fuente VOICE_RECOGNITION (en el diagnóstico del 1.2b.3c anduvo igual o mejor que MIC en los
 * dos teléfonos) y no activa SCO: el micrófono Bluetooth no se usa, así la música sigue en estéreo
 * (A2DP). El [AudioRecord] se crea al colectar y se libera al cancelar o fallar. El audio nunca se
 * guarda (NF4): para diagnóstico solo se loguean su nivel ([AudioLevel]) y si se pierde audio
 * ([AudioDrift]) cada [LEVEL_REPORT_FRAMES] bloques y al soltar el micrófono (así cada detección deja
 * el nivel de esa palabra clave).
 */
class AudioRecordSource(
    private val context: Context,
) : AudioSource {
    override fun frames(): Flow<ShortArray> =
        flow {
            val record = openRecord()
            val level = AudioLevel(LEVEL_REPORT_FRAMES)
            val drift = AudioDrift(SAMPLE_RATE, record.bufferSizeInFrames, LEVEL_REPORT_FRAMES)
            val timestamp = AudioTimestamp()
            try {
                val openedAt = TimeSource.Monotonic.markNow()
                record.startRecording()
                val startedAtNanos = System.nanoTime()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    throw MicrophoneUnavailableException("AudioRecord no empezó a grabar (¿otra app tiene el micrófono?)")
                }
                Log.i(TAG, "Micrófono abierto en ${openedAt.elapsedNow().inWholeMilliseconds} ms")
                logInputInfo(record)
                while (currentCoroutineContext().isActive) {
                    val frame = ShortArray(FRAME_SIZE)
                    val read = record.read(frame, 0, FRAME_SIZE)
                    if (read < 0) throw MicrophoneUnavailableException("AudioRecord.read devolvió $read")
                    if (read > 0) {
                        val now = System.nanoTime()
                        val block = if (read == FRAME_SIZE) frame else frame.copyOf(read)
                        level.add(block)?.let { logLevel("Mic", it) }
                        drift.add(read, now - startedAtNanos, hardwareFrames(record, timestamp, now))?.let {
                            logDrift("Mic", it)
                        }
                        emit(block)
                    }
                }
            } finally {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
                record.release()
                level.flush()?.let { logLevel("Mic (parcial, ${it.frames} bloques)", it) }
                drift.flush()?.let { logDrift("Mic (parcial, ${it.blocks} bloques)", it) }
                Log.d(TAG, "Micrófono liberado")
            }
        }.flowOn(Dispatchers.IO)

    private fun openRecord(): AudioRecord {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) throw MicrophoneUnavailableException("Falta el permiso RECORD_AUDIO")

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) throw MicrophoneUnavailableException("getMinBufferSize devolvió $minBuffer")

        val record =
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
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

    /** Diagnóstico: micrófono que sirve la fuente y efectos de preprocesamiento que ofrece el teléfono. */
    private fun logInputInfo(record: AudioRecord) {
        val device = record.routedDevice
        Log.i(
            TAG,
            "Micrófono: dispositivo ${device?.productName} (tipo ${device?.type}), " +
                "disponibles AGC ${AutomaticGainControl.isAvailable()}, NS ${NoiseSuppressor.isAvailable()}, " +
                "AEC ${AcousticEchoCanceler.isAvailable()}",
        )
    }

    /**
     * Muestras capturadas por el hardware hasta [nowNanos], según [AudioRecord.getTimestamp] (posición de
     * una muestra y el instante en que se capturó, extrapolado hasta ahora); null si el teléfono no lo da.
     */
    private fun hardwareFrames(
        record: AudioRecord,
        timestamp: AudioTimestamp,
        nowNanos: Long,
    ): Long? {
        if (record.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) != AudioRecord.SUCCESS) return null
        return timestamp.framePosition + (nowNanos - timestamp.nanoTime) * SAMPLE_RATE / NANOS_PER_SECOND
    }

    private fun logDrift(
        label: String,
        report: AudioDrift.Report,
    ) {
        val backlog =
            report.maxBacklogMs?.let { "pendiente HW máx $it de ${report.bufferMs} ms" }
                ?: "pendiente HW sin datos (búfer ${report.bufferMs} ms)"
        Log.i(
            TAG,
            String.format(
                Locale.ROOT,
                "%s: audio %.2f s en %.2f s de reloj, atraso mín %d ms, máx %d ms, %s%s",
                label,
                report.audioMs / MILLIS_PER_SECOND,
                report.clockMs / MILLIS_PER_SECOND,
                report.minLagMs,
                report.maxLagMs,
                backlog,
                if (report.overflow) " — DESBORDE" else "",
            ),
        )
    }

    private fun logLevel(
        label: String,
        report: AudioLevel.Report,
    ) {
        Log.i(
            TAG,
            String.format(
                Locale.ROOT,
                "%s: RMS prom %.1f dBFS, máx %.1f dBFS, pico %.1f dBFS",
                label,
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
        private const val NANOS_PER_SECOND = 1_000_000_000L
        private const val MILLIS_PER_SECOND = 1_000.0
        private const val TAG = "Halley"
    }
}
