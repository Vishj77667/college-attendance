package com.example.collegeattendance.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    /** Returns -1 when the roll number already exists (unique index conflict). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStudent(student: StudentEntity): Long

    @Query("SELECT * FROM students ORDER BY name COLLATE NOCASE ASC")
    fun observeStudents(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students")
    suspend fun getAllStudents(): List<StudentEntity>

    @Query("SELECT COUNT(*) FROM students")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM students WHERE rollNumber = :rollNumber LIMIT 1")
    suspend fun findByRollNumber(rollNumber: String): StudentEntity?

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun deleteStudent(id: Int)
}
