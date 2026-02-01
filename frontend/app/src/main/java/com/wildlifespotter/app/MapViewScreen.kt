package com.wildlifespotter.app

import android.annotation.SuppressLint
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

data class SpotLocation(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val speciesLabel: String,
    val locationName: String
)

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapViewScreen(
    onBackClick: () -> Unit,
    onSpotClick: (spotId: String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var spots by remember { mutableStateOf<List<SpotLocation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        Configuration.getInstance().load(
            context,
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        )
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // MapView
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
        }
    }

    val lifecycleObserver = remember {
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            mapView.onDetach()
        }
    }

    /* ---------------- Realtime location ---------------- */
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    DisposableEffect(Unit) {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 2000L
        ).setMinUpdateDistanceMeters(1f)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation
                if (loc != null) {
                    userLocation = GeoPoint(loc.latitude, loc.longitude)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, context.mainLooper)

        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    /* ---------------- SPOTS  ---------------- */
    LaunchedEffect(Unit) {
        try {
            val userId = auth.currentUser?.uid
            if (userId != null) {
                val snapshot = db.collection("spots")
                    .whereEqualTo("user_id", userId)
                    .get()
                    .await()

                spots = snapshot.documents.mapNotNull { doc ->
                    try {
                        val lat = doc.getDouble("latitude") ?: return@mapNotNull null
                        val lng = doc.getDouble("longitude") ?: return@mapNotNull null
                        val speciesRaw = doc.get("species")
                        val speciesLabel = when (speciesRaw) {
                            is String -> speciesRaw
                            is Map<*, *> -> speciesRaw["label"] as? String ?: "Unknown"
                            else -> "Unknown"
                        }
                        SpotLocation(
                            id = doc.id,
                            latitude = lat,
                            longitude = lng,
                            speciesLabel = speciesLabel.replaceFirstChar { it.uppercase() },
                            locationName = doc.getString("location_name") ?: "Unknown"
                        )
                    } catch (e: Exception) {
                        Log.e("MapViewScreen", "Error parsing spot", e)
                        null
                    }
                }
            } else {
                errorMessage = "User not logged in"
            }
        } catch (e: Exception) {
            errorMessage = e.message
        } finally {
            isLoading = false
        }
    }

    /* ---------------- Updating Markers ---------------- */
    LaunchedEffect(userLocation, spots) {
        mapView.overlays.clear()

        // User Marker
        userLocation?.let { loc ->
            val userMarker = Marker(mapView).apply {
                position = loc
                title = "Your Location"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                ContextCompat.getDrawable(context, android.R.drawable.ic_menu_mylocation)?.let {
                    it.setTint(android.graphics.Color.RED)
                    icon = it as BitmapDrawable
                }
            }
            mapView.overlays.add(userMarker)
            mapView.controller.setCenter(loc)
        }

        // Spot Marker
        spots.forEach { spot ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(spot.latitude, spot.longitude)
                title = spot.speciesLabel
                snippet = spot.locationName
                setOnMarkerClickListener { _, _ ->
                    onSpotClick(spot.id)
                    true
                }
                ContextCompat.getDrawable(context, android.R.drawable.ic_dialog_map)?.let {
                    it.setTint(android.graphics.Color.BLUE)
                    icon = it as BitmapDrawable
                }
            }
            mapView.overlays.add(marker)
        }

        mapView.invalidate()
    }

    /* ---------------- UI ---------------- */
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Spots Map") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF4CAF50))
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            when {
                isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF4CAF50)
                )
                errorMessage != null -> Text(
                    text = errorMessage ?: "Error",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.Red
                )
                else -> AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
