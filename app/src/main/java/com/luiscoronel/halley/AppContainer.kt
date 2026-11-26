package com.luiscoronel.halley

import android.content.Context
import android.content.pm.ApplicationInfo
import com.luiscoronel.halley.audio.AndroidSpeaker
import com.luiscoronel.halley.audio.AudioRecordSource
import com.luiscoronel.halley.audio.BeepPlayer
import com.luiscoronel.halley.audio.VoskKeywordSpotter
import com.luiscoronel.halley.audio.VoskModelLoader
import com.luiscoronel.halley.core.pipeline.AudioSource
import com.luiscoronel.halley.core.pipeline.KeywordSpotter
import com.luiscoronel.halley.core.pipeline.TriggerableKeywordSpotter
import com.luiscoronel.halley.system.ServiceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Inyección de dependencias manual: un único contenedor por proceso, creado en [HalleyApplication].
 *
 * Las fábricas crean piezas cuyo dueño es quien las pide (el servicio): él las cierra.
 */
class AppContainer(
    private val context: Context,
) {
    private val mutableServiceStatus = MutableStateFlow(ServiceStatus.STOPPED)
    val serviceStatus: StateFlow<ServiceStatus> = mutableServiceStatus.asStateFlow()

    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** Motor en uso con el disparador de debug; null en release o si el servicio no arrancó. */
    @Volatile
    private var triggerable: TriggerableKeywordSpotter? = null

    fun updateServiceStatus(status: ServiceStatus) {
        mutableServiceStatus.value = status
    }

    /** Botón de debug "Simular detección". En release no hace nada. */
    fun simulateDetection() {
        triggerable?.trigger()
    }

    fun createAudioSource(): AudioSource = AudioRecordSource(context)

    /** Carga el modelo y crea el motor de Vosk. En builds debuggable lo envuelve con el disparador de debug. */
    suspend fun createKeywordSpotter(): KeywordSpotter {
        // Crear el reconocedor compila la gramática (código nativo): fuera del hilo principal.
        val vosk =
            withContext(Dispatchers.IO) {
                VoskKeywordSpotter.create(VoskModelLoader(context).load(), AudioRecordSource.SAMPLE_RATE)
            }
        if (!debuggable) return vosk
        return TriggerableKeywordSpotter(vosk).also { triggerable = it }
    }

    suspend fun createSpeaker(): AndroidSpeaker = AndroidSpeaker.create(context)

    fun createBeepPlayer(): BeepPlayer = BeepPlayer()
}
