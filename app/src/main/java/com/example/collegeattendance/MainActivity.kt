package com.example.collegeattendance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.collegeattendance.ui.AdminDashboardScreen
import com.example.collegeattendance.ui.AdminLoginScreen
import com.example.collegeattendance.ui.AttendanceCameraScreen
import com.example.collegeattendance.ui.AttendanceListScreen
import com.example.collegeattendance.ui.EnrollScreen
import com.example.collegeattendance.ui.HomeScreen
import com.example.collegeattendance.ui.StudentListScreen
import com.example.collegeattendance.ui.camera.AttendanceViewModel
import com.example.collegeattendance.ui.theme.CollegeAttendanceTheme

/** Every screen the app can show. */
enum class Screen {
    HOME,
    ADMIN_LOGIN,
    ADMIN_DASHBOARD,
    ENROLL,
    STUDENT_LIST,
    ATTENDANCE,
    ATTENDANCE_LIST
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CollegeAttendanceTheme {
                Surface {
                    var stack by remember { mutableStateOf(listOf(Screen.HOME)) }
                    val context = LocalContext.current
                    val current = stack.last()

                    fun navigate(screen: Screen) {
                        stack = when (screen) {
                            // Logging in / out resets the stack so the system back
                            // button can never land on the login screen again.
                            Screen.ADMIN_DASHBOARD, Screen.HOME -> listOf(screen)
                            else -> stack + screen
                        }
                    }

                    fun goBack() {
                        if (stack.size > 1) stack = stack.dropLast(1)
                    }

                    // Default back handling for screens without their own.
                    BackHandler(enabled = stack.size > 1 && current == Screen.ADMIN_DASHBOARD) {
                        goBack()
                    }

                    when (current) {
                        Screen.HOME -> HomeScreen(
                            onNavigateToCamera = { navigate(Screen.ATTENDANCE) },
                            onNavigateToAdminLogin = { navigate(Screen.ADMIN_LOGIN) }
                        )

                        Screen.ATTENDANCE -> AttendanceCameraScreen(
                            onBack = { goBack() },
                            onViewAttendance = { navigate(Screen.ATTENDANCE_LIST) }
                        )

                        Screen.ATTENDANCE_LIST -> AttendanceListScreen(
                            onBack = { goBack() },
                            date = AttendanceViewModel.isoDate(System.currentTimeMillis())
                        )

                        Screen.ADMIN_LOGIN -> AdminLoginScreen(
                            onLoginSuccess = { navigate(Screen.ADMIN_DASHBOARD) },
                            onBack = { goBack() }
                        )

                        Screen.ADMIN_DASHBOARD -> AdminDashboardScreen(
                            onAddStudentClick = { navigate(Screen.ENROLL) },
                            onViewStudentsClick = { navigate(Screen.STUDENT_LIST) },
                            onViewAttendanceClick = { navigate(Screen.ATTENDANCE_LIST) },
                            onLogout = { navigate(Screen.HOME) }
                        )

                        Screen.ENROLL -> EnrollScreen(
                            onBack = { goBack() },
                            onSaved = { goBack() }
                        )

                        Screen.STUDENT_LIST -> StudentListScreen(
                            onBack = { goBack() },
                            onAddStudent = { navigate(Screen.ENROLL) }
                        )
                    }
                }
            }
        }
    }
}
