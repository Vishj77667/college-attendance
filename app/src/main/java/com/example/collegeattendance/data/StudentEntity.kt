package com.example.collegeattendance.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "students",
    indices = [Index(value = ["rollNumber"], unique = true)]
)
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val rollNumber: String,
    // We store the embedding as a comma-separated string of floats
    val faceEmbedding: String
)
