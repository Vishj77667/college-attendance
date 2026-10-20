package com.example.collegeattendance

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.collegeattendance.ui.camera.FaceAnalysis
import com.example.collegeattendance.ui.camera.FaceRecognitionEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * Regression test for the "crash while capturing" bug.
 *
 * The camera analyzer keeps replacing the pending frame while the user taps
 * capture. If the analyzer recycles the frame the capture is still reading,
 * the app dies with a native use-after-recycle crash. This test hammers that
 * exact interleaving and fails on any exception.
 */
@RunWith(AndroidJUnit4::class)
class FaceCaptureConcurrencyTest {

    private lateinit var engine: FaceRecognitionEngine

    private fun faceBitmap(): Bitmap =
        BitmapFactory.decodeStream(
            InstrumentationRegistry.getInstrumentation().context.assets.open("person_a_1.png")
        )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        engine = FaceRecognitionEngine(context)
    }

    @After
    fun tearDown() {
        engine.close()
    }

    @Test
    fun captureSurvivesFramesBeingReplacedConcurrently() = runBlocking {
        val analysis = FaceAnalysis(engine)
        val source = faceBitmap()
        val failure = AtomicReference<Throwable?>(null)
        val iterations = 40

        // Producer: behaves like the camera analyzer, replacing the pending frame
        // as fast as it can (which recycles the frame it replaces).
        val producer = Thread {
            try {
                for (i in 0 until iterations * 4) {
                    val copy = source.copy(Bitmap.Config.ARGB_8888, false)
                    analysis.publish(copy)
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            }
        }

        // Consumer: behaves like the user tapping "Capture sample".
        val consumer = Thread {
            try {
                for (i in 0 until iterations) {
                    val embedding = runBlocking { analysis.captureEmbedding() }
                    if (embedding != null) {
                        assertTrue("embedding must be 192 floats", embedding.size == 192)
                    }
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            }
        }

        val start = CountDownLatch(1)
        producer.start()
        consumer.start()
        start.countDown()
        producer.join(30_000)
        consumer.join(30_000)
        analysis.release()

        val error = failure.get()
        if (error != null) {
            throw AssertionError("Capture crashed while frames were replaced: " + error, error)
        }
    }

    @Test
    fun captureOnRecycledFrameReturnsNullInsteadOfCrashing() = runBlocking {
        val analysis = FaceAnalysis(engine)
        val frame = faceBitmap()
        analysis.publish(frame)
        frame.recycle() // simulate a frame that was freed underneath us
        val embedding = analysis.captureEmbedding()
        assertTrue("A recycled frame must yield no embedding", embedding == null)
        analysis.release()
    }

    @Test
    fun captureWithoutAnyFrameReturnsNull() = runBlocking {
        val analysis = FaceAnalysis(engine)
        assertTrue(analysis.captureEmbedding() == null)
        analysis.release()
    }
}