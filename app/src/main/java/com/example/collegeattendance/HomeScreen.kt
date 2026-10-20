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
fun HomeScreen(
    onNavigateToCamera: () -> Unit,
    onNavigateToAdminLogin: () -> Unit,
    onViewAttendance: () -> Unit = {}
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
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Face Attendance",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$studentCount enrolled \u00b7 $presentToday present today",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onNavigateToCamera,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Take attendance (student)")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onViewAttendance,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Today's attendance")
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onNavigateToAdminLogin,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Admin login")
        }
    }
}
