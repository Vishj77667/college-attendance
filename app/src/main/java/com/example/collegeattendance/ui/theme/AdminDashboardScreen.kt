package com.example.collegeattendance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.collegeattendance.data.AppDatabase

@Composable
fun AdminDashboardScreen(
    onAddStudentClick: () -> Unit,
    onViewStudentsClick: () -> Unit,
    onViewAttendanceClick: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val studentCount by db.studentDao().observeCount().collectAsState(initial = 0)
    val today = remember {
        com.example.collegeattendance.ui.camera.AttendanceViewModel.isoDate(System.currentTimeMillis())
    }
    val presentToday by db.attendanceDao().observeCountForDate(today).collectAsState(initial = 0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Admin dashboard", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "$studentCount enrolled \u00b7 $presentToday present today",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(32.dp))

        Button(onClick = onAddStudentClick, modifier = Modifier.fillMaxWidth()) {
            Text("Add new student (enroll face)")
        }
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onViewStudentsClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Text("View enrolled students")
        }
        Spacer(Modifier.height(12.dp))

        OutlinedButton(onClick = onViewAttendanceClick, modifier = Modifier.fillMaxWidth()) {
            Text("Today's attendance")
        }
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Logout")
        }
    }
}
