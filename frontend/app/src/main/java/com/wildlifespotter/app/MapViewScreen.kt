package com.wildlifespotter.app

import android.annotation.SuppressLint
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.compose.foundation.clickable
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
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
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
    val locationName: String,
    val userId: String
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
    var showLocationAlert by remember { mutableStateOf(false) }
    var selectedSpotGroup by remember { mutableStateOf<List<SpotLocation>?>(null) }

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
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                userLocation = GeoPoint(loc.latitude, loc.longitude)
            } else {
                showLocationAlert = true
                userLocation = GeoPoint(41.9028, 12.4964)
            }
        }

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

    LaunchedEffect(userLocation) {
        val loc = userLocation ?: return@LaunchedEffect
        try {
            // Geohash del centro (posizione utente), troncato a 5 caratteri (~5 km di lato).
            // Tutti gli spot che condividono lo stesso prefisso a 5 char sono nel raggio.
            val fullHash = GeoFireUtils.getGeoHashForLocation(
                GeoLocation(loc.latitude, loc.longitude)
            )
            val prefix = fullHash.substring(0, 5)

            // Range lessicografico sul prefisso: "abcde" .. "abcdf"
            // (ultimo carattere +1 nel charset base32 di geohash: 0-9 a-z esclusi a,i,l,o)
            val base32 = "0123456789bcdefghjkmnpqrstuvwxyz"
            val lastChar = prefix.last()
            val nextChar = base32[base32.indexOf(lastChar) + 1]
            val rangeMin = prefix
            val rangeMax = prefix.dropLast(1) + nextChar

            val snapshot = db.collection("spots")
                .orderBy("geohash")
                .startAt(rangeMin)
                .endBefore(rangeMax)
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
                        locationName = doc.getString("location_name") ?: "Unknown",
                        userId = doc.getString("user_id") ?: ""
                    )
                } catch (e: Exception) {
                    Log.e("MapViewScreen", "Error parsing spot", e)
                    null
                }
            }
        } catch (e: Exception) {
            errorMessage = e.message
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(userLocation, spots) {
        val currentUid = auth.currentUser?.uid
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

        val groupedSpots = spots.groupBy { spot ->
            "${(spot.latitude * 10000).toInt()}_${(spot.longitude * 10000).toInt()}"
        }

        groupedSpots.forEach { (_, spotsAtLocation) ->
            val firstSpot = spotsAtLocation.first()
            val hasOwn = spotsAtLocation.any { it.userId == currentUid }
            val markerColor = if (hasOwn) android.graphics.Color.GREEN else android.graphics.Color.BLUE
            
            val marker = Marker(mapView).apply {
                position = GeoPoint(firstSpot.latitude, firstSpot.longitude)
                title = if (spotsAtLocation.size > 1) {
                    "${spotsAtLocation.size} spots here"
                } else {
                    firstSpot.speciesLabel
                }
                snippet = if (spotsAtLocation.size == 1) firstSpot.locationName else null
                setOnMarkerClickListener { _, _ ->
                    if (spotsAtLocation.size == 1) {
                        onSpotClick(firstSpot.id)
                    } else {
                        selectedSpotGroup = spotsAtLocation
                    }
                    true
                }
                ContextCompat.getDrawable(context, android.R.drawable.ic_dialog_map)?.let {
                    it.setTint(markerColor)
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
                title = { Text("Nearby Spots") },
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

            if (showLocationAlert) {
                AlertDialog(
                    onDismissRequest = { showLocationAlert = false },
                    title = { Text("Location Required") },
                    text = { Text("Be sure to turn on your location, set by default on: Rome") },
                    confirmButton = {
                        Button(onClick = { showLocationAlert = false }) {
                            Text("OK")
                        }
                    }
                )
            }

            selectedSpotGroup?.let { spotsGroup ->
                AlertDialog(
                    onDismissRequest = { selectedSpotGroup = null },
                    title = { Text("Select a Spot") },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            spotsGroup.forEach { spot ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedSpotGroup = null
                                            onSpotClick(spot.id)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = spot.speciesLabel,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                        Text(
                                            text = if (spot.userId == auth.currentUser?.uid) {
                                                "By you"
                                            } else {
                                                "By another user"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { selectedSpotGroup = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
