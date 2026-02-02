package com.wildlifespotter.app

import android.annotation.SuppressLint
import android.graphics.drawable.BitmapDrawable
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
import android.location.Location
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wildlifespotter.app.models.MapViewModel
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
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    val mapViewModel: MapViewModel = viewModel()
    val spots = mapViewModel.spots
    val isLoading = mapViewModel.isLoading
    val errorMessage = mapViewModel.errorMessage
    var showLocationAlert by remember { mutableStateOf(false) }
    var selectedSpotGroup by remember { mutableStateOf<List<SpotLocation>?>(null) }
    var rangeKm by remember { mutableStateOf(5f) }
    var hasCenteredOnUser by remember { mutableStateOf(false) }
    var configReady by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var lastQueryLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var lastQueryRangeKm by remember { mutableStateOf<Float?>(null) }

    val minMoveMeters = 25f

    LaunchedEffect(Unit) {
        Configuration.getInstance().load(
            context,
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        )
        Configuration.getInstance().userAgentValue = context.packageName
        configReady = true
    }
    val lifecycleObserver = remember {
        LifecycleEventObserver { _, event ->
            val mv = mapViewRef ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> mv.onResume()
                Lifecycle.Event.ON_PAUSE -> mv.onPause()
                else -> {}
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapViewRef) {
        val mv = mapViewRef
        if (mv == null) return@DisposableEffect onDispose { }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            mv.onDetach()
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
        ).setMinUpdateDistanceMeters(minMoveMeters)
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

    LaunchedEffect(userLocation, rangeKm) {
        val loc = userLocation ?: return@LaunchedEffect
        val last = lastQueryLocation
        val lastRange = lastQueryRangeKm
        val rangeChanged = lastRange == null || lastRange != rangeKm
        if (!rangeChanged && last != null) {
            val distance = FloatArray(1)
            Location.distanceBetween(
                last.latitude,
                last.longitude,
                loc.latitude,
                loc.longitude,
                distance
            )
            if (distance[0] < minMoveMeters) return@LaunchedEffect
        }
        lastQueryLocation = loc
        lastQueryRangeKm = rangeKm
        mapViewModel.loadSpots(loc.latitude, loc.longitude, rangeKm)
    }

    LaunchedEffect(userLocation, spots, mapViewRef) {
        val mapView = mapViewRef ?: return@LaunchedEffect
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
            if (!hasCenteredOnUser) {
                mapView.controller.setZoom(16.5)
                hasCenteredOnUser = true
            }
        }

        val groupedSpots = spots.groupBy { spot ->
            "${(spot.latitude * 10000).toInt()}_${(spot.longitude * 10000).toInt()}"
        }

        groupedSpots.forEach { (_, spotsAtLocation) ->
            val firstSpot = spotsAtLocation.first()
            val hasOwn = spotsAtLocation.any { it.userId == currentUid }
            val markerColor = if (hasOwn) android.graphics.Color.parseColor("#1B5E20") else android.graphics.Color.BLUE
            
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
                !configReady -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF4CAF50)
                )
                else -> AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(16.5)
                            mapViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Range: ${rangeKm.toInt()} km",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = rangeKm,
                        onValueChange = { rangeKm = it },
                        valueRange = 1f..25f,
                        steps = 23
                    )
                }
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
