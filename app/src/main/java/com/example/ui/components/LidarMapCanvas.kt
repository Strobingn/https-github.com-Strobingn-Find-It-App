package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TargetSignal
import com.example.geospatial.GeoSpatialLibrary

@Composable
fun LidarMapCanvas(
    bitmap: Bitmap?,
    isRendering: Boolean,
    sweepX: Float,
    sweepY: Float,
    loggedSignals: List<TargetSignal>,
    onSweepPositionChanged: (Float, Float) -> Unit,
    onStopSweeping: () -> Unit,
    gridSpacing: Float = 0f,
    geoMetadata: GeoSpatialLibrary.GeoSpatialMetadata,
    currentLat: Double?,
    currentLon: Double?,
    modifier: Modifier = Modifier,
) {
    // Cache ImageBitmap — recreating every drag frame can crash if Bitmap is mid-render
    val imageBitmap = remember(bitmap) {
        try {
            bitmap?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1C1D21))
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .testTag("lidar_map_canvas_container"),
    ) {
        if (imageBitmap != null && bitmap != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    // ONE gesture handler only — dual detectTap+detectDrag crashed on hold/drag
                    .pointerInput(onSweepPositionChanged, onStopSweeping) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            fun report(offset: Offset) {
                                val xPct = ((offset.x / w) * 100f).coerceIn(0f, 100f)
                                val yPct = ((offset.y / h) * 100f).coerceIn(0f, 100f)
                                onSweepPositionChanged(xPct, yPct)
                            }
                            report(down.position)
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) break
                                    if (change.positionChange() != Offset.Zero) {
                                        change.consume()
                                    }
                                    report(change.position)
                                }
                            } finally {
                                onStopSweeping()
                            }
                        }
                    }
                    .testTag("lidar_canvas"),
            ) {
                val canvasWidth = size.width.coerceAtLeast(1f)
                val canvasHeight = size.height.coerceAtLeast(1f)

                drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset(0, 0),
                    dstSize = IntSize(canvasWidth.toInt(), canvasHeight.toInt()),
                )

                // Search grid (avoid huge loops if spacing tiny)
                if (gridSpacing >= 1f) {
                    val cols = (100f / gridSpacing).toInt().coerceIn(1, 50)
                    val rows = (100f / gridSpacing).toInt().coerceIn(1, 50)
                    for (i in 1 until cols) {
                        val px = (i * gridSpacing / 100f) * canvasWidth
                        drawLine(
                            color = Color(0xFF29B6F6),
                            start = Offset(px, 0f),
                            end = Offset(px, canvasHeight),
                            strokeWidth = 1f,
                            alpha = 0.35f,
                        )
                    }
                    for (i in 1 until rows) {
                        val py = (i * gridSpacing / 100f) * canvasHeight
                        drawLine(
                            color = Color(0xFF29B6F6),
                            start = Offset(0f, py),
                            end = Offset(canvasWidth, py),
                            strokeWidth = 1f,
                            alpha = 0.35f,
                        )
                    }
                }

                for (sig in loggedSignals) {
                    val px = (sig.gridX.coerceIn(0f, 100f) / 100f) * canvasWidth
                    val py = (sig.gridY.coerceIn(0f, 100f) / 100f) * canvasHeight
                    val pinColor = try {
                        Color(sig.metalType.colorHex)
                    } catch (_: Exception) {
                        Color(0xFFFFD700)
                    }
                    drawCircle(color = pinColor, radius = 12f, center = Offset(px, py), alpha = 0.5f)
                    drawCircle(color = Color.White, radius = 4f, center = Offset(px, py))
                    drawCircle(
                        color = pinColor,
                        radius = 18f,
                        center = Offset(px, py),
                        style = Stroke(width = 2f),
                    )
                }

                val sx = (sweepX.coerceIn(0f, 100f) / 100f) * canvasWidth
                val sy = (sweepY.coerceIn(0f, 100f) / 100f) * canvasHeight
                val coil = Offset(sx, sy)

                drawCircle(
                    color = Color(0xFFFFD700),
                    radius = 36f,
                    center = coil,
                    style = Stroke(width = 1.5f),
                    alpha = 0.35f,
                )
                drawCircle(
                    color = Color(0xFFFFD700),
                    radius = 24f,
                    center = coil,
                    style = Stroke(width = 3.5f),
                    alpha = 0.85f,
                )
                drawLine(
                    color = Color(0xFFFFD700),
                    start = Offset(sx - 10f, sy),
                    end = Offset(sx + 10f, sy),
                    strokeWidth = 2f,
                    alpha = 0.8f,
                )
                drawLine(
                    color = Color(0xFFFFD700),
                    start = Offset(sx, sy - 10f),
                    end = Offset(sx, sy + 10f),
                    strokeWidth = 2f,
                    alpha = 0.8f,
                )
                drawCircle(color = Color.White, radius = 3f, center = coil)
            }

            // --- GEOSPATIAL GIS HUD ---
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xE60D0E12))
                    .border(0.5.dp, Color(0xFF2C2E35), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Column {
                    Text(
                        text = geoMetadata.siteName.uppercase(),
                        color = Color(0xFFFFD700),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp,
                    )
                    Text(
                        text = "${geoMetadata.crs} • ${geoMetadata.datum}",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xE60D0E12))
                    .border(0.5.dp, Color(0xFF2C2E35), RoundedCornerShape(8.dp))
                    .padding(8.dp),
            ) {
                if (currentLat != null && currentLon != null) {
                    val utm = runCatching {
                        GeoSpatialLibrary.geographicToUtm(currentLat, currentLon)
                    }.getOrNull()
                    Text(
                        text = "${GeoSpatialLibrary.formatDms(currentLat, true)}  ·  ${GeoSpatialLibrary.formatDms(currentLon, false)}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    if (utm != null) {
                        Text(
                            text = "UTM ${utm.zone}${utm.hemisphere}  E ${"%.1f".format(utm.easting)} m  N ${"%.1f".format(utm.northing)} m",
                            color = Color(0xFF64B5F6),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                } else {
                    Text(
                        text = "Local grid ${sweepX.toInt()}, ${sweepY.toInt()} · Geographic CRS unavailable",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No LiDAR data loaded.\nSelect a template below to render.",
                    color = Color.LightGray,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        if (isRendering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
