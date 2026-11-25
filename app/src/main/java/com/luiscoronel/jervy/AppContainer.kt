package com.luiscoronel.jervy

import android.content.Context
import com.luiscoronel.jervy.audio.AndroidSpeaker
import com.luiscoronel.jervy.audio.AudioRecordSource
import com.luiscoronel.jervy.audio.BeepPlayer
import com.luiscoronel.jervy.audio.ManualTriggerSpotter
import com.luiscoronel.jervy.core.pipeline.AudioSource
import com.luiscoronel.jervy.core.pipeline.KeywordSpotter
import com.luiscoronel.jervy.system.ServiceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Inyección de dependencias manual: un único contenedor por proceso, creado en [JervyApplication].
 *
 * Las fábricas crean piezas cuyo dueño es quien las pide (el servicio): él las cierra.
 */
class AppContainer(
    private val context: Context,
) {
    private val mutableServiceStatus = MutableStateFlow(ServiceStatus.STOPPED)
    val serviceStatus: StateFlow<ServiceStatus> = mutableServiceStatus.asStateFlow()

    fun updateServiceStatus(status: ServiceStatus) {
        mutableServiceStatus.value = status
    }

    // 1.2b.2a: motor simulado con el botón de debug. En el 1.2b.2b se reemplaza por Vosk.
    private val manualSpotter = ManualTriggerSpotter()

    fun simulateDetection() {
        manualSpotter.trigger()
    }

    fun createAudioSource(): AudioSource = AudioRecordSource(context)

    fun createKeywordSpotter(): KeywordSpotter = manualSpotter

    suspend fun createSpeaker(): AndroidSpeaker = AndroidSpeaker.create(context)

    fun createBeepPlayer(): BeepPlayer = BeepPlayer()
}
