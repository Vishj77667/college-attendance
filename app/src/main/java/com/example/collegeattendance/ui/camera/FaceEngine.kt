package com.example.collegeattendance.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.atan2
import kotlin.math.sqrt

private const val MODEL_FILE_NAME = "mobilefacenet.tflite"
private const val MODEL_INPUT = 112
private const val MODEL_OUTPUT = 192
private const val NORM_MEAN = 127.5f
private const val NORM_STD = 128.0f
private const val LOG_TAG = "FaceEngine"

const val FACE_MATCH_THRESHOLD = 0.89f

class FaceRecognitionEngine(context: Context) {

    @Volatile
    private var interpreter: Interpreter? = openModel(context)
    private val detectionLock = Any()

    private val detector: FaceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setMinFaceSize(0.15f)
                .build()
        )
    }

    val isModelLoaded: Boolean
        get() = interpreter != null

    private fun openModel(context: Context): Interpreter? {
        try {
            val afd = context.assets.openFd(MODEL_FILE_NAME)
            val stream = FileInputStream(afd.fileDescriptor)
            val mapped: MappedByteBuffer = stream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )
            val options = Interpreter.Options()
            options.setNumThreads(2)
            val engine = Interpreter(mapped, options)
            Log.d(LOG_TAG, "model loaded")
            return engine
        } catch (e: Exception) {
            Log.e(LOG_TAG, "model load failed: " + e.message)
            return null
        }
    }

    fun getEmbedding(bitmap: Bitmap): FloatArray? {
        val engine = interpreter ?: return null
        if (bitmap.isRecycled) return null
        var scaled: Bitmap? = null
        val input: Bitmap
        if (bitmap.width == MODEL_INPUT && bitmap.height == MODEL_INPUT) {
            input = bitmap
        } else {
            scaled = Bitmap.createScaledBitmap(bitmap, MODEL_INPUT, MODEL_INPUT, true)
            input = scaled
        }

        val buffer = ByteBuffer.allocateDirect(MODEL_INPUT * MODEL_INPUT * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(MODEL_INPUT * MODEL_INPUT)
        input.getPixels(pixels, 0, MODEL_INPUT, 0, 0, MODEL_INPUT, MODEL_INPUT)
        var i = 0
        while (i < pixels.size) {
            val p = pixels[i]
            buffer.putFloat((((p shr 16) and 0xFF) - NORM_MEAN) / NORM_STD)
            buffer.putFloat((((p shr 8) and 0xFF) - NORM_MEAN) / NORM_STD)
            buffer.putFloat(((p and 0xFF) - NORM_MEAN) / NORM_STD)
            i++
        }
        buffer.rewind()

        val output = Array(1) { FloatArray(MODEL_OUTPUT) }
        var result: FloatArray? = null
        try {
            engine.run(buffer, output)
            result = output[0]
        } catch (e: Exception) {
            Log.e(LOG_TAG, "inference failed: " + e.message)
        }
        if (scaled != null) scaled.recycle()
        return result
    }

    /**
     * Blocking detection. Serialised because the camera analyzer and a capture
     * can both ask for detection at the same time, and sharing one ML Kit
     * detector concurrently is not safe.
     */
    fun detectFacesSync(bitmap: Bitmap, rotationDegrees: Int): List<Face>? {
        if (bitmap.isRecycled) return null
        synchronized(detectionLock) {
            try {
                if (bitmap.isRecycled) return null
                val image = InputImage.fromBitmap(bitmap, rotationDegrees)
                return Tasks.await(detector.process(image))
            } catch (t: Throwable) {
                Log.e(LOG_TAG, "detection failed: " + t.message)
                return null
            }
        }
    }

    fun largestFace(faces: List<Face>): Face? {
        var best: Face? = null
        var bestArea = 0L
        for (face in faces) {
            val box = face.boundingBox
            val area = box.width().toLong() * box.height().toLong()
            if (area > bestArea) {
                bestArea = area
                best = face
            }
        }
        return best
    }

    fun embedLargestFace(bitmap: Bitmap, rotationDegrees: Int): FloatArray? {
        if (bitmap.isRecycled) return null
        val faces = detectFacesSync(bitmap, rotationDegrees) ?: return null
        val face = largestFace(faces) ?: return null
        val crop = alignedFaceCrop(bitmap, face, rotationDegrees) ?: return null
        return try {
            if (crop.isRecycled) null else getEmbedding(crop)
        } finally {
            if (!crop.isRecycled) crop.recycle()
        }
    }

    fun alignedFaceCrop(bitmap: Bitmap, face: Face, rotationDegrees: Int): Bitmap? {
        var source = bitmap
        val normalized = ((rotationDegrees % 360) + 360) % 360
        if (normalized != 0) {
            val m = Matrix()
            m.postRotate(normalized.toFloat())
            source = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        }

        val box = RectF(face.boundingBox)
        val padX = box.width() * 0.25f
        val padY = box.height() * 0.25f
        box.inset(-padX, -padY)
        box.intersect(0f, 0f, source.width.toFloat(), source.height.toFloat())
        if (box.width() < 8f || box.height() < 8f) {
            if (source !== bitmap) source.recycle()
            return null
        }

        val left = face.getLandmark(FaceLandmark.LEFT_EYE)
        val right = face.getLandmark(FaceLandmark.RIGHT_EYE)
        val matrix = Matrix()
        if (left != null && right != null) {
            val lx = left.position.x
            val ly = left.position.y
            val rx = right.position.x
            val ry = right.position.y
            val angle = Math.toDegrees(atan2((ry - ly).toDouble(), (rx - lx).toDouble())).toFloat()
            val cx = (lx + rx) / 2f
            val cy = (ly + ry) / 2f
            matrix.postTranslate(-cx, -cy)
            matrix.postRotate(-angle)
            matrix.postTranslate(cx, cy)
        }
        matrix.postTranslate(-box.left, -box.top)
        matrix.postScale(MODEL_INPUT / box.width(), MODEL_INPUT / box.height())

        val out = Bitmap.createBitmap(MODEL_INPUT, MODEL_INPUT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        if (source !== bitmap) source.recycle()
        return out
    }

    fun close() {
        try {
            detector.close()
        } catch (e: Exception) {
            Log.w(LOG_TAG, "detector close failed")
        }
        try {
            // Clear the reference first so an in-flight capture stops using it
            // instead of touching a closed native interpreter.
            val engine = interpreter
            interpreter = null
            engine?.close()
        } catch (e: Exception) {
            Log.w(LOG_TAG, "interpreter close failed")
        }
    }
}

fun faceDistance(a: FloatArray, b: FloatArray): Float {
    if (a.size != b.size) return Float.MAX_VALUE
    var sum = 0.0
    for (i in a.indices) {
        val d = (a[i] - b[i]).toDouble()
        sum += d * d
    }
    return sqrt(sum).toFloat()
}

fun serializeEmbedding(embedding: FloatArray): String {
    val sb = StringBuilder()
    for (i in embedding.indices) {
        if (i > 0) sb.append(',')
        sb.append(embedding[i])
    }
    return sb.toString()
}

fun parseEmbedding(csv: String): FloatArray? {
    if (csv.isEmpty()) return null
    val parts = csv.split(',')
    val values = FloatArray(parts.size)
    for (i in parts.indices) {
        val v = parts[i].trim().toFloatOrNull()
        if (v == null) return null
        values[i] = v
    }
    return values
}
