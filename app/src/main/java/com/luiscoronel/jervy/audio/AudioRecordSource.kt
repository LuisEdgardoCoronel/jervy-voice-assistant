package com.luiscoronel.jervy.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.luiscoronel.jervy.core.pipeline.AudioSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.time.TimeSource

/**
 * Micrófono del teléfono vía [AudioRecord]: PCM 16 bits, mono, 16 kHz, bloques de 100 ms.
 *
 * Usa la fuente VOICE_RECOGNITION y no activa SCO: el micrófono Bluetooth no se usa, así la música
 * sigue en estéreo (A2DP). El [AudioRecord] se crea al colectar y se libera al cancelar o fallar.
 * El audio nunca se guarda (NF4).
 */
class AudioRecordSource(
    private val context: Context,
) : AudioSource {
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
                while (currentCoroutineContext().isActive) {
                    val frame = ShortArray(FRAME_SIZE)
                    val read = record.read(frame, 0, FRAME_SIZE)
                    if (read < 0) throw MicrophoneUnavailableException("AudioRecord.read devolvió $read")
                    if (read > 0) emit(if (read == FRAME_SIZE) frame else frame.copyOf(read))
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

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_SIZE = SAMPLE_RATE / 10
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val BYTES_PER_SAMPLE = 2
        private const val TAG = "Jervy"
    }
}
