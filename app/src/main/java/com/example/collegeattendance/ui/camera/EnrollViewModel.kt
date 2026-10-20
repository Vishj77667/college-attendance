package com.example.collegeattendance.ui.camera

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.collegeattendance.data.AppDatabase
import com.example.collegeattendance.data.StudentEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EnrollUiState(
    val samples: Int = 0,
    val maxSamples: Int = EnrollViewModel.MAX_SAMPLES,
    val isCapturing: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val message: String? = null,
    val error: Boolean = false,
    val faceVisible: Boolean = false,
    val modelReady: Boolean = true
) {
    val canSave: Boolean
        get() = samples >= maxSamples && !isSaving && !saved
}

class EnrollViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MAX_SAMPLES = 5
    }

    private val db = AppDatabase.getDatabase(application)
    private val engine = FaceRecognitionEngine(application)
    val faceAnalysis = FaceAnalysis(engine)

    private val _uiState = MutableStateFlow(
        EnrollUiState(
            modelReady = engine.isModelLoaded,
            message = if (engine.isModelLoaded) null else "Face model could not be loaded.",
            error = !engine.isModelLoaded
        )
    )
    val uiState: StateFlow<EnrollUiState> = _uiState.asStateFlow()

    private val embeddings = mutableListOf<FloatArray>()

    init {
        viewModelScope.launch {
            faceAnalysis.state.collect { state ->
                val current = _uiState.value
                if (current.faceVisible != state.hasFace) {
                    _uiState.value = current.copy(faceVisible = state.hasFace)
                }
            }
        }
    }

    fun capture(name: String, rollNumber: String) {
        val state = _uiState.value
        if (state.isCapturing || state.samples >= MAX_SAMPLES) return
        if (state.samples == 0) {
            if (name.isBlank() || rollNumber.isBlank()) {
                _uiState.value = state.copy(
                    message = "Enter the student name and roll number first.",
                    error = true
                )
                return
            }
        }

        _uiState.value = state.copy(isCapturing = true, message = null, error = false)
        viewModelScope.launch {
            val embedding = try {
                faceAnalysis.captureEmbedding()
            } catch (t: Throwable) {
                null
            }
            if (embedding == null) {
                _uiState.value = _uiState.value.copy(
                    isCapturing = false,
                    message = "No face detected. Look at the camera and try again.",
                    error = true
                )
                return@launch
            }
            embeddings.add(embedding)
            val count = embeddings.size
            _uiState.value = _uiState.value.copy(
                samples = count,
                isCapturing = false,
                message = if (count < MAX_SAMPLES) {
                    "Sample " + count + "/" + MAX_SAMPLES + " captured. Change your pose slightly."
                } else {
                    "All " + MAX_SAMPLES + " samples captured. Tap Save."
                },
                error = false
            )
        }
    }

    fun retake() {
        embeddings.clear()
        _uiState.value = _uiState.value.copy(
            samples = 0,
            saved = false,
            message = "Samples cleared.",
            error = false
        )
    }

    fun save(name: String, rollNumber: String) {
        if (embeddings.isEmpty() || _uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(isSaving = true, message = null, error = false)

        viewModelScope.launch {
            val trimmedName = name.trim()
            val trimmedRoll = rollNumber.trim()

            val averaged = FloatArray(embeddings[0].size)
            for (i in averaged.indices) {
                var sum = 0.0
                for (e in embeddings) sum += e[i].toDouble()
                averaged[i] = (sum / embeddings.size).toFloat()
            }

            val insertedId = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    db.studentDao().insertStudent(
                        StudentEntity(
                            name = trimmedName,
                            rollNumber = trimmedRoll,
                            faceEmbedding = serializeEmbedding(averaged)
                        )
                    )
                }
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    message = "Could not save the student: " + t.message,
                    error = true
                )
                return@launch
            }

            if (insertedId == -1L) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    message = "Roll number " + trimmedRoll + " is already enrolled.",
                    error = true
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saved = true,
                    message = trimmedName + " enrolled successfully.",
                    error = false
                )
            }
        }
    }

    override fun onCleared() {
        faceAnalysis.release()
        engine.close()
        super.onCleared()
    }
}