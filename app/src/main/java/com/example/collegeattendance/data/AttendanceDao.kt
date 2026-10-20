package com.example.collegeattendance.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    /** Ignored when this student has already been marked for this date. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAttendance(attendance: AttendanceEntity): Long

    @Query("SELECT * FROM attendance WHERE date = :date ORDER BY timestamp DESC")
    fun observeForDate(date: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE date = :date ORDER BY timestamp DESC")
    suspend fun getForDate(date: String): List<AttendanceEntity>

    @Query("SELECT COUNT(*) FROM attendance WHERE date = :date")
    fun observeCountForDate(date: String): Flow<Int>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId AND date = :date LIMIT 1")
    suspend fun find(studentId: Int, date: String): AttendanceEntity?

    @Query("SELECT date FROM attendance GROUP BY date ORDER BY date DESC")
    fun observeDates(): Flow<List<String>>

    @Query("UPDATE attendance SET studentName = :name, rollNumber = :roll WHERE studentId = :studentId")
    suspend fun syncStudentDetails(studentId: Int, name: String, roll: String)

    @Query("DELETE FROM attendance WHERE date = :date")
    suspend fun deleteForDate(date: String)
}
