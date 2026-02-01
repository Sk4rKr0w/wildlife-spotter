package com.wildlifespotter.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.location.LocationServices
import com.wildlifespotter.app.ui.components.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt
import kotlinx.coroutines.tasks.await

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // ===== Stati contapassi =====
    var totalSteps by remember { mutableStateOf(0) }
    var dailySteps by remember { mutableStateOf(0) }
    var lastSyncedSteps by remember { mutableStateOf(0) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd-MM-yyyy") }
    val todayKey = remember { LocalDate.now().format(dateFormatter) }

    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    LaunchedEffect(Unit) {
        val sharedPreferences = context.getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        dailySteps = sharedPreferences.getInt("dailySteps_$todayKey", 0)
        lastSyncedSteps = sharedPreferences.getInt("lastSynced_$todayKey", 0)
        val userId = auth.currentUser?.uid
        if (userId != null) {
            try {
                val doc = db.collection("users")
                    .document(userId)
                    .collection("steps")
                    .document(todayKey)
                    .get()
                    .await()
                val remoteDaily = doc.getLong("dailySteps")?.toInt() ?: 0
                if (remoteDaily > dailySteps) {
                    dailySteps = remoteDaily
                }
                val pending = dailySteps - lastSyncedSteps
                if (pending > 0) {
                    db.collection("users")
                        .document(userId)
                        .set(
                            mapOf(
                                "totalSteps" to FieldValue.increment(pending.toLong()),
                                "stepsUpdatedAt" to FieldValue.serverTimestamp()
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                        .await()
                    db.collection("users")
                        .document(userId)
                        .collection("steps")
                        .document(todayKey)
                        .set(
                            mapOf(
                                "dailySteps" to FieldValue.increment(pending.toLong()),
                                "updatedAt" to FieldValue.serverTimestamp()
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                        .await()
                    lastSyncedSteps = dailySteps
                    sharedPreferences.edit {
                        putInt("lastSynced_$todayKey", lastSyncedSteps)
                        putInt("dailySteps_$todayKey", dailySteps)
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "Failed to sync pending steps on start", e)
            }
        }
    }

    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val magnetSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    // ===== Parametri contapassi =====
    var lastStepTime by remember { mutableStateOf(0L) }
    val stepThreshold = 13f
    val minStepInterval = 300L

    // ===== Stati bussola =====
    var azimuth by remember { mutableStateOf(0f) }
    var direction by remember { mutableStateOf("N") }
    val gravityValues = FloatArray(3)
    val magneticValues = FloatArray(3)
    val rotationMatrix = FloatArray(9)
    val orientationValues = FloatArray(3)

    // ===== Stati posizione =====
    var latitude by remember { mutableStateOf(0.0) }
    var longitude by remember { mutableStateOf(0.0) }
    var address by remember { mutableStateOf("Unknown") }

    val sensorListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]

                        val accel = sqrt(x * x + y * y + z * z)
                        val now = System.currentTimeMillis()
                        if (accel > stepThreshold && now - lastStepTime > minStepInterval) {
                            totalSteps += 1
                            dailySteps += 1
                            lastStepTime = now
                        }

                        System.arraycopy(event.values, 0, gravityValues, 0, 3)
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, magneticValues, 0, 3)
                    }
                }

                val success = SensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, magneticValues)
                if (success) {
                    SensorManager.getOrientation(rotationMatrix, orientationValues)
                    azimuth = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                    if (azimuth < 0) azimuth += 360f
                    direction = getDirection(azimuth)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
    }

    DisposableEffect(sensorManager) {
        accelSensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
        magnetSensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }

        onDispose {
            sensorManager.unregisterListener(sensorListener)
        }
    }

    LaunchedEffect(Unit) {
        val sharedPreferences = context.getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        val lastDayKey = sharedPreferences.getString("lastDayKey", null)
        if (lastDayKey != null && lastDayKey != todayKey) {
            val prevDaily = sharedPreferences.getInt("dailySteps_$lastDayKey", 0)
            val prevSynced = sharedPreferences.getInt("lastSynced_$lastDayKey", 0)
            val delta = prevDaily - prevSynced
            val userId = auth.currentUser?.uid
            if (delta > 0 && userId != null) {
                try {
                    db.collection("users")
                        .document(userId)
                        .set(
                            mapOf(
                                "totalSteps" to FieldValue.increment(delta.toLong()),
                                "stepsUpdatedAt" to FieldValue.serverTimestamp()
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                        .await()
                    db.collection("users")
                        .document(userId)
                        .collection("steps")
                        .document(lastDayKey)
                        .set(
                            mapOf(
                                "dailySteps" to FieldValue.increment(delta.toLong()),
                                "updatedAt" to FieldValue.serverTimestamp()
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                        .await()
                } catch (e: Exception) {
                    Log.e("HomeScreen", "Failed to sync previous day steps", e)
                }
            }
            dailySteps = 0
            lastSyncedSteps = 0
            sharedPreferences.edit {
                putInt("dailySteps_$todayKey", 0)
                putInt("lastSynced_$todayKey", 0)
            }
        }
        sharedPreferences.edit { putString("lastDayKey", todayKey) }
    }

    LaunchedEffect(dailySteps) {
        val sharedPreferences = context.getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit {
            putInt("dailySteps_$todayKey", dailySteps)
            putInt("lastSynced_$todayKey", lastSyncedSteps)
        }
        val delta = dailySteps - lastSyncedSteps
        if (delta < 25) return@LaunchedEffect
        val userId = auth.currentUser?.uid ?: return@LaunchedEffect
        val today = todayKey
        try {
            db.collection("users")
                .document(userId)
                .set(
                    mapOf(
                        "totalSteps" to FieldValue.increment(delta.toLong()),
                        "stepsUpdatedAt" to FieldValue.serverTimestamp()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()

            db.collection("users")
                .document(userId)
                .collection("steps")
                .document(today)
                .set(
                    mapOf(
                        "dailySteps" to FieldValue.increment(delta.toLong()),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()

            lastSyncedSteps = dailySteps
            sharedPreferences.edit {
                putInt("lastSynced_$today", lastSyncedSteps)
            }
        } catch (e: Exception) {
            Log.e("HomeScreen", "Failed to sync steps", e)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val delta = dailySteps - lastSyncedSteps
            if (delta <= 0) return@onDispose
            val userId = auth.currentUser?.uid ?: return@onDispose
            try {
                db.collection("users")
                    .document(userId)
                    .set(
                        mapOf(
                            "totalSteps" to FieldValue.increment(delta.toLong()),
                            "stepsUpdatedAt" to FieldValue.serverTimestamp()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .addOnSuccessListener { }
                db.collection("users")
                    .document(userId)
                    .collection("steps")
                    .document(todayKey)
                    .set(
                        mapOf(
                            "dailySteps" to FieldValue.increment(delta.toLong()),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .addOnSuccessListener { }
                val sharedPreferences = context.getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
                sharedPreferences.edit {
                    putInt("dailySteps_$todayKey", dailySteps)
                    putInt("lastSynced_$todayKey", dailySteps)
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "Failed to sync steps on dispose", e)
            }
        }
    }

    // ===== Recupera posizione =====
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    LaunchedEffect(Unit) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let {
                    latitude = it.latitude
                    longitude = it.longitude

                    val geocoder = Geocoder(context)
                    val list = geocoder.getFromLocation(latitude, longitude, 1)
                    if (!list.isNullOrEmpty()) {
                        address = "${list[0].locality ?: ""}, ${list[0].countryName ?: ""}"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(Unit) {
        val userId = auth.currentUser?.uid ?: return@LaunchedEffect
        try {
            val doc = db.collection("users")
                .document(userId)
                .collection("steps")
                .document(todayKey)
                .get()
                .await()
            val remoteDaily = doc.getLong("dailySteps")?.toInt() ?: 0
            if (remoteDaily > dailySteps) {
                dailySteps = remoteDaily
                if (lastSyncedSteps < dailySteps) {
                    lastSyncedSteps = dailySteps
                }
                val sharedPreferences = context.getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
                sharedPreferences.edit {
                    putInt("dailySteps_$todayKey", dailySteps)
                    putInt("lastSynced_$todayKey", lastSyncedSteps)
                }
            }
        } catch (e: Exception) {
            Log.e("HomeScreen", "Failed to load daily steps", e)
        }
    }

    // ===== UI Compose =====
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A2332), Color(0xFF2D3E50), Color(0xFF1A2332))
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Sfondo animato con onde
        AnimatedWaveBackground(
            primaryColor = Color(0xFF4CAF50),
            secondaryColor = Color(0xFF2EA333)
        )

        // Particelle fluttuanti
        FloatingParticles(particleCount = 15)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Title
            Text(
                text = "Activity Dashboard",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32),
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // StepCounter Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF374B5E).copy(alpha = 0.8f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.DirectionsWalk,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Steps Today",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                    IconButton(
                        onClick = {
                                dailySteps = 0
                                lastSyncedSteps = 0
                                val sharedPreferences = context.getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
                                sharedPreferences.edit {
                                    putInt("dailySteps_$todayKey", dailySteps)
                                    putInt("lastSynced_$todayKey", lastSyncedSteps)
                                }
                                Toast.makeText(context, "Steps reset!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Reset",
                                tint = Color(0xFFFFC107)
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Indicatore circolare animato
                    CircularStepIndicator(
                        currentSteps = dailySteps,
                        goalSteps = 10000,
                        size = 150f
                    )

                    Spacer(Modifier.height(16.dp))

                    // Grafico a onde
                    WaveActivityGraph()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card bussola con canvas animato
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF374B5E).copy(alpha = 0.8f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Explore,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Compass",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // Bussola animata
                    AnimatedCompass(azimuth = azimuth, size = 200f)

                    Spacer(Modifier.height(20.dp))

                    // Info direzione
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2D3E50))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Direction",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                            Text(
                                direction,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Azimuth",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                            Text(
                                "%.1f°".format(azimuth),
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card posizione
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF374B5E).copy(alpha = 0.8f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Location",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Indirizzo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2D3E50))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            address,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Coordinate
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2D3E50))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Latitude",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                            Text(
                                "%.5f".format(latitude),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Longitude",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                            Text(
                                "%.5f".format(longitude),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

private fun getDirection(azimuth: Float): String {
    return when (azimuth) {
        in 337.5..360.0, in 0f..22.5f -> "N"
        in 22.5..67.5 -> "NE"
        in 67.5..112.5 -> "E"
        in 112.5..157.5 -> "SE"
        in 157.5..202.5 -> "S"
        in 202.5..247.5 -> "SW"
        in 247.5..292.5 -> "W"
        in 292.5..337.5 -> "NW"
        else -> ""
    }
}
