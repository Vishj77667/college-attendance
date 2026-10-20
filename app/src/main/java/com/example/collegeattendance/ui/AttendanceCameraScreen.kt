package com.example.collegeattendance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.collegeattendance.ui.camera.AttendanceViewModel
import com.example.collegeattendance.ui.camera.CameraPermissionGate
import com.example.collegeattendance.ui.camera.FaceCamera
import com.example.collegeattendance.ui.camera.MatchResult

@Composable
fun AttendanceCameraScreen(
    onBack: () -> Unit,
    onViewAttendance: () -> Unit,
    viewModel: AttendanceViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    BackHandler { onBack() }

    val overlayColor = when (val r = state.result) {
        is MatchResult.Marked -> Color(0xFF4CAF50)
        is MatchResult.AlreadyMarked -> Color(0xFF2196F3)
        is MatchResult.UnknownFace -> Color(0xFFF44336)
        else -> Color(0xFFFFC107)
    }

    CameraPermissionGate {
        Box(modifier = Modifier.fillMaxSize()) {
            FaceCamera(faceAnalysis = viewModel.faceAnalysis, overlayColor = overlayColor)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .statusBarsPadding()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Take attendance",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = state.markedCount.toString() + " present today / " +
                            state.enrolledCount.toString() + " enrolled",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = onViewAttendance) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = "Attendance records",
                        tint = Color.White
                    )
                }
            }

            val result = state.result
            if (result != null) {
                ResultBanner(
                    result = result,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 112.dp, start = 16.dp, end = 16.dp)
                )
            }

            if (!state.modelReady) {
                Text(
                    text = "Face model missing - recognition is unavailable.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Auto capture", modifier = Modifier.weight(1f))
                        Switch(
                            checked = state.autoCapture,
                            onCheckedChange = { viewModel.setAutoCapture(it) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.capture() },
                        enabled = !state.isProcessing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(18.dp).height(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (state.isProcessing) "Recognising..." else "Recognise now")
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultBanner(result: MatchResult, modifier: Modifier = Modifier) {
    var title = ""
    var subtitle = ""
    var color = Color(0xFFF44336)

    when (result) {
        is MatchResult.Marked -> {
            title = "Attendance marked"
            subtitle = result.name + " (" + result.roll + ") at " + result.time +
                " - " + result.confidence.toInt() + "% match"
            color = Color(0xFF4CAF50)
        }
        is MatchResult.AlreadyMarked -> {
            title = "Already marked"
            subtitle = result.name + " (" + result.roll + ") was marked at " + result.time
            color = Color(0xFF2196F3)
        }
        is MatchResult.UnknownFace -> {
            title = "Face not recognised"
            if (result.closestName != null && result.confidence != null) {
                subtitle = "Closest: " + result.closestName + " - " +
                    result.confidence.toInt() + "% - below the match threshold"
            } else {
                subtitle = "No students enrolled yet. Ask an admin to enroll you."
            }
        }
        MatchResult.NoFace -> {
            title = "No face detected"
            subtitle = "Move closer and look straight at the camera."
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = color
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}