package com.example.collegeattendance.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per student per day. The unique index on (studentId, date) is what
 * stops a student from being marked present twice on the same day.
 */
@Entity(
    tableName = "attendance",
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["studentId", "date"], unique = true),
        Index(value = ["date"])
    ]
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Int,
    val studentName: String,
    val rollNumber: String,
    /** ISO date, e.g. 2026-10-19 */
    val date: String,
    /** Human readable time, e.g. 09:42 AM */
    val time: String,
    /** When it happened, in epoch millis. */
    val timestamp: Long
)
