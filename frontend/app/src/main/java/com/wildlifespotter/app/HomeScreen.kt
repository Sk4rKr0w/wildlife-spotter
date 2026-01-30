package com.wildlifespotter.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.google.android.gms.location.LocationServices
import kotlin.math.sqrt

@Composable
fun HomeScreen() {
    val context = LocalContext.current

    // ===== Stati contapassi =====
    var totalSteps by remember { mutableStateOf(0) }
    var previousTotalSteps: Int by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val sharedPreferences = context.getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        previousTotalSteps = sharedPreferences.getFloat("key1", 0f).toInt()
        Log.d("HomeScreen", "Previous steps: $previousTotalSteps")
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
                    // ===== Contapassi =====
                    Sensor.TYPE_ACCELEROMETER -> {
                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]

                        // Contapassi
                        val accel = sqrt(x * x + y * y + z * z)
                        val now = System.currentTimeMillis()
                        if (accel > stepThreshold && now - lastStepTime > minStepInterval) {
                            totalSteps += 1
                            lastStepTime = now
                        }

                        // Bussola
                        System.arraycopy(event.values, 0, gravityValues, 0, 3)
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, magneticValues, 0, 3)
                    }
                }

                // Calcolo azimuth
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

    // ===== Registra listener sensori =====
    DisposableEffect(sensorManager) {
        accelSensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
        magnetSensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }

        onDispose {
            sensorManager.unregisterListener(sensorListener)
        }
    }

    val currentSteps = (totalSteps - previousTotalSteps).coerceAtLeast(0)

    // ===== Recupera posizione =====
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    LaunchedEffect(Unit) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let {
                    latitude = it.latitude
                    longitude = it.longitude

                    // Geocoder per indirizzo leggibile
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

    // ===== UI Compose =====
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Contapassi
        Text(
            text = "Steps taken: $currentSteps",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .clickable {
                    Toast.makeText(context, "Long tap to reset steps", Toast.LENGTH_SHORT).show()
                }
                .padding(16.dp)
        )

        Button(onClick = {
            previousTotalSteps = totalSteps
            saveData(context, previousTotalSteps)
        }) {
            Text("Reset Steps")
        }

        Spacer(modifier = Modifier.height(16.dp))

        CircularProgressIndicator(
            progress = currentSteps / 10000f.coerceAtLeast(1f),
            modifier = Modifier.size(150.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Bussola
        Text("Azimuth: %.1f°".format(azimuth), style = MaterialTheme.typography.headlineSmall)
        Text("Direction: $direction", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(32.dp))

        // Posizione
        Text("Lat: %.5f, Lon: %.5f".format(latitude, longitude), style = MaterialTheme.typography.bodyLarge)
        Text("Address: $address", style = MaterialTheme.typography.bodyLarge)
    }
}

private fun saveData(context: Context, previousTotalSteps: Int) {
    val sharedPreferences = context.getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
    sharedPreferences.edit {
        putFloat("key1", previousTotalSteps.toFloat())
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
