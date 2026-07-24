package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.data.ElevationGrid
import com.example.geospatial.GeoSpatialLibrary
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.GroundOverlay
import com.google.android.gms.maps.model.GroundOverlayOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlin.math.cos

@Composable
fun TerrainGoogleMapScreen(
    terrainBitmap: Bitmap?,
    grid: ElevationGrid,
    metadata: GeoSpatialLibrary.GeoSpatialMetadata,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = rememberManagedMapView()
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    var groundOverlay by remember { mutableStateOf<GroundOverlay?>(null) }
    var mapCenter by remember { mutableStateOf(LatLng(39.5, -98.35)) }
    var manualBounds by remember(metadata.siteName) {
        mutableStateOf<GeoSpatialLibrary.GeographicBounds?>(null)
    }
    var overlayOpacity by rememberSaveable { mutableFloatStateOf(0.72f) }
    var mapType by rememberSaveable { mutableStateOf(GoogleMap.MAP_TYPE_HYBRID) }
    var showBoundsDialog by rememberSaveable { mutableStateOf(false) }
    val activeBounds = manualBounds ?: metadata.bounds

    DisposableEffect(mapView) {
        mapView.getMapAsync { map ->
            googleMap = map
            map.mapType = mapType
            map.uiSettings.isCompassEnabled = true
            map.uiSettings.isMapToolbarEnabled = false
            map.uiSettings.isZoomControlsEnabled = false
            map.uiSettings.isMyLocationButtonEnabled = false
            map.setOnCameraIdleListener { mapCenter = map.cameraPosition.target }
            val hasLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (hasLocation) {
                runCatching {
                    map.isMyLocationEnabled = true
                    map.uiSettings.isMyLocationButtonEnabled = true
                }
            }
        }
        onDispose {
            groundOverlay?.remove()
            googleMap = null
        }
    }

    LaunchedEffect(googleMap, mapType) {
        googleMap?.mapType = mapType
    }

    LaunchedEffect(googleMap, terrainBitmap, activeBounds, overlayOpacity) {
        val map = googleMap ?: return@LaunchedEffect
        groundOverlay?.remove()
        groundOverlay = null
        val bitmap = terrainBitmap?.takeIf { !it.isRecycled } ?: return@LaunchedEffect
        val bounds = activeBounds ?: return@LaunchedEffect
        val mapBounds = bounds.toLatLngBounds()
        groundOverlay = map.addGroundOverlay(
            GroundOverlayOptions()
                .image(BitmapDescriptorFactory.fromBitmap(bitmap))
                .positionFromBounds(mapBounds)
                .transparency(1f - overlayOpacity.coerceIn(0.05f, 1f))
                .zIndex(4f)
                .clickable(false),
        )
        mapView.post {
            runCatching { map.animateCamera(CameraUpdateFactory.newLatLngBounds(mapBounds, 72)) }
                .onFailure { map.moveCamera(CameraUpdateFactory.newLatLngZoom(mapBounds.center, 16f)) }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth(0.96f),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            Icons.Default.Layers,
                            contentDescription = null,
                            modifier = Modifier.padding(9.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Rendered LiDAR overlay", fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                BuildConfig.MAPS_API_KEY.isBlank() -> "MAPS_API_KEY is missing from .env/local.properties"
                                terrainBitmap == null -> "Render or import a terrain layer first"
                                activeBounds == null -> "Position the LAZ image using map center or exact bounds"
                                manualBounds != null -> "Manually aligned on Google Maps"
                                else -> "Using geographic bounds from the terrain file"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (BuildConfig.MAPS_API_KEY.isBlank()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MapTypeChip("Map", GoogleMap.MAP_TYPE_NORMAL, mapType) { mapType = it }
                    MapTypeChip("Satellite", GoogleMap.MAP_TYPE_SATELLITE, mapType) { mapType = it }
                    MapTypeChip("Hybrid", GoogleMap.MAP_TYPE_HYBRID, mapType) { mapType = it }
                    MapTypeChip("Terrain", GoogleMap.MAP_TYPE_TERRAIN, mapType) { mapType = it }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(0.96f),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Overlay opacity", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    Text("${(overlayOpacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = overlayOpacity,
                    onValueChange = { overlayOpacity = it },
                    valueRange = 0.1f..1f,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            manualBounds = boundsCenteredAt(
                                center = mapCenter,
                                widthMeters = (grid.width - 1).coerceAtLeast(1) * grid.cellSizeMeters,
                                heightMeters = (grid.height - 1).coerceAtLeast(1) * grid.cellSizeMeters,
                            )
                        },
                        enabled = terrainBitmap != null,
                    ) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Place at map center")
                    }
                    OutlinedButton(onClick = { showBoundsDialog = true }) {
                        Icon(Icons.Default.EditLocationAlt, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Exact bounds")
                    }
                    if (metadata.bounds != null && manualBounds != null) {
                        OutlinedButton(onClick = { manualBounds = null }) {
                            Icon(Icons.Default.MyLocation, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Use file bounds")
                        }
                    }
                }
            }
        }
    }

    if (showBoundsDialog) {
        BoundsEditorDialog(
            initial = activeBounds,
            onDismiss = { showBoundsDialog = false },
            onApply = {
                manualBounds = it
                showBoundsDialog = false
            },
        )
    }
}

@Composable
private fun MapTypeChip(
    label: String,
    type: Int,
    selectedType: Int,
    onSelected: (Int) -> Unit,
) {
    FilterChip(
        selected = type == selectedType,
        onClick = { onSelected(type) },
        label = { Text(label) },
    )
}

@Composable
private fun BoundsEditorDialog(
    initial: GeoSpatialLibrary.GeographicBounds?,
    onDismiss: () -> Unit,
    onApply: (GeoSpatialLibrary.GeographicBounds) -> Unit,
) {
    var south by remember(initial) { mutableStateOf(initial?.minLat?.toString().orEmpty()) }
    var north by remember(initial) { mutableStateOf(initial?.maxLat?.toString().orEmpty()) }
    var west by remember(initial) { mutableStateOf(initial?.minLon?.toString().orEmpty()) }
    var east by remember(initial) { mutableStateOf(initial?.maxLon?.toString().orEmpty()) }
    val parsed = remember(south, north, west, east) {
        val s = south.toDoubleOrNull()
        val n = north.toDoubleOrNull()
        val w = west.toDoubleOrNull()
        val e = east.toDoubleOrNull()
        if (s != null && n != null && w != null && e != null &&
            s in -90.0..90.0 && n in -90.0..90.0 &&
            w in -180.0..180.0 && e in -180.0..180.0 && n > s && e > w
        ) {
            GeoSpatialLibrary.GeographicBounds(s, n, w, e)
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Align LAZ overlay") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter the WGS84 footprint. This is required when a LAZ file does not expose geographic bounds the app can recognize.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoordinateField("South", south, { south = it }, Modifier.weight(1f))
                    CoordinateField("North", north, { north = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoordinateField("West", west, { west = it }, Modifier.weight(1f))
                    CoordinateField("East", east, { east = it }, Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onApply) }, enabled = parsed != null) {
                Text("Apply")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CoordinateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(16)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun rememberManagedMapView(): MapView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember(context) { MapView(context).apply { onCreate(Bundle()) } }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_LOW_MEMORY -> mapView.onLowMemory()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}

private fun GeoSpatialLibrary.GeographicBounds.toLatLngBounds(): LatLngBounds =
    LatLngBounds(
        LatLng(minLat, minLon),
        LatLng(maxLat, maxLon),
    )

private fun boundsCenteredAt(
    center: LatLng,
    widthMeters: Float,
    heightMeters: Float,
): GeoSpatialLibrary.GeographicBounds {
    val safeWidth = widthMeters.coerceAtLeast(1f).toDouble()
    val safeHeight = heightMeters.coerceAtLeast(1f).toDouble()
    val halfLatDegrees = safeHeight / 111_320.0 / 2.0
    val longitudeMetersPerDegree = (111_320.0 * cos(Math.toRadians(center.latitude)))
        .coerceAtLeast(10_000.0)
    val halfLonDegrees = safeWidth / longitudeMetersPerDegree / 2.0
    return GeoSpatialLibrary.GeographicBounds(
        minLat = (center.latitude - halfLatDegrees).coerceAtLeast(-90.0),
        maxLat = (center.latitude + halfLatDegrees).coerceAtMost(90.0),
        minLon = (center.longitude - halfLonDegrees).coerceAtLeast(-180.0),
        maxLon = (center.longitude + halfLonDegrees).coerceAtMost(180.0),
    )
}
