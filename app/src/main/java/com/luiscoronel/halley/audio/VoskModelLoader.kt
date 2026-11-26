package com.luiscoronel.halley.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.android.StorageService
import java.io.File
import java.io.IOException
import kotlin.time.TimeSource

/**
 * Carga el modelo de Vosk empaquetado en assets (lo pone ahí la tarea de Gradle downloadVoskModel).
 *
 * Vosk necesita el modelo en archivos comunes: [StorageService.sync] lo copia a la carpeta privada
 * de la app en almacenamiento externo (`Android/data/<paquete>/files/model`) la primera vez, o
 * cuando cambia el archivo `uuid` (otro modelo). Si la carga falla, borra la copia para que el
 * próximo arranque la rehaga.
 */
class VoskModelLoader(
    private val context: Context,
) {
    suspend fun load(): Model =
        withContext(Dispatchers.IO) {
            val startedAt = TimeSource.Monotonic.markNow()
            val path =
                try {
                    StorageService.sync(context, ASSET_PATH, TARGET_PATH)
                } catch (e: IOException) {
                    throw ModelUnavailableException("No se pudo copiar el modelo de Vosk", e)
                }
            Log.i(TAG, "Modelo de Vosk copiado/verificado en ${startedAt.elapsedNow().inWholeMilliseconds} ms")

            val loadStartedAt = TimeSource.Monotonic.markNow()
            val model =
                try {
                    Model(path)
                } catch (e: IOException) {
                    File(path).deleteRecursively()
                    throw ModelUnavailableException("No se pudo cargar el modelo de Vosk (copia borrada)", e)
                } catch (e: UnsatisfiedLinkError) {
                    throw ModelUnavailableException("No se pudo cargar la librería nativa de Vosk", e)
                }
            Log.i(TAG, "Modelo de Vosk cargado en ${loadStartedAt.elapsedNow().inWholeMilliseconds} ms")
            model
        }

    private companion object {
        const val TAG = "Halley"
        const val ASSET_PATH = "models/vosk-es"
        const val TARGET_PATH = "model"
    }
}
