package com.example.collegeattendance.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Composable
fun FaceCamera(
    faceAnalysis: FaceAnalysis,
    modifier: Modifier = Modifier,
    overlayColor: Color = Color(0xFFFFC107),
    showGuide: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val frameState by faceAnalysis.state.collectAsState()

    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val preview = remember {
        Preview.Builder()
            .setResolutionSelector(faceResolutionSelector())
            .build()
            .apply { setSurfaceProvider { surfaceRequest = it } }
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(faceResolutionSelector())
            .build()
            .apply { setAnalyzer(analysisExecutor, faceAnalysis.analyzer) }
    }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    LaunchedEffect(lifecycleOwner) {
        try {
            val provider = awaitCameraProvider(context)
            val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = "Camera unavailable: " + e.message
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val request = surfaceRequest
        if (request != null) {
            CameraXViewfinder(
                surfaceRequest = request,
                modifier = Modifier.fillMaxSize(),
                implementationMode = ImplementationMode.EMBEDDED,
                contentScale = ContentScale.Fit
            )
        }

        FaceBoxOverlay(state = frameState, color = overlayColor, showGuide = showGuide)

        val message = errorMessage
        if (message != null) {
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC000000))
                    .padding(12.dp)
            )
        }
    }
}

@Composable
fun FaceBoxOverlay(
    state: FaceFrameState,
    color: Color,
    showGuide: Boolean = true
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (showGuide) {
            val ovalW = size.width * 0.62f
            val ovalH = ovalW * 1.3f
            drawOval(
                color = color.copy(alpha = 0.55f),
                topLeft = Offset((size.width - ovalW) / 2f, (size.height - ovalH) / 2f),
                size = Size(ovalW, ovalH),
                style = Stroke(width = 4.dp.toPx())
            )
        }

        val box = state.faceBox
        if (box != null && state.frameWidth > 0 && state.frameHeight > 0) {
            val scale = minOf(
                size.width / state.frameWidth.toFloat(),
                size.height / state.frameHeight.toFloat()
            )
            val offsetX = (size.width - state.frameWidth * scale) / 2f
            val offsetY = (size.height - state.frameHeight * scale) / 2f
            drawRect(
                color = color,
                topLeft = Offset(box.left * scale + offsetX, box.top * scale + offsetY),
                size = Size(box.width() * scale, box.height() * scale),
                style = Stroke(width = 4.dp.toPx())
            )
        }
    }
}


@Composable
fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var asked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result -> granted = result }

    LaunchedEffect(Unit) {
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (granted) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Camera permission is required to recognise faces.",
                style = MaterialTheme.typography.bodyLarge
            )
            Button(
                onClick = { launcher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Grant camera permission")
            }
        }
    }
}

private fun faceResolutionSelector(): ResolutionSelector =
    ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        .build()

suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider {
    val future = ProcessCameraProvider.getInstance(context)
    return suspendCoroutine { cont ->
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }
}
