package com.wildlifespotter.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.location.Geocoder
import android.location.Location
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

// ------------------------ Data Classes ------------------------

data class INaturalistResponse(val results: List<INaturalistResult>)
data class INaturalistResult(val taxon: INaturalistTaxon?)
data class INaturalistTaxon(
    val name: String,
    val preferred_common_name: String?,
    val iconic_taxon_name: String?
)

// ------------------------ Composable ------------------------

@Composable
fun AddSpotScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()

    // ---------- Stati UI ----------
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var compressedImage by remember { mutableStateOf<ByteArray?>(null) }
    var description by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // ---------- Location ----------
    var locationName by remember { mutableStateOf("Getting location...") }
    var latitude by remember { mutableDoubleStateOf(0.0) }
    var longitude by remember { mutableDoubleStateOf(0.0) }

    // ---------- Sensori ----------
    var currentSteps by remember { mutableFloatStateOf(0f) }
    var currentAzimuth by remember { mutableFloatStateOf(0f) }

    // ---------- Sensori Setup ----------
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager

        val stepListener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                event?.let {
                    if (it.sensor.type == android.hardware.Sensor.TYPE_STEP_COUNTER)
                        currentSteps = it.values[0]
                }
            }
            override fun onAccuracyChanged(p0: android.hardware.Sensor?, p1: Int) {}
        }

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

        sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER)?.also {
            sensorManager.registerListener(stepListener, it, android.hardware.SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)?.also {
            sensorManager.registerListener(orientationListener, it, android.hardware.SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_MAGNETIC_FIELD)?.also {
            sensorManager.registerListener(orientationListener, it, android.hardware.SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(stepListener)
            sensorManager.unregisterListener(orientationListener)
        }
    }

    // ---------- Image Pickers ----------
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                selectedImageUri = uri
                scope.launch {
                    compressImage(context, uri)?.let { compressed ->
                        compressedImage = compressed
                        getFastLocation(context) { lat, lng, name ->
                            latitude = lat
                            longitude = lng
                            locationName = name
                        }
                    }
                }
            }
        }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
        onResult = { bitmap ->
            bitmap?.let {
                scope.launch {
                    val compressed = compressBitmap(bitmap)
                    compressedImage = compressed
                    selectedImageUri = null // preview bitmap only
                    getFastLocation(context) { lat, lng, name ->
                        latitude = lat
                        longitude = lng
                        locationName = name
                    }
                }
            }
        }
    )

    // ---------- UI ----------
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A2332), Color(0xFF2D3E50))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Add Wildlife Spot",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // ---------- Image Card ----------
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = Color(0xFF374B5E))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedImageUri != null) {
                        Image(
                            painter = rememberAsyncImagePainter(selectedImageUri),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (compressedImage != null) {
                        Image(
                            bitmap = BitmapFactory.decodeByteArray(compressedImage, 0, compressedImage!!.size).asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Add a Photo", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Button(onClick = { cameraLauncher.launch(null) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Camera")
                                }
                                Button(onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107))) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Gallery")
                                }
                            }
                        }
                    }

                }
            }

            Spacer(Modifier.height(16.dp))

            // ---------- Description ----------
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                placeholder = { Text("Ex: che bel gattino!") },
                modifier = Modifier.fillMaxWidth().height(120.dp)
            )

            Spacer(Modifier.height(16.dp))

            // ---------- Location ----------
            Text("Location: $locationName", color = Color.White)
            if (latitude != 0.0 && longitude != 0.0)
                Text("Coordinates: %.6f, %.6f".format(latitude, longitude), color = Color.Gray)

            Spacer(Modifier.height(16.dp))

            // ---------- Upload ----------
            Button(
                onClick = {
                    scope.launch {
                        if (compressedImage == null) {
                            Toast.makeText(context, "Select image", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        isLoading = true
                        uploadSpotWithBytes(
                            context, compressedImage!!, "", description,
                            latitude, longitude, locationName, currentSteps, currentAzimuth
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
                enabled = !isLoading && compressedImage != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White)
                else Text("Add Spot", color = Color.White)
            }
        }
    }
}

// ------------------------ Helpers ------------------------

// Compress image from URI
suspend fun compressImage(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    try {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        compressBitmap(bitmap)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// Compress bitmap to JPEG
fun compressBitmap(bitmap: Bitmap, maxSize: Int = 1024, quality: Int = 95): ByteArray {
    val ratio = maxOf(bitmap.width, bitmap.height).toFloat() / maxSize
    val width = (bitmap.width / ratio).toInt()
    val height = (bitmap.height / ratio).toInt()
    val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
    val baos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
    return baos.toByteArray()
}

// Fast location with fallback
@SuppressLint("MissingPermission")
suspend fun getFastLocation(context: Context, onResult: (Double, Double, String) -> Unit) {
    val client = LocationServices.getFusedLocationProviderClient(context)
    try {
        val last = client.lastLocation.await()
        if (last != null) {
            reverseGeocode(context, last.latitude, last.longitude)?.let { name ->
                onResult(last.latitude, last.longitude, name)
                return
            }
            onResult(last.latitude, last.longitude, "Unknown Location")
        } else {
            val current = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            if (current != null) {
                reverseGeocode(context, current.latitude, current.longitude)?.let { name ->
                    onResult(current.latitude, current.longitude, name)
                    return
                }
            }
            onResult(0.0, 0.0, "Location unavailable")
        }
    } catch (e: Exception) {
        e.printStackTrace()
        onResult(0.0, 0.0, "Error getting location")
    }
}

// Reverse geocode
suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocation(lat, lng, 1)
        if (!addresses.isNullOrEmpty()) {
            val a = addresses[0]
            buildString {
                a.locality?.let { append(it) }
                a.subLocality?.let { append("-$it") }
                if (isEmpty()) append(a.subAdminArea ?: "Unknown Location")
            }
        } else null
    } catch (e: Exception) {
        null
    }
}

// Upload spot from bytes
suspend fun uploadSpotWithBytes(
    context: Context,
    bytes: ByteArray,
    species: String,
    description: String,
    latitude: Double,
    longitude: Double,
    locationName: String,
    steps: Float,
    azimuth: Float,
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
            try {
                val userDoc = db.collection("users").document(userId).get().await()
                userDoc.getString("country") ?: "ITA"
            } catch (e: Exception) {
                "ITA"
            }
        } else {
            "ITA"
        }
        val identifyRes = try {
            RetrofitInstance.api.identifyImage(imageId, countryCode)
        } catch (e: Exception) {
            null
        }
        val annotation = identifyRes?.annotations?.firstOrNull()
        val fallbackSpecies = species.ifBlank { "Unknown Species" }
        val label = annotation?.label?.takeIf { it.isNotBlank() } ?: fallbackSpecies
        val taxonomy = annotation?.taxonomy
        if (annotation?.label?.isNotBlank() == true) {
            onComplete(true, "Species identified: ${annotation.label}")
        }
        val geohash = GeoFireUtils.getGeoHashForLocation(GeoLocation(latitude, longitude))
        val spotData = hashMapOf(
            "species" to mapOf(
                "label" to label,
                "taxonomy" to taxonomyToMap(taxonomy)
            ),
            "description" to description,
            "latitude" to latitude,
            "longitude" to longitude,
            "location_name" to locationName,
            "geohash" to geohash,
            "timestamp" to FieldValue.serverTimestamp(),
            "image_id" to imageId,
            "user_id" to (userId ?: "anonymous"),
            "sensor_data" to mapOf("steps_count" to steps, "compass_azimuth" to azimuth)
        )
        db.collection("spots").add(spotData).await()
        
        if (userId != null) {
            // Increment totalSpots, create doc if missing.
            db.collection("users").document(userId)
                .set(
                    mapOf("totalSpots" to FieldValue.increment(1)),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()
        }
        
        onComplete(true, "Spot added successfully!")
    } catch (e: Exception) {
        e.printStackTrace()
        onComplete(false, "Error: ${e.message}")
    }
}

fun taxonomyToMap(taxonomy: IdentifyTaxonomy?): Map<String, Any?> {
    if (taxonomy == null) return emptyMap()
    return mapOf(
        "id" to taxonomy.id,
        "class" to taxonomy.className,
        "order" to taxonomy.order,
        "family" to taxonomy.family,
        "genus" to taxonomy.genus,
        "species" to taxonomy.species
    )
}
