package com.example.collegeattendance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.collegeattendance.ui.camera.CameraPermissionGate
import com.example.collegeattendance.ui.camera.EnrollViewModel
import com.example.collegeattendance.ui.camera.FaceCamera

@Composable
fun EnrollScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EnrollViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var name by remember { mutableStateOf("") }
    var rollNumber by remember { mutableStateOf("") }
    var handled by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    LaunchedEffect(state.saved) {
        if (state.saved && !handled) {
            handled = true
            onSaved()
        }
    }

    CameraPermissionGate {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                FaceCamera(faceAnalysis = viewModel.faceAnalysis)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x99000000))
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Enroll face",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (state.faceVisible) "Face detected" else "Align your face",
                        color = if (state.faceVisible) Color(0xFF69F0AE) else Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            }

            Surface(shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Student name") },
                        singleLine = true,
                        enabled = state.samples == 0,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rollNumber,
                        onValueChange = { rollNumber = it },
                        label = { Text("Roll number") },
                        singleLine = true,
                        enabled = state.samples == 0,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Samples: " + state.samples + " / " + state.maxSamples,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.samples / state.maxSamples.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )

                    val message = state.message
                    if (message != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = message,
                            color = if (state.error) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (state.samples < state.maxSamples) {
                            Button(
                                onClick = { viewModel.capture(name, rollNumber) },
                                enabled = !state.isCapturing && !state.isSaving,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (state.isCapturing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.width(18.dp).height(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(if (state.isCapturing) "Capturing" else "Capture sample")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.save(name, rollNumber) },
                                enabled = state.canSave,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (state.isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.width(18.dp).height(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(if (state.isSaving) "Saving" else "Save student")
                            }
                        }

                        if (state.samples > 0 && !state.isSaving) {
                            OutlinedButton(onClick = { viewModel.retake() }) {
                                Text("Reset")
                            }
                        }
                    }
                }
            }
        }
    }
}