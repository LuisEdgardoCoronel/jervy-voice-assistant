package com.luiscoronel.jervy.core.pipeline

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class SpottingWakeWordDetectorTest {
    @Test
    fun `emite la palabra clave cuando el motor la reconoce`() =
        runTest {
            val detector = SpottingWakeWordDetector(FakeAudioSource(SILENCE, MATCH), FakeSpotter())

            val detection = detector.detections().first()

            assertEquals(KEYWORD, detection.keyword)
        }

    @Test
    fun `detectedAt marca el momento en que el motor reconocio la palabra`() =
        runTest {
            val timeSource = TestTimeSource()
            val start = timeSource.markNow()
            val source =
                AudioSource {
                    flow {
                        emit(SILENCE)
                        timeSource += 300.milliseconds
                        emit(MATCH)
                        timeSource += 1.seconds
                    }
                }

            val detection = SpottingWakeWordDetector(source, FakeSpotter(), timeSource).detections().first()

            assertEquals(300.milliseconds, detection.detectedAt - start)
        }

    @Test
    fun `tomar la primera deteccion cierra la fuente y no procesa mas audio`() =
        runTest {
            val source = FakeAudioSource(SILENCE, MATCH, SILENCE, SILENCE)
            val spotter = FakeSpotter()

            SpottingWakeWordDetector(source, spotter).detections().first()

            assertEquals(1, source.closed)
            assertEquals(2, spotter.accepted)
        }

    @Test
    fun `llama a reset al empezar y despues de cada deteccion`() =
        runTest {
            val spotter = FakeSpotter()
            val detector = SpottingWakeWordDetector(FakeAudioSource(MATCH, SILENCE, MATCH), spotter)

            val detections = detector.detections().toList()

            assertEquals(2, detections.size)
            assertEquals(3, spotter.resets)
        }

    @Test
    fun `sin deteccion no emite nada y la fuente se cierra al terminar`() =
        runTest {
            val source = FakeAudioSource(SILENCE, SILENCE, SILENCE)

            val detections = SpottingWakeWordDetector(source, FakeSpotter()).detections().toList()

            assertTrue(detections.isEmpty())
            assertEquals(1, source.closed)
        }

    @Test
    fun `si el motor falla el flow falla con esa excepcion y la fuente se cierra igual`() =
        runTest {
            val source = FakeAudioSource(SILENCE, BROKEN, MATCH)
            val detector = SpottingWakeWordDetector(source, FakeSpotter())

            val error = runCatching { detector.detections().toList() }.exceptionOrNull()

            assertTrue(error is IllegalStateException)
            assertEquals(ENGINE_ERROR, error?.message)
            assertEquals(1, source.closed)
        }

    @Test
    fun `cancelar la coleccion (pausa) cierra la fuente`() =
        runTest {
            val source = FakeAudioSource(SILENCE, SILENCE, endless = true)
            val detector = SpottingWakeWordDetector(source, FakeSpotter())

            // UNDISPATCHED: la colección arranca ya y queda suspendida esperando audio, como el micrófono real.
            val job = launch(start = CoroutineStart.UNDISPATCHED) { detector.detections().collect {} }
            assertEquals(1, source.opened)
            assertEquals(0, source.closed)

            job.cancelAndJoin()

            assertEquals(1, source.closed)
        }

    @Test
    fun `no cierra el motor ni al detectar ni al fallar`() =
        runTest {
            val spotter = FakeSpotter()
            SpottingWakeWordDetector(FakeAudioSource(MATCH), spotter).detections().first()
            runCatching { SpottingWakeWordDetector(FakeAudioSource(BROKEN), spotter).detections().toList() }

            assertFalse(spotter.closed)
        }

    /** Fuente finita (o infinita con [endless]) que cuenta aperturas y cierres. */
    private class FakeAudioSource(
        private vararg val blocks: ShortArray,
        private val endless: Boolean = false,
    ) : AudioSource {
        var opened = 0
        var closed = 0

        override fun frames() =
            flow {
                opened++
                try {
                    blocks.forEach { emit(it) }
                    if (endless) awaitCancellation()
                } finally {
                    closed++
                }
            }
    }

    /** Reconoce [MATCH] y falla con [BROKEN] (comparados por identidad); el resto es silencio. */
    private class FakeSpotter : KeywordSpotter {
        var accepted = 0
        var resets = 0
        var closed = false

        override fun accept(samples: ShortArray): String? {
            accepted++
            return when {
                samples === MATCH -> KEYWORD
                samples === BROKEN -> throw IllegalStateException(ENGINE_ERROR)
                else -> null
            }
        }

        override fun reset() {
            resets++
        }

        override fun close() {
            closed = true
        }
    }

    private companion object {
        const val KEYWORD = "jer vi"
        const val ENGINE_ERROR = "motor roto"
        val SILENCE = shortArrayOf(0)
        val MATCH = shortArrayOf(1)
        val BROKEN = shortArrayOf(2)
    }
}
