package com.luiscoronel.halley

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.luiscoronel.halley.audio.AndroidSpeaker
import com.luiscoronel.halley.audio.AudioRecordSource
import com.luiscoronel.halley.audio.BeepPlayer
import com.luiscoronel.halley.audio.SherpaKeywordSpotter
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

    /**
     * Carga el modelo y crea el motor elegido al compilar (`-PwakeEngine`, selector temporal del 1.2c).
     * En builds debuggable lo envuelve con el disparador de debug.
     */
    suspend fun createKeywordSpotter(): KeywordSpotter {
        Log.i(TAG, "Motor de palabra clave: ${BuildConfig.WAKE_ENGINE}")
        // Cargar el modelo y crear el reconocedor es código nativo: fuera del hilo principal.
        val engine =
            withContext(Dispatchers.IO) {
                when (BuildConfig.WAKE_ENGINE) {
                    "vosk" -> VoskKeywordSpotter.create(VoskModelLoader(context).load(), AudioRecordSource.SAMPLE_RATE)
                    else -> SherpaKeywordSpotter.create(context.assets)
                }
            }
        if (!debuggable) return engine
        return TriggerableKeywordSpotter(engine).also { triggerable = it }
    }

    suspend fun createSpeaker(): AndroidSpeaker = AndroidSpeaker.create(context)

    fun createBeepPlayer(): BeepPlayer = BeepPlayer()

    private companion object {
        const val TAG = "Halley"
    }
}
