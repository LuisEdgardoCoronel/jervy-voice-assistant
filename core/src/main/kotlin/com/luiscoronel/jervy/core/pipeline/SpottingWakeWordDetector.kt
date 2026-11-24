package com.luiscoronel.jervy.core.pipeline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlin.time.TimeSource

/**
 * [WakeWordDetector] que pasa cada bloque de [source] por [spotter].
 *
 * - Al empezar cada colección y después de cada detección llama a [KeywordSpotter.reset], para que
 *   el audio de una escucha anterior no dispare una detección nueva.
 * - **No cierra el [spotter]**: solo llama a `reset()`. El `close()` lo hace su dueño (el servicio,
 *   en `onDestroy`), porque el mismo motor se reutiliza en cada ciclo de escucha.
 * - Si [spotter] tira una excepción, el Flow falla con esa excepción y [source] se cierra igual.
 * - No colectar dos veces en paralelo: [KeywordSpotter] no es thread-safe.
 */
class SpottingWakeWordDetector(
    private val source: AudioSource,
    private val spotter: KeywordSpotter,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) : WakeWordDetector {
    override fun detections(): Flow<WakeWordDetection> =
        source
            .frames()
            .onStart { spotter.reset() }
            .mapNotNull { frame ->
                spotter.accept(frame)?.let { keyword ->
                    val detection = WakeWordDetection(keyword, timeSource.markNow())
                    spotter.reset()
                    detection
                }
            }
}
