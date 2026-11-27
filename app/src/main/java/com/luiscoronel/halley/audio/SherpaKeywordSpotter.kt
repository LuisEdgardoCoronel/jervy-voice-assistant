package com.luiscoronel.halley.audio

import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.luiscoronel.halley.core.pipeline.KeywordSpotter
import kotlin.time.TimeSource
import com.k2fsa.sherpa.onnx.KeywordSpotter as SherpaKws

/**
 * [KeywordSpotter] con el keyword spotting de sherpa-onnx (modelo zipformer en inglés, "HAILEY").
 *
 * Las palabras clave, con su refuerzo (`:`) y umbral (`#`), están en el asset [KEYWORDS_FILE]: para
 * ajustarlos se edita ese archivo. [KEYWORDS_SCORE] y [KEYWORDS_THRESHOLD] son los valores para las
 * líneas que no los traen. El modelo y las keywords se leen directo de assets (sin copiar a disco).
 *
 * A diferencia de Vosk, no transcribe: solo informa la palabra clave detectada (NF4).
 *
 * Es dueño del motor y del stream: [close] libera los dos. No es thread-safe.
 */
class SherpaKeywordSpotter private constructor(
    private val kws: SherpaKws,
) : KeywordSpotter {
    private var stream: OnlineStream = kws.createStream()
    private val timer = BlockTimer()

    override fun accept(samples: ShortArray): String? {
        val startedAt = System.nanoTime()
        stream.acceptWaveform(samples.toFloatPcm(), AudioRecordSource.SAMPLE_RATE)
        var keyword: String? = null
        while (keyword == null && kws.isReady(stream)) {
            kws.decode(stream)
            val result = kws.getResult(stream)
            if (result.keyword.isNotEmpty()) {
                Log.i(TAG, "Sherpa: \"${result.keyword}\" (tokens ${result.tokens.joinToString(" ")})")
                // sherpa-onnx pide reiniciar el stream después de cada detección.
                kws.reset(stream)
                keyword = result.keyword
            }
        }
        timer.record(System.nanoTime() - startedAt)?.let {
            Log.i(TAG, "Sherpa: bloque prom ${it.averageMs} ms, máx ${it.maxMs} ms")
        }
        return keyword
    }

    /** Descarta el audio acumulado: un stream nuevo (el reset de sherpa solo reinicia la búsqueda). */
    override fun reset() {
        stream.release()
        stream = kws.createStream()
    }

    override fun close() {
        stream.release()
        kws.release()
    }

    companion object {
        private const val TAG = "Halley"
        private const val MODEL_DIR = "models/sherpa-kws-en"
        const val KEYWORDS_FILE = "$MODEL_DIR/keywords.txt"

        /** Refuerzo por defecto (más alto = detecta más fácil, y también más falsas). */
        const val KEYWORDS_SCORE = 1.0f

        /** Umbral por defecto (más alto = más exigente). */
        const val KEYWORDS_THRESHOLD = 0.25f
        private const val MAX_ACTIVE_PATHS = 4
        private const val NUM_TRAILING_BLANKS = 1
        private const val NUM_THREADS = 1
        private const val FEATURE_DIM = 80
        private const val MODEL_NAME = "epoch-12-avg-2-chunk-16-left-64.int8.onnx"

        /** Carga el modelo desde assets (código nativo: llamar fuera del hilo principal). */
        fun create(assets: AssetManager): SherpaKeywordSpotter {
            val startedAt = TimeSource.Monotonic.markNow()
            val config =
                KeywordSpotterConfig(
                    featConfig = FeatureConfig(sampleRate = AudioRecordSource.SAMPLE_RATE, featureDim = FEATURE_DIM),
                    modelConfig =
                        OnlineModelConfig(
                            transducer =
                                OnlineTransducerModelConfig(
                                    encoder = "$MODEL_DIR/encoder-$MODEL_NAME",
                                    decoder = "$MODEL_DIR/decoder-$MODEL_NAME",
                                    joiner = "$MODEL_DIR/joiner-$MODEL_NAME",
                                ),
                            tokens = "$MODEL_DIR/tokens.txt",
                            numThreads = NUM_THREADS,
                            modelType = "zipformer2",
                        ),
                    maxActivePaths = MAX_ACTIVE_PATHS,
                    keywordsFile = KEYWORDS_FILE,
                    keywordsScore = KEYWORDS_SCORE,
                    keywordsThreshold = KEYWORDS_THRESHOLD,
                    numTrailingBlanks = NUM_TRAILING_BLANKS,
                )
            val kws =
                try {
                    SherpaKws(assets, config)
                } catch (e: IllegalArgumentException) {
                    throw ModelUnavailableException("No se pudo cargar el modelo de sherpa-onnx", e)
                } catch (e: UnsatisfiedLinkError) {
                    throw ModelUnavailableException("No se pudo cargar la librería nativa de sherpa-onnx", e)
                }
            Log.i(TAG, "Modelo de sherpa-onnx cargado en ${startedAt.elapsedNow().inWholeMilliseconds} ms")
            return SherpaKeywordSpotter(kws)
        }
    }
}
