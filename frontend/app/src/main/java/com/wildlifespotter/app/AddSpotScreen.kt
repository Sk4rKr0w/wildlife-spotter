package com.wildlifespotter.app

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.wildlifespotter.app.models.AddSpotEvent
import com.wildlifespotter.app.models.AddSpotViewModel
import com.wildlifespotter.app.ui.components.*
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AddSpotScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val viewModel: AddSpotViewModel = viewModel()
    val uiState = viewModel.uiState

    // ---------- UI States ----------
    val selectedImageUri = uiState.selectedImageUri
    val compressedImage = uiState.compressedImage
    val description = uiState.description
    val isLoading = uiState.isLoading
    val isAnalyzingImage = uiState.isAnalyzingImage
    val locationAvailable = uiState.locationAvailable
    val showLocationDialog = uiState.showLocationDialog
    val locationName = uiState.locationName

    // ---------- Sensors ----------
    var currentAzimuth by remember { mutableFloatStateOf(0f) }

    // ---------- Sensors Setup ----------
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager

        val orientationListener = object : android.hardware.SensorEventListener {
            val accelerometerReading = FloatArray(3)
            val magnetometerReading = FloatArray(3)
            val rotationMatrix = FloatArray(9)
            val orientationAngles = FloatArray(3)

            override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                if (event == null) return
                when (event.sensor.type) {
                    android.hardware.Sensor.TYPE_ACCELEROMETER ->
                        System.arraycopy(event.values, 0, accelerometerReading, 0, 3)
                    android.hardware.Sensor.TYPE_MAGNETIC_FIELD ->
                        System.arraycopy(event.values, 0, magnetometerReading, 0, 3)
                }
                android.hardware.SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
                android.hardware.SensorManager.getOrientation(rotationMatrix, orientationAngles)
                currentAzimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            }
            override fun onAccuracyChanged(p0: android.hardware.Sensor?, p1: Int) {}
        }

        sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)?.also {
            sensorManager.registerListener(orientationListener, it, android.hardware.SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_MAGNETIC_FIELD)?.also {
            sensorManager.registerListener(orientationListener, it, android.hardware.SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(orientationListener)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startLocationUpdates(context)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AddSpotEvent.Message -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.onPhotoPicked(context, uri)
            }
        }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
        onResult = { bitmap ->
            if (bitmap != null) {
                viewModel.onCameraCaptured(bitmap)
            }
        }
    )

    // ---------- UI ----------
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedWaveBackground(primaryColor = Color(0xFF4CAF50), secondaryColor = Color(0xFF2EA333))
        FloatingParticles(particleCount = 12)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Title
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
                Icon(Icons.Default.AddLocationAlt, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Text("Add Wildlife Spot", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
            }

            // ---------- Image Card ----------
            Card(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF374B5E))
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (selectedImageUri != null) {
                        Image(painter = rememberAsyncImagePainter(selectedImageUri), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else if (compressedImage != null) {
                        Image(bitmap = BitmapFactory.decodeByteArray(compressedImage, 0, compressedImage!!.size).asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Icon(Icons.Default.AddAPhoto, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(80.dp))
                            Text("Capture Wildlife", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Button(onClick = { cameraLauncher.launch(null) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), shape = RoundedCornerShape(16.dp)) {
                                    Icon(Icons.Default.CameraAlt, null); Text(" Camera")
                                }
                                Button(onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)), shape = RoundedCornerShape(16.dp)) {
                                    Icon(Icons.Default.PhotoLibrary, null); Text(" Gallery")
                                }
                            }
                        }
                    }
                    if (isAnalyzingImage) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF4CAF50))
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---------- Description Field ----------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF374B5E).copy(alpha = 0.8f))
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { viewModel.updateDescription(it) },
                    label = { Text("Observation Notes") },
                    placeholder = { Text("What did you see? Describe the behavior...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,

                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),

                        focusedPlaceholderColor = Color.White.copy(alpha = 0.7f),
                        unfocusedPlaceholderColor = Color.Gray,

                        focusedLabelColor = Color(0xFF4CAF50),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // ---------- Location Card ----------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF374B5E).copy(alpha = 0.8f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = Color(0xFFE53935), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Location Details", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(locationName, color = Color.White, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF2D3E50)).padding(12.dp))
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---------- Action Button ----------
            Button(
                onClick = {
                    viewModel.submit(currentAzimuth)
                },
                enabled = !isLoading && compressedImage != null && locationAvailable,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else {
                    Icon(Icons.Default.CloudUpload, null)
                    Spacer(Modifier.width(12.dp))
                    Text("Add Wildlife Spot", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(40.dp))
        }

        if (showLocationDialog) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Location Required") },
                text = { 
                    Column {
                        Text("Please enable location services to add a wildlife spot.")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Current status: $locationName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Waiting for location...")
                    }
                }
            )
        }
    }
}
