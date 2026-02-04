package com.wildlifespotter.app

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
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
import com.google.android.gms.location.LocationServices
import com.wildlifespotter.app.ui.components.*
import com.wildlifespotter.app.models.HomeViewModel
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    onNavigateToMap: () -> Unit,
    onNavigateToHistory: () -> Unit
){
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val uiState = homeViewModel.uiState

    // ===== Compass states =====
    var azimuth by remember { mutableStateOf(0f) }
    var direction by remember { mutableStateOf("N") }
    val gravityValues = FloatArray(3)
    val magneticValues = FloatArray(3)
    val rotationMatrix = FloatArray(9)
    val orientationValues = FloatArray(3)

    // ===== Position States =====
    var latitude by remember { mutableStateOf(0.0) }
    var longitude by remember { mutableStateOf(0.0) }
    var address by remember { mutableStateOf("Unknown") }

    val sensorManager = remember {
        context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
    }
    val accelSensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
    val magnetSensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_MAGNETIC_FIELD)

    val sensorListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
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
        accelSensor?.let { sensorManager.registerListener(sensorListener, it, android.hardware.SensorManager.SENSOR_DELAY_UI) }
        magnetSensor?.let { sensorManager.registerListener(sensorListener, it, android.hardware.SensorManager.SENSOR_DELAY_UI) }

        onDispose {
            sensorManager.unregisterListener(sensorListener)
        }
    }

    // ===== Gathering Position =====
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var showLocationAlert by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    latitude = loc.latitude
                    longitude = loc.longitude

                    val geocoder = Geocoder(context)
                    val list = geocoder.getFromLocation(latitude, longitude, 1)
                    if (!list.isNullOrEmpty()) {
                        address = "${list[0].locality ?: ""}, ${list[0].countryName ?: ""}"
                    }
                } else {
                    showLocationAlert = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    if (showLocationAlert) {
        AlertDialog(
            onDismissRequest = { showLocationAlert = false },
            title = { Text("Location Required") },
            text = { Text("Be sure to turn on your location") },
            confirmButton = {
                Button(onClick = { showLocationAlert = false }) {
                    Text("OK")
                }
            }
        )
    }


    // ===== UI Compose =====
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A2332), Color(0xFF2D3E50), Color(0xFF1A2332))
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedWaveBackground(
            primaryColor = Color(0xFF4CAF50),
            secondaryColor = Color(0xFF2EA333)
        )

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

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2D3E50).copy(alpha = 0.85f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.align(Alignment.TopStart),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Pets,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Total Spots (All Time)",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (uiState.isLoadingSteps) {
                        CircularProgressIndicator(
                            color = Color(0xFF4CAF50),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(48.dp)
                        )
                    } else {
                        Text(
                            uiState.totalSpots.toString(),
                            color = Color(0xFF4CAF50),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onNavigateToMap() },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2D3E50).copy(alpha = 0.85f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "View Nearby Spots on Map",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

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
                    }

                    Spacer(Modifier.height(24.dp))

                    if (uiState.isLoadingSteps) {
                        CircularProgressIndicator(
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.size(150.dp)
                        )
                    } else {
                        CircularStepIndicator(
                            currentSteps = uiState.dailySteps,
                            goalSteps = 10000,
                            size = 150f
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    WaveActivityGraph()
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onNavigateToHistory() },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2D3E50).copy(alpha = 0.85f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.align(Alignment.TopStart),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Total Steps (All Time)",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (uiState.isLoadingSteps) {
                        CircularProgressIndicator(
                            color = Color(0xFF4CAF50),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(48.dp)
                        )
                    } else {
                        Text(
                            uiState.totalSteps.toString(),
                            color = Color(0xFF4CAF50),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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

                    AnimatedCompass(azimuth = azimuth, size = 200f)

                    Spacer(Modifier.height(20.dp))

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
