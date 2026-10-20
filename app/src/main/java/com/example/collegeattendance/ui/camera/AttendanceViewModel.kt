package com.example.collegeattendance.ui.camera

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.collegeattendance.data.AppDatabase
import com.example.collegeattendance.data.AttendanceEntity
import com.example.collegeattendance.data.StudentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface MatchResult {
    data class Marked(
        val name: String,
        val roll: String,
        val time: String,
        val confidence: Float
    ) : MatchResult

    data class AlreadyMarked(val name: String, val roll: String, val time: String) : MatchResult

    data class UnknownFace(val closestName: String?, val confidence: Float?) : MatchResult

    data object NoFace : MatchResult
}

data class AttendanceUiState(
    val isProcessing: Boolean = false,
    val enrolledCount: Int = 0,
    val markedCount: Int = 0,
    val result: MatchResult? = null,
    val modelReady: Boolean = true,
    val autoCapture: Boolean = true
)

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val engine = FaceRecognitionEngine(application)
    val faceAnalysis = FaceAnalysis(engine)

    private val _uiState = MutableStateFlow(AttendanceUiState(modelReady = engine.isModelLoaded))
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    private val today: String = isoDate(System.currentTimeMillis())

    @Volatile
    private var gallery: List<Pair<StudentEntity, FloatArray>> = emptyList()

    private var lastSeenFaceAt = 0L
    private var lastCaptureAt = 0L

    init {
        viewModelScope.launch {
            db.studentDao().observeStudents().collect { students ->
                val parsed = withContext(Dispatchers.Default) {
                    val list = ArrayList<Pair<StudentEntity, FloatArray>>()
                    for (student in students) {
                        val emb = parseEmbedding(student.faceEmbedding)
                        if (emb != null) list.add(Pair(student, emb))
                    }
                    list
                }
                gallery = parsed
                _uiState.value = _uiState.value.copy(enrolledCount = students.size)
            }
        }
        viewModelScope.launch {
            db.attendanceDao().observeCountForDate(today).collect { count ->
                _uiState.value = _uiState.value.copy(markedCount = count)
            }
        }
        viewModelScope.launch {
            faceAnalysis.state.collect { state ->
                if (!state.hasFace) {
                    lastSeenFaceAt = 0L
                } else {
                    val now = System.currentTimeMillis()
                    if (lastSeenFaceAt == 0L) lastSeenFaceAt = now
                    val current = _uiState.value
                    val cooldownPassed = now - lastCaptureAt > 3500L
                    val stable = now - lastSeenFaceAt > 900L
                    if (current.autoCapture && !current.isProcessing && cooldownPassed && stable) {
                        capture()
                    }
                }
            }
        }
    }

    fun capture() {
        val state = _uiState.value
        if (state.isProcessing) return
        lastCaptureAt = System.currentTimeMillis()
        _uiState.value = state.copy(isProcessing = true, result = null)

        viewModelScope.launch {
            val embedding = try {
                faceAnalysis.captureEmbedding()
            } catch (t: Throwable) {
                null
            }
            if (embedding == null) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    result = MatchResult.NoFace
                )
                return@launch
            }

            val candidates = gallery
            if (candidates.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    result = MatchResult.UnknownFace(null, null)
                )
                return@launch
            }

            var bestStudent: StudentEntity? = null
            var bestDistance = Float.MAX_VALUE
            for (pair in candidates) {
                val d = faceDistance(embedding, pair.second)
                if (d < bestDistance) {
                    bestDistance = d
                    bestStudent = pair.first
                }
            }

            val winner = bestStudent
            val result: MatchResult
            if (winner != null && bestDistance <= FACE_MATCH_THRESHOLD) {
                result = markPresent(winner, bestDistance)
            } else {
                result = MatchResult.UnknownFace(
                    closestName = winner?.name,
                    confidence = scoreToConfidence(bestDistance)
                )
            }
            _uiState.value = _uiState.value.copy(isProcessing = false, result = result)
        }
    }

    private suspend fun markPresent(student: StudentEntity, distance: Float): MatchResult {
        val now = System.currentTimeMillis()
        val existing = withContext(Dispatchers.IO) { db.attendanceDao().find(student.id, today) }
        if (existing != null) {
            return MatchResult.AlreadyMarked(student.name, student.rollNumber, existing.time)
        }

        val inserted = withContext(Dispatchers.IO) {
            db.attendanceDao().insertAttendance(
                AttendanceEntity(
                    studentId = student.id,
                    studentName = student.name,
                    rollNumber = student.rollNumber,
                    date = today,
                    time = timeOfDay(now),
                    timestamp = now
                )
            )
        }

        return if (inserted == -1L) {
            MatchResult.AlreadyMarked(student.name, student.rollNumber, timeOfDay(now))
        } else {
            MatchResult.Marked(
                name = student.name,
                roll = student.rollNumber,
                time = timeOfDay(now),
                confidence = scoreToConfidence(distance)
            )
        }
    }

    fun setAutoCapture(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoCapture = enabled)
    }

    override fun onCleared() {
        faceAnalysis.release()
        engine.close()
        super.onCleared()
    }

    companion object {
        fun isoDate(millis: Long): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

        fun timeOfDay(millis: Long): String =
            SimpleDateFormat("hh:mm a", Locale.US).format(Date(millis))

        fun scoreToConfidence(distance: Float): Float {
            val score = (1f - distance / (FACE_MATCH_THRESHOLD * 2f)) * 100f
            if (score < 0f) return 0f
            if (score > 100f) return 100f
            return score
        }
    }
}