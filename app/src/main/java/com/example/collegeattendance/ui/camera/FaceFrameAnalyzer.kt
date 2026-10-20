package com.example.collegeattendance.ui.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class FaceFrameState(
    val faceBox: Rect? = null,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0
) {
    val hasFace: Boolean
        get() = faceBox != null && frameWidth > 0 && frameHeight > 0
}

/**
 * Turns camera frames into face boxes and hands out frames for capture.
 *
 * Ownership of the pending frame is strict: the analyzer keeps it until either
 * (a) a newer frame replaces it, in which case the replaced frame is recycled,
 * or (b) a capture takes it out of the slot, in which case the analyzer can no
 * longer see it and the capture recycles it when it is done.
 *
 * The old implementation let the analyzer recycle a frame that a capture was
 * still drawing from, which crashed the app with a native use-after-recycle.
 */
class FaceAnalysis(private val engine: FaceRecognitionEngine) {

    private val _state = MutableStateFlow(FaceFrameState())
    val state: StateFlow<FaceFrameState> = _state.asStateFlow()

    /** Set to false to pause analysis (e.g. while a capture runs). */
    @Volatile
    var enabled: Boolean = true

    /** Analysis runs on at most this many pixels per side. 112x112 is enough
     *  for the embedder, and small frames keep memory and CPU predictable. */
    private val MAX_ANALYSIS_DIM = 720

    private val frameLock = Any()
    private var latestFrame: Bitmap? = null

    val analyzer = ImageAnalysis.Analyzer { proxy -> analyze(proxy) }

    private fun analyze(proxy: ImageProxy) {
        // `owned` is the single bitmap this pass is responsible for freeing.
        // Exactly one bitmap is ever retained for capture, and analysis runs on
        // a downscaled copy: full-resolution camera frames are ~11 MB each and
        // retaining several of them per frame is what made real devices run out
        // of memory while capturing.
        var owned: Bitmap? = null
        try {
            if (!enabled) return
            var frame = frameToBitmap(proxy) ?: return

            if (frame.width > MAX_ANALYSIS_DIM || frame.height > MAX_ANALYSIS_DIM) {
                val scale = MAX_ANALYSIS_DIM.toFloat() / maxOf(frame.width, frame.height)
                val w = (frame.width * scale).toInt().coerceAtLeast(1)
                val h = (frame.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(frame, w, h, true)
                frame.recycle()
                frame = scaled
            }

            val rotation = ((proxy.imageInfo.rotationDegrees % 360) + 360) % 360
            if (rotation != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotation.toFloat())
                val rotated = Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true)
                frame.recycle()
                frame = rotated
            }

            owned = frame
            val faces = engine.detectFacesSync(frame, 0)
            val box = if (faces == null) null else engine.largestFace(faces)?.boundingBox

            _state.value = FaceFrameState(
                faceBox = box,
                frameWidth = frame.width,
                frameHeight = frame.height
            )

            // Ownership moves to the slot; do not recycle it below.
            publish(frame)
            owned = null
        } catch (t: Throwable) {
            // A single bad frame must never take the analyzer down.
        } finally {
            if (owned != null && !owned.isRecycled) owned.recycle()
            proxy.close()
        }
    }

    /**
     * Embeds the newest frame. Takes ownership of it, so the analyzer can never
     * recycle it while ML Kit is still reading its pixels.
     */
    /**
     * Publishes a new frame and takes ownership of it. The frame it replaces is
     * recycled only after it has been removed from the slot, so a capture that
     * already owns it is never affected.
     */
    fun publish(frame: Bitmap) {
        val replaced = synchronized(frameLock) {
            val old = latestFrame
            latestFrame = frame
            old
        }
        if (replaced != null && replaced !== frame && !replaced.isRecycled) replaced.recycle()
    }

    /**
     * Embeds the newest frame. Takes ownership of it, so the analyzer can never
     * recycle it while ML Kit is still reading its pixels.
     */
    suspend fun captureEmbedding(): FloatArray? = withContext(Dispatchers.Default) {
        val frame = synchronized(frameLock) {
            val taken = latestFrame
            latestFrame = null
            taken
        } ?: return@withContext null

        enabled = false
        try {
            if (frame.isRecycled) null else engine.embedLargestFace(frame, 0)
        } catch (t: Throwable) {
            null
        } finally {
            if (!frame.isRecycled) frame.recycle()
            enabled = true
        }
    }

    /** Drops the retained frame; call when leaving the screen. */
    fun release() {
        val frame = synchronized(frameLock) {
            val taken = latestFrame
            latestFrame = null
            taken
        }
        if (frame != null && !frame.isRecycled) frame.recycle()
    }

    companion object {
        fun frameToBitmap(proxy: ImageProxy): Bitmap? {
            try {
                return proxy.toBitmap()
            } catch (t: Throwable) {
                return yuv420ToBitmap(proxy)
            }
        }

        fun yuv420ToBitmap(proxy: ImageProxy): Bitmap? {
            if (proxy.format != android.graphics.ImageFormat.YUV_420_888) return null
            val width = proxy.width
            val height = proxy.height
            if (width <= 0 || height <= 0) return null
            val planes = proxy.planes
            if (planes.size < 3) return null

            val yBuf = planes[0].buffer
            val uBuf = planes[1].buffer
            val vBuf = planes[2].buffer
            val yRowStride = planes[0].rowStride
            val yPixelStride = planes[0].pixelStride
            val uRowStride = planes[1].rowStride
            val uPixelStride = planes[1].pixelStride
            val vRowStride = planes[2].rowStride
            val vPixelStride = planes[2].pixelStride

            val argb = IntArray(width * height)
            for (row in 0 until height) {
                val yRow = row * yRowStride
                val uvRow = row / 2
                for (col in 0 until width) {
                    val yIndex = yRow + col * yPixelStride
                    val uvCol = col / 2
                    val uIndex = uvRow * uRowStride + uvCol * uPixelStride
                    val vIndex = uvRow * vRowStride + uvCol * vPixelStride

                    val y = if (yIndex < yBuf.limit()) yBuf.get(yIndex).toInt() and 0xFF else 0
                    val u = if (uIndex < uBuf.limit()) uBuf.get(uIndex).toInt() and 0xFF else 128
                    val v = if (vIndex < vBuf.limit()) vBuf.get(vIndex).toInt() and 0xFF else 128

                    val yf = y - 16
                    val uf = u - 128
                    val vf = v - 128
                    var r = (1.164f * yf + 1.596f * vf).toInt()
                    var g = (1.164f * yf - 0.392f * uf - 0.813f * vf).toInt()
                    var b = (1.164f * yf + 2.017f * uf).toInt()
                    if (r < 0) r = 0
                    if (r > 255) r = 255
                    if (g < 0) g = 0
                    if (g > 255) g = 255
                    if (b < 0) b = 0
                    if (b > 255) b = 255
                    argb[row * width + col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            return Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
        }
    }
}

fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
    val normalized = ((degrees % 360) + 360) % 360
    if (normalized == 0) return bitmap
    val matrix = Matrix()
    matrix.postRotate(normalized.toFloat())
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}