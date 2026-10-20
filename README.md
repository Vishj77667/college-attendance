# College Attendance (face recognition)

Offline face-recognition attendance for Android. Enroll a student once with
their face, then mark attendance by looking at the camera. No network or cloud
service is used at runtime.

## How it works

1. **Enrollment** (admin) - ML Kit detects the largest face, the crop is aligned
   on the eyes, resized to 112x112 and passed through **MobileFaceNet** (TFLite)
   to produce a 192-float embedding. Five samples are averaged for stability.
2. **Attendance** - the same pipeline runs on a live camera frame; the L2
   distance between the captured embedding and every enrolled profile is
   computed. The closest profile is accepted when the distance is below
   `0.89` (the threshold the model was validated with).
3. **Storage** - Room/SQLite. A unique index on `(studentId, date)` guarantees a
   student can only be marked once per day.

## Admin credentials

```
username: admin
password: college2024
```

Defined as `ADMIN_USERNAME` / `ADMIN_PASSWORD` at the bottom of
`app/src/main/java/com/example/collegeattendance/ui/theme/AdminLoginScreen.kt`.

## Screens

| Screen | Purpose |
| --- | --- |
| Home | Counters, take attendance, today's attendance, admin login |
| Take attendance | Live camera, auto-capture toggle, result banner |
| Admin login | Credential gate for management screens |
| Admin dashboard | Enroll student, view students, today's attendance |
| Enroll face | Name + roll number, 5 face samples, save |
| Enrolled students | List with remove (cascades to attendance) |
| Today's attendance | Who is present, with time |

## Project layout

```
app/src/main/java/com/example/collegeattendance/
  MainActivity.kt          navigation stack (enum based)
  HomeScreen.kt
  data/                    Room entities, DAOs, database
  ui/                      Compose screens
  ui/camera/               CameraX + ML Kit + TFLite pipeline
app/src/main/assets/mobilefacenet.tflite   5.0 MB face embedding model
```

## Build

```bash
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/
./gradlew connectedDebugAndroidTest   # needs a device/emulator
```

Requirements: JDK 17, Android SDK 36.

`debug` builds include x86_64 so they also run on an emulator; `release` is
ARM-only because TensorFlow Lite ships a ~7 MB native library per architecture.

## Tests

`app/src/androidTest` contains on-device tests that prove the pipeline works:

- the TFLite model loads and returns 192-value embeddings
- two different photos of the same person match (distance ~0.59 < 0.89)
- a photo of a different person is rejected (distance ~1.38)
- attendance is marked once per day; duplicates are rejected
- duplicate roll numbers are rejected

## Notes

- The face model (`mobilefacenet.tflite`) is stored uncompressed so TFLite can
  memory-map it; this is configured with `androidResources { noCompress += "tflite" }`.
- Camera frames are rotated to upright before detection, and the crop is aligned
  on the eye landmarks, which is required for accurate embeddings.
- Everything runs on device; the app requests only the camera permission.