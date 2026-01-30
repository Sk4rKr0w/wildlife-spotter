package com.wildlifespotter.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import com.wildlifespotter.app.interfaces.RetrofitInstance

@Composable
fun AddSpotScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()

    var speciesInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    var currentSteps by remember { mutableFloatStateOf(0f) }
    var currentAzimuth by remember { mutableFloatStateOf(0f) }

    // UI State for source selection dialog
    var showSourceDialog by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // ---- Sensors ----
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Step counter
        val stepListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let { if (it.sensor.type == Sensor.TYPE_STEP_COUNTER) currentSteps = it.values[0] }
            }
            override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}
        }

        // Compass
        val orientationListener = object : SensorEventListener {
            val accelerometerReading = FloatArray(3)
            val magnetometerReading = FloatArray(3)
            val rotationMatrix = FloatArray(9)
            val orientationAngles = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
                } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
                }

                SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                currentAzimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            }
            override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}
        }

        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.also {
            sensorManager.registerListener(stepListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
            sensorManager.registerListener(orientationListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also {
            sensorManager.registerListener(orientationListener, it, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(stepListener)
            sensorManager.unregisterListener(orientationListener)
        }
    }

    // ---- Photo pickers ----
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    isLoading = true
                    uploadSpot(context, speciesInput, currentSteps, currentAzimuth, uri)
                    isLoading = false
                    speciesInput = ""
                }
            }
        }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success && tempCameraUri != null) {
                scope.launch {
                    isLoading = true
                    uploadSpot(context, speciesInput, currentSteps, currentAzimuth, tempCameraUri!!)
                    isLoading = false
                    speciesInput = ""
                }
            }
        }
    )

    // ---- UI ----
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(text = "Add a spot", style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = speciesInput,
                onValueChange = { speciesInput = it },
                label = { Text("Animal species (es. Wolf)") },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Compass: ${currentAzimuth.toInt()}° | Steps: ${currentSteps.toInt()}",
                style = MaterialTheme.typography.bodySmall
            )

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        if (speciesInput.isBlank()) {
                            Toast.makeText(context, "Set species before adding a spot", Toast.LENGTH_SHORT).show()
                        } else {
                            showSourceDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select photo and add spot")
                }
            }
        }

        // ---- Source selection dialog ----
        if (showSourceDialog) {
            AlertDialog(
                onDismissRequest = { showSourceDialog = false },
                title = { Text("Choose Image Source") },
                text = { Text("Select a photo from gallery or take a new one.") },
                confirmButton = {
                    TextButton(onClick = {
                        showSourceDialog = false
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Text("Gallery")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showSourceDialog = false
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            val uri = createImageUri(context)
                            tempCameraUri = uri
                            cameraLauncher.launch(uri)
                        } else {
                            Toast.makeText(context, "Camera permission not granted", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Camera")
                    }
                }
            )
        }
    }
}

// ---- Helpers ----

fun createImageUri(context: Context): Uri {
    val directory = File(context.cacheDir, "images")
    directory.mkdirs()
    val file = File.createTempFile("selected_image_", ".jpg", directory)
    val authority = "${context.packageName}.provider"
    return FileProvider.getUriForFile(context, authority, file)
}

fun prepareImagePart(context: Context, uri: Uri): MultipartBody.Part? {
    return try {
        val contentResolver = context.contentResolver
        val type = contentResolver.getType(uri) ?: "image/jpeg"
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val byteArray = inputStream.readBytes()
        inputStream.close()
        val requestBody = byteArray.toRequestBody(type.toMediaTypeOrNull())
        MultipartBody.Part.createFormData("image", "upload.jpg", requestBody)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@SuppressLint("MissingPermission")
suspend fun uploadSpot(
    context: Context,
    species: String,
    currentSteps: Float,
    currentAzimuth: Float,
    imageUri: Uri
) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val locationClient = LocationServices.getFusedLocationProviderClient(context)

    try {
        val imagePart = prepareImagePart(context, imageUri)
        if (imagePart == null) {
            Toast.makeText(context, "Error during image reading", Toast.LENGTH_LONG).show()
            return
        }

        // Upload image to backend
        val response = RetrofitInstance.api.uploadImage(imagePart)
        val imageId = response.id
        if (imageId.isBlank()) {
            Toast.makeText(context, "Error during image upload", Toast.LENGTH_LONG).show()
            return
        }

        // GPS location
        val location: Location? = locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        if (location == null) {
            Toast.makeText(context, "Can't obtain GPS position", Toast.LENGTH_LONG).show()
            return
        }

        val geohash = GeoFireUtils.getGeoHashForLocation(GeoLocation(location.latitude, location.longitude))
        val sensorData = mapOf(
            "compass_azimuth" to currentAzimuth,
            "steps_count" to currentSteps
        )

        val spotData = hashMapOf(
            "species" to species,
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "geohash" to geohash,
            "timestamp" to FieldValue.serverTimestamp(),
            "image_id" to imageId,
            "user_id" to (auth.currentUser?.uid ?: "anonymous"),
            "description" to "",
            "sensor_data" to sensorData
        )

        db.collection("spots").add(spotData).await()

        Toast.makeText(context, "Spot added successfully!", Toast.LENGTH_LONG).show()

    } catch (e: Exception) {
        Toast.makeText(context, "Error adding spot on DB: ${e.message}", Toast.LENGTH_LONG).show()
        e.printStackTrace()
    }
}
