package com.wildlifespotter.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Data class per ultimo avvistamento
data class LastSpot(
    val species: String = "",
    val description: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val imageUrl: String = "",
    val timestamp: Timestamp? = null
)

@Composable
fun HomeScreen(navController: NavHostController) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // Sensori
    var currentSteps by remember { mutableFloatStateOf(0f) }
    var initialSteps by remember { mutableFloatStateOf(-1f) }
    var currentAzimuth by remember { mutableFloatStateOf(0f) }

    // Ultimo avvistamento
    var lastSpot by remember { mutableStateOf<LastSpot?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Firestore snapshot listener per ultimo avvistamento live
    LaunchedEffect(Unit) {
        val userId = auth.currentUser?.uid ?: return@LaunchedEffect
        db.collection("spots")
            .whereEqualTo("user_id", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    println("Firestore error: $error")
                    return@addSnapshotListener
                }
                val doc = snapshot?.documents?.firstOrNull()
                if (doc != null) {
                    val imageId = doc.getString("image_id") ?: ""
                    lastSpot = LastSpot(
                        species = doc.getString("species") ?: "Unknown",
                        description = doc.getString("description") ?: "No description",
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        imageUrl = if (imageId.isNotEmpty()) "http://10.0.2.2:3000/images/$imageId" else "",
                        timestamp = doc.getTimestamp("timestamp")
                    )
                }
                isLoading = false
            }
    }

    // Sensori setup
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Step Counter
        val stepListener = object : SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
                    if (initialSteps < 0) initialSteps = event.values[0]
                    currentSteps = event.values[0] - initialSteps
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // Compass
        val orientationListener = object : SensorEventListener {
            val accelerometerReading = FloatArray(3)
            val magnetometerReading = FloatArray(3)
            val rotationMatrix = FloatArray(9)
            val orientationAngles = FloatArray(3)

            override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                when (event?.sensor?.type) {
                    Sensor.TYPE_ACCELEROMETER -> System.arraycopy(event.values, 0, accelerometerReading, 0, 3)
                    Sensor.TYPE_MAGNETIC_FIELD -> System.arraycopy(event.values, 0, magnetometerReading, 0, 3)
                }
                SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                currentAzimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
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

    // UI
    val backgroundGradient = Brush.verticalGradient(listOf(Color(0xFF1A2332), Color(0xFF2D3E50)))

    Box(modifier = Modifier.fillMaxSize().background(backgroundGradient)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Text(
                text = "Wildlife Activity",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Ultimo avvistamento
            if (isLoading) {
                LoadingCard()
            } else if (lastSpot != null) {
                LastSightingCard(lastSpot!!)
            } else {
                NoSightingsCard()
            }

            // Compass
            CompassCard(currentAzimuth)

            // Statistiche passi
            StatCard(
                icon = Icons.Default.DirectionsWalk,
                title = "Steps Today",
                value = currentSteps.roundToInt().toString(),
                backgroundColor = Color(0xFF374B5E),
                iconColor = Color(0xFF4CAF50)
            )
        }
    }
}

// Composable per loading card
@Composable
fun LoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF374B5E))
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(300.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF4CAF50))
        }
    }
}

// Composable quando non ci sono avvistamenti
@Composable
fun NoSightingsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF374B5E))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color(0xFF4CAF50))
            Spacer(modifier = Modifier.height(16.dp))
            Text("No sightings yet", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text("Start exploring!", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}

// Ultimo avvistamento
@Composable
fun LastSightingCard(spot: LastSpot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF374B5E))
    ) {
        Column {
            if (spot.imageUrl.isNotEmpty()) {
                Image(
                    // painter = rememberAsyncImagePainter(spot.imageUrl),
                    painter = painterResource(id = R.drawable.gatto),
                    contentDescription = "Last sighting",
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(Color(0xFF2D3E50)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(80.dp), tint = Color(0xFF4CAF50))
                }
            }

            Column(modifier = Modifier.padding(20.dp)) {
                Text("Last Sighting", style = MaterialTheme.typography.labelLarge, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Pets, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(spot.species, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (spot.description.isNotEmpty()) {
                    Text(spot.description, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFBBBBBB))
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Divider(color = Color(0xFF4A5A6B), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Coordinates", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text("%.4f, %.4f".format(spot.latitude, spot.longitude), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    }
                    Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

// Compass card
@Composable
fun CompassCard(azimuth: Float) {
    val direction = getCardinalDirection(azimuth)
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF374B5E))) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Compass", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(20.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
                Surface(modifier = Modifier.size(140.dp), shape = CircleShape, color = Color(0xFF2D3E50)) {}
                Icon(Icons.Default.Navigation, contentDescription = "Compass needle", modifier = Modifier.size(80.dp).rotate(-azimuth), tint = Color(0xFF4CAF50))
                Text("N", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(direction, style = MaterialTheme.typography.titleLarge, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
            Text("${azimuth.roundToInt()}°", style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
    }
}

// Stat card
@Composable
fun StatCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, modifier: Modifier = Modifier, backgroundColor: Color = Color(0xFF374B5E), iconColor: Color = Color(0xFF4CAF50)) {
    Card(modifier = modifier.height(120.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = backgroundColor)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(32.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

// Helper per direzione cardinale
fun getCardinalDirection(azimuth: Float): String {
    val normalized = (azimuth + 360) % 360
    return when {
        normalized >= 337.5 || normalized < 22.5 -> "North"
        normalized >= 22.5 && normalized < 67.5 -> "North-East"
        normalized >= 67.5 && normalized < 112.5 -> "East"
        normalized >= 112.5 && normalized < 157.5 -> "South-East"
        normalized >= 157.5 && normalized < 202.5 -> "South"
        normalized >= 202.5 && normalized < 247.5 -> "South-West"
        normalized >= 247.5 && normalized < 292.5 -> "West"
        normalized >= 292.5 && normalized < 337.5 -> "North-West"
        else -> "North"
    }
}
