package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.NormalizedRasterBounds
import com.example.geospatial.GeoSpatialLibrary
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GroundOverlay
import com.google.maps.android.compose.GroundOverlayPosition
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * Google Maps basemap with LiDAR/hillshade [bitmap] as a ground overlay.
 * Pan/zoom is the map; overlay stays georeferenced to [metadata.bounds].
 */
@Composable
fun TerrainMapOverlay(
    bitmap: Bitmap?,
    isRendering: Boolean,
    metadata: GeoSpatialLibrary.GeoSpatialMetadata,
    overlayOpacity: Float,
    onOverlayOpacityChanged: (Float) -> Unit,
    onViewportChanged: (NormalizedRasterBounds, Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val geoBounds = metadata.bounds
    val siteBounds = remember(geoBounds) {
        geoBounds?.let {
            LatLngBounds(
                LatLng(it.minLat, it.minLon),
                LatLng(it.maxLat, it.maxLon),
            )
        }
    }
    val center = remember(siteBounds) {
        siteBounds?.center ?: LatLng(43.1205, -124.4085)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, 15f)
    }
    var mapType by remember { mutableStateOf(MapType.HYBRID) }
    var opacity by remember(overlayOpacity) { mutableFloatStateOf(overlayOpacity.coerceIn(0.15f, 1f)) }

    LaunchedEffect(siteBounds) {
        val b = siteBounds ?: return@LaunchedEffect
        runCatching {
            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(b, 72))
        }.onFailure {
            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(b.center, 15f))
        }
    }

    // Report visible region in normalized site coordinates for auto-detail refine.
    LaunchedEffect(cameraPositionState, siteBounds) {
        val site = siteBounds ?: return@LaunchedEffect
        snapshotFlow { cameraPositionState.position to cameraPositionState.isMoving }
            .map { (pos, moving) -> Triple(pos.target.latitude, pos.target.longitude, pos.zoom) to moving }
            .distinctUntilChanged()
            .collect { (cam, moving) ->
                if (moving) return@collect
                val (lat, lon, zoom) = cam
                val visible = approximateVisibleBounds(lat, lon, zoom)
                val normalized = latLngBoundsToNormalized(visible, site)
                // Rough zoom relative to site fit (~15–17 typical for small plots)
                val relativeZoom = (zoom / 14f).coerceIn(1f, 32f)
                onViewportChanged(normalized, relativeZoom)
            }
    }

    val overlayImage = remember(bitmap) {
        bitmap
            ?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
            ?.let { BitmapDescriptorFactory.fromBitmap(it) }
    }

    Box(modifier = modifier.fillMaxSize().testTag("terrain_map_overlay")) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = mapType,
                isBuildingEnabled = false,
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                compassEnabled = true,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = false,
            ),
        ) {
            if (siteBounds != null && overlayImage != null) {
                GroundOverlay(
                    position = GroundOverlayPosition.create(siteBounds),
                    image = overlayImage,
                    // Maps transparency: 0 = opaque, 1 = invisible
                    transparency = (1f - opacity).coerceIn(0f, 0.95f),
                    clickable = false,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!BuildConfig.HAS_MAPS_API_KEY) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                ) {
                    Text(
                        "Maps key missing — add MAPS_API_KEY to secrets.properties and rebuild.",
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            if (siteBounds == null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.95f),
                ) {
                    Text(
                        "This layer has no geographic bounds — map basemap needs a georeferenced DEM/LAZ.",
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 4.dp,
            ) {
                Column(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Google Maps basemap + hillshade overlay",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "Hybrid" to MapType.HYBRID,
                            "Terrain" to MapType.TERRAIN,
                            "Sat" to MapType.SATELLITE,
                            "Road" to MapType.NORMAL,
                        ).forEach { (label, type) ->
                            FilterChip(
                                selected = mapType == type,
                                onClick = { mapType = type },
                                label = { Text(label) },
                            )
                        }
                    }
                    Text(
                        "Hillshade ${ (opacity * 100).toInt() }%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = opacity,
                        onValueChange = {
                            opacity = it
                            onOverlayOpacityChanged(it)
                        },
                        valueRange = 0.15f..1f,
                    )
                }
            }
        }

        if (isRendering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

/** Rough visible lat/lon box from camera center + zoom (Web Mercator approximation). */
private fun approximateVisibleBounds(lat: Double, lon: Double, zoom: Float): LatLngBounds {
    val z = zoom.toDouble().coerceIn(1.0, 21.0)
    // Degrees of longitude visible at this zoom (full world at zoom 0 ≈ 360°)
    val lonSpan = 360.0 / Math.pow(2.0, z)
    val latRad = Math.toRadians(lat)
    val latSpan = lonSpan * cos(latRad).coerceAtLeast(0.2)
    val halfLon = lonSpan / 2.0
    val halfLat = latSpan / 2.0
    return LatLngBounds(
        LatLng(lat - halfLat, lon - halfLon),
        LatLng(lat + halfLat, lon + halfLon),
    )
}

private fun latLngBoundsToNormalized(
    visible: LatLngBounds,
    site: LatLngBounds,
): NormalizedRasterBounds {
    val siteLatSpan = (site.northeast.latitude - site.southwest.latitude).coerceAtLeast(1e-9)
    val siteLonSpan = (site.northeast.longitude - site.southwest.longitude).coerceAtLeast(1e-9)
    // Image row 0 = north → normalized top is north edge.
    val left = ((visible.southwest.longitude - site.southwest.longitude) / siteLonSpan)
        .coerceIn(0.0, 1.0)
    val right = ((visible.northeast.longitude - site.southwest.longitude) / siteLonSpan)
        .coerceIn(0.0, 1.0)
    val top = ((site.northeast.latitude - visible.northeast.latitude) / siteLatSpan)
        .coerceIn(0.0, 1.0)
    val bottom = ((site.northeast.latitude - visible.southwest.latitude) / siteLatSpan)
        .coerceIn(0.0, 1.0)
    return NormalizedRasterBounds(
        left = min(left, right),
        top = min(top, bottom),
        right = max(left, right),
        bottom = max(top, bottom),
    ).sanitized()
}
