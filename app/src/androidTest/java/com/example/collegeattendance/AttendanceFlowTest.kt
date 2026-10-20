package com.example.collegeattendance

import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.collegeattendance.data.AppDatabase
import com.example.collegeattendance.data.AttendanceEntity
import com.example.collegeattendance.data.StudentEntity
import com.example.collegeattendance.ui.camera.FACE_MATCH_THRESHOLD
import com.example.collegeattendance.ui.camera.FaceRecognitionEngine
import com.example.collegeattendance.ui.camera.faceDistance
import com.example.collegeattendance.ui.camera.serializeEmbedding
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real attendance logic: enroll a face, recognise it again from a
 * different photo, and make sure the same student can only be marked once a day.
 */
@RunWith(AndroidJUnit4::class)
class AttendanceFlowTest {

    private lateinit var db: AppDatabase
    private lateinit var engine: FaceRecognitionEngine
    private val today = "2026-10-19"

    private fun load(name: String) =
        BitmapFactory.decodeStream(
            InstrumentationRegistry.getInstrumentation().context.assets.open(name)
        )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        engine = FaceRecognitionEngine(context)
    }

    @After
    fun tearDown() {
        db.close()
        engine.close()
    }

    @Test
    fun enrolledFaceIsRecognisedAndMarkedOncePerDay() = runBlocking {
        val enrolled = engine.embedLargestFace(load("person_a_1.png"), 0)
        assertNotNull(enrolled)
        val studentId = db.studentDao()
            .insertStudent(
                StudentEntity(
                    name = "Test Student",
                    rollNumber = "R-001",
                    faceEmbedding = serializeEmbedding(enrolled!!)
                )
            )
        assertTrue("Student must be inserted", studentId > 0L)

        val stored = db.studentDao().findByRollNumber("R-001")
        assertNotNull(stored)
        val profile = com.example.collegeattendance.ui.camera.parseEmbedding(stored!!.faceEmbedding)
        assertNotNull(profile)

        // Recognise the same person from a DIFFERENT photo.
        val probe = engine.embedLargestFace(load("person_a_2.png"), 0)
        assertNotNull(probe)
        val distance = faceDistance(probe!!, profile!!)
        assertTrue("Same person must match below the threshold", distance < FACE_MATCH_THRESHOLD)

        // First marking succeeds.
        val first = db.attendanceDao().insertAttendance(
            AttendanceEntity(
                studentId = studentId.toInt(),
                studentName = stored.name,
                rollNumber = stored.rollNumber,
                date = today,
                time = "09:00 AM",
                timestamp = 1L
            )
        )
        assertTrue("First attendance insert must succeed", first > 0L)

        // Second attempt on the same day is ignored by the unique index.
        val second = db.attendanceDao().insertAttendance(
            AttendanceEntity(
                studentId = studentId.toInt(),
                studentName = stored.name,
                rollNumber = stored.rollNumber,
                date = today,
                time = "10:00 AM",
                timestamp = 2L
            )
        )
        assertEquals("Duplicate attendance must be rejected", -1L, second)
        assertEquals(1, db.attendanceDao().getForDate(today).size)
    }

    @Test
    fun differentPersonDoesNotMatchEnrolledStudent() = runBlocking {
        val enrolled = engine.embedLargestFace(load("person_a_1.png"), 0)
        val intruder = engine.embedLargestFace(load("person_b_1.jpg"), 0)
        assertNotNull(enrolled)
        assertNotNull(intruder)

        val distance = faceDistance(intruder!!, enrolled!!)
        assertTrue(
            "A different person must not be accepted as the enrolled student",
            distance > FACE_MATCH_THRESHOLD
        )
    }

    @Test
    fun duplicateRollNumberIsRejected() = runBlocking {
        val first = db.studentDao().insertStudent(
            StudentEntity(name = "A", rollNumber = "R-9", faceEmbedding = "0.1,0.2")
        )
        val second = db.studentDao().insertStudent(
            StudentEntity(name = "B", rollNumber = "R-9", faceEmbedding = "0.3,0.4")
        )
        assertTrue(first > 0L)
        assertEquals("Enrolling the same roll number twice must fail", -1L, second)
        assertEquals(1, db.studentDao().getAllStudents().size)
    }
}