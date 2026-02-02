package com.wildlifespotter.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.wildlifespotter.app.interfaces.IdentifyTaxonomy
import com.wildlifespotter.app.interfaces.RetrofitInstance
import com.wildlifespotter.app.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AddSpotScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()

    // ---------- UI States ----------
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var compressedImage by remember { mutableStateOf<ByteArray?>(null) }
    var speciesLabel by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isAnalyzingImage by remember { mutableStateOf(false) }
    var locationAvailable by remember { mutableStateOf(true) }
    var showLocationDialog by remember { mutableStateOf(false) }

    // ---------- Location ----------
    var locationName by remember { mutableStateOf("Getting location...") }
    var latitude by remember { mutableDoubleStateOf(0.0) }
    var longitude by remember { mutableDoubleStateOf(0.0) }

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
        while (true) {
            getFastLocation(context) { lat, lng, name ->
                latitude = lat
                longitude = lng
                locationName = name
                val isValid = lat != 0.0 && lng != 0.0 && 
                              !name.contains("unavailable", ignoreCase = true) &&
                              !name.contains("Error", ignoreCase = true)
                locationAvailable = isValid
                showLocationDialog = !isValid
            }
            kotlinx.coroutines.delay(2000)
        }
    }

    // ---------- Image Pickers Logic ----------
    val onImageCaptured: (ByteArray?) -> Unit = { compressed ->
        if (compressed != null) {
            compressedImage = compressed
            isAnalyzingImage = true
            scope.launch {
                kotlinx.coroutines.delay(500)
                isAnalyzingImage = false
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                selectedImageUri = uri
                scope.launch {
                    val compressed = compressImage(context, uri)
                    onImageCaptured(compressed)
                }
            }
        }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
        onResult = { bitmap ->
            if (bitmap != null) {
                selectedImageUri = null
                val compressed = compressBitmap(bitmap)
                onImageCaptured(compressed)
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
                    onValueChange = { description = it },
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
                    scope.launch {
                        if (compressedImage == null) return@launch
                        isLoading = true
                        uploadSpotWithBytes(
                            context, compressedImage!!, "", description,
                            latitude, longitude, locationName, currentAzimuth
                        ) { success, msg ->
                            isLoading = false
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            if (success) {
                                description = ""
                                compressedImage = null
                                selectedImageUri = null
                                locationName = "Getting location..."
                            }
                        }
                    }
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

// ------------------------ Helpers ------------------------

suspend fun compressImage(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    try {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        compressBitmap(bitmap)
    } catch (e: Exception) { e.printStackTrace(); null }
}

fun compressBitmap(bitmap: Bitmap, maxSize: Int = 1024, quality: Int = 92): ByteArray {
    val ratio = maxOf(bitmap.width, bitmap.height).toFloat() / maxSize
    val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width / ratio).toInt(), (bitmap.height / ratio).toInt(), true)
    val baos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
    return baos.toByteArray()
}

@SuppressLint("MissingPermission")
suspend fun getFastLocation(context: Context, onResult: (Double, Double, String) -> Unit) {
    val client = LocationServices.getFusedLocationProviderClient(context)
    try {
        val last = client.lastLocation.await() ?: client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        if (last != null) {
            val name = reverseGeocode(context, last.latitude, last.longitude) ?: "Unknown Location"
            onResult(last.latitude, last.longitude, name)
        } else onResult(0.0, 0.0, "Location unavailable")
    } catch (e: Exception) { onResult(0.0, 0.0, "Error") }
}

suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
    try {
        val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)
        addresses?.firstOrNull()?.let { "${it.locality ?: ""}-${it.subLocality ?: it.subAdminArea ?: ""}" }
    } catch (e: Exception) { null }
}

suspend fun uploadSpotWithBytes(
    context: Context, bytes: ByteArray, species: String, description: String,
    latitude: Double, longitude: Double, locationName: String, azimuth: Float,
    onComplete: (Boolean, String) -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    try {
        val imagePart = MultipartBody.Part.createFormData("image", "upload.jpg", bytes.toRequestBody("image/jpeg".toMediaTypeOrNull()))
        val uploadRes = RetrofitInstance.api.uploadImage(imagePart)
        val imageId = uploadRes.id
        val userId = auth.currentUser?.uid


        val countryCode = if (userId != null) {
            db.collection("users").document(userId).get().await().getString("country") ?: "ITA"
        } else "ITA"


        val identifyRes = try { RetrofitInstance.api.identifyImage(imageId, countryCode) } catch (e: Exception) { null }
        val annotation = identifyRes?.annotations?.firstOrNull()
        val label = annotation?.label?.takeIf { it.isNotBlank() } ?: (if(species.isBlank()) "Unknown Species" else species)

        val geohash = GeoFireUtils.getGeoHashForLocation(GeoLocation(latitude, longitude))

        // Daily steps from firestore
        val todayKey = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val dailySteps = if (userId != null) {
            db.collection("users")
                .document(userId)
                .collection("steps")
                .document(todayKey)
                .get()
                .await()
                .getLong("dailySteps") ?: 0L
        } else 0L

        val spotData = hashMapOf(
            "species" to mapOf("label" to label, "taxonomy" to taxonomyToMap(annotation?.taxonomy)),
            "description" to description,
            "latitude" to latitude,
            "longitude" to longitude,
            "location_name" to locationName,
            "geohash" to geohash,
            "timestamp" to FieldValue.serverTimestamp(),
            "image_id" to imageId,
            "user_id" to (userId ?: "anonymous"),
            "daily_steps" to dailySteps,
            "sensor_data" to mapOf("compass_azimuth" to azimuth)
        )

        db.collection("spots").add(spotData).await()
        if (userId != null) {
            db.collection("users").document(userId).set(mapOf("totalSpots" to FieldValue.increment(1)), com.google.firebase.firestore.SetOptions.merge()).await()
        }
        onComplete(true, if(annotation?.label != null) "Identified: ${annotation.label.replaceFirstChar { it.uppercase() }}" else "Spot added!")
    } catch (e: Exception) { onComplete(false, "Error: ${e.message}") }
}

fun taxonomyToMap(taxonomy: IdentifyTaxonomy?): Map<String, Any?> {
    if (taxonomy == null) return emptyMap()
    return mapOf("id" to taxonomy.id, "class" to taxonomy.className, "order" to taxonomy.order, "family" to taxonomy.family, "genus" to taxonomy.genus, "species" to taxonomy.species)
}