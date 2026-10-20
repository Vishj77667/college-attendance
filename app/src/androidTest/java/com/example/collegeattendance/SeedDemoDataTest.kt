package com.example.collegeattendance

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.collegeattendance.data.AppDatabase
import com.example.collegeattendance.data.AttendanceEntity
import com.example.collegeattendance.data.StudentEntity
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Documentation helper: puts a few students and today's attendance into the app's
 * own database so that screenshots for the project report show realistic data.
 * Not part of the functional test suite.
 */
@RunWith(AndroidJUnit4::class)
class SeedDemoDataTest {

    @Test
    fun seed() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.getDatabase(context)
        val students = db.studentDao()
        val attendance = db.attendanceDao()

        val today = com.example.collegeattendance.ui.camera.AttendanceViewModel
            .isoDate(System.currentTimeMillis())

        val demo = listOf(
            Triple("Aarav Patil", "CS-101", 0.13f),
            Triple("Sneha Deshmukh", "CS-102", 0.27f),
            Triple("Rohan Jadhav", "CS-103", 0.41f),
            Triple("Priya Kulkarni", "CS-104", 0.58f)
        )
        for ((name, roll, seedValue) in demo) {
            val embedding = FloatArray(192) { i -> seedValue + (i % 7) * 0.001f }
            val id = students.insertStudent(
                StudentEntity(
                    name = name,
                    rollNumber = roll,
                    faceEmbedding = com.example.collegeattendance.ui.camera
                        .serializeEmbedding(embedding)
                )
            )
            if (id > 0L && (roll == "CS-101" || roll == "CS-103")) {
                attendance.insertAttendance(
                    AttendanceEntity(
                        studentId = id.toInt(),
                        studentName = name,
                        rollNumber = roll,
                        date = today,
                        time = if (roll == "CS-101") "09:12 AM" else "09:18 AM",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}