package com.example.collegeattendance

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.collegeattendance.ui.camera.FACE_MATCH_THRESHOLD
import com.example.collegeattendance.ui.camera.FaceRecognitionEngine
import com.example.collegeattendance.ui.camera.faceDistance
import com.example.collegeattendance.ui.camera.parseEmbedding
import com.example.collegeattendance.ui.camera.serializeEmbedding
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FacePipelineTest {

    private fun load(name: String): Bitmap {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val stream = instrumentation.context.assets.open(name)
        val bitmap = BitmapFactory.decodeStream(stream)
        stream.close()
        return bitmap
    }

    @Test
    fun modelLoadsAndProducesEmbeddings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = FaceRecognitionEngine(context)
        assertTrue("TFLite model must load from assets", engine.isModelLoaded)

        val face = load("person_a_1.png")
        val embedding = engine.embedLargestFace(face, 0)
        assertNotNull("A face must be detected in the sample photo", embedding)
        assertTrue("MobileFaceNet must return 192 floats", embedding!!.size == 192)

        var sum = 0.0
        for (v in embedding) sum += v.toDouble() * v
        assertTrue("Embedding must not be all zeros", sum > 0.1)
        engine.close()
    }

    @Test
    fun samePersonMatchesAndDifferentPersonDoesNot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = FaceRecognitionEngine(context)

        val a1 = engine.embedLargestFace(load("person_a_1.png"), 0)
        val a2 = engine.embedLargestFace(load("person_a_2.png"), 0)
        val b1 = engine.embedLargestFace(load("person_b_1.jpg"), 0)
        assertNotNull(a1)
        assertNotNull(a2)
        assertNotNull(b1)

        val sameDistance = faceDistance(a1!!, a2!!)
        val differentDistance = faceDistance(a1, b1!!)

        println("same person distance = " + sameDistance + " (threshold " + FACE_MATCH_THRESHOLD + ")")
        println("different person distance = " + differentDistance)

        assertTrue(
            "Two photos of the same person must be within the match threshold",
            sameDistance < FACE_MATCH_THRESHOLD
        )
        assertTrue(
            "Photos of different people must be farther apart than the same person",
            differentDistance > sameDistance
        )
        engine.close()
    }

    @Test
    fun embeddingSerialisationRoundTrips() {
        val original = floatArrayOf(0.1f, -0.25f, 0.5f, 3.25f)
        val restored = parseEmbedding(serializeEmbedding(original))
        assertNotNull(restored)
        assertTrue(restored!!.size == original.size)
        for (i in original.indices) {
            assertTrue(restored[i] == original[i])
        }
    }
}