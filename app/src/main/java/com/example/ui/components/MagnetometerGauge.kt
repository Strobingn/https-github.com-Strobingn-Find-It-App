package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MetalType
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MagnetometerGauge(
    signalStrength: Float, // 0 to 100
    detectedMetal: MetalType?,
    detectedDepthCm: Int? = null,
    isPhysicalSensor: Boolean,
    modifier: Modifier = Modifier
) {
    // Smoothly animate the needle swings for realistic inertia
    val animatedStrength by animateFloatAsState(
        targetValue = signalStrength,
        animationSpec = tween(durationMillis = 150)
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141518))
            .border(1.dp, Color(0xFF2C2E35), RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("magnetometer_gauge_container")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header Text indicating hardware or virtual sensor
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isPhysicalSensor) Color(0xFF4CAF50) else Color(0xFF2196F3))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isPhysicalSensor) "PHONE MAGNETOMETER" else "SIMULATED TEMPLATE SWEEP",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray,
                    letterSpacing = 1.2.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1. Semi-Circular Analog Gauge
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .testTag("analog_gauge_canvas"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val center = Offset(w / 2f, h * 0.75f)
                    val radius = w * 0.42f

                    // Draw Background Arc (180 degrees from 180 to 360)
                    drawArc(
                        color = Color(0xFF2A2C35),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = 10f, cap = StrokeCap.Round)
                    )

                    // Draw Gradient Colored Active Arc
                    drawArc(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF4CAF50), // Low (green)
                                Color(0xFFFFEB3B), // Mid (yellow)
                                Color(0xFFF44336)  // High (red)
                            )
                        ),
                        startAngle = 180f,
                        sweepAngle = 180f * (animatedStrength / 100f).coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = 12f, cap = StrokeCap.Round)
                    )

                    // Draw Gauge Ticks (Major ticks every 30 degrees)
                    for (angleDeg in 180..360 step 30) {
                        val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
                        val tickStart = Offset(
                            center.x + (radius - 12f) * cos(angleRad),
                            center.y + (radius - 12f) * sin(angleRad)
                        )
                        val tickEnd = Offset(
                            center.x + (radius + 2f) * cos(angleRad),
                            center.y + (radius + 2f) * sin(angleRad)
                        )
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.6f),
                            start = tickStart,
                            end = tickEnd,
                            strokeWidth = 3f
                        )
                    }

                    // Draw Physical Gauge Needle
                    // Needle angle goes from 180deg (0 strength) to 360deg (100 strength)
                    val needleAngleDeg = 180f + (180f * (animatedStrength / 100f).coerceIn(0f, 1f))
                    val needleAngleRad = Math.toRadians(needleAngleDeg.toDouble()).toFloat()
                    val needleLength = radius * 0.85f
                    val needleEnd = Offset(
                        center.x + needleLength * cos(needleAngleRad),
                        center.y + needleLength * sin(needleAngleRad)
                    )

                    // Draw needle drop shadow
                    drawLine(
                        color = Color.Black.copy(alpha = 0.5f),
                        start = Offset(center.x + 2f, center.y + 2f),
                        end = Offset(needleEnd.x + 2f, needleEnd.y + 2f),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round
                    )

                    // Draw actual needle (Golden Amber)
                    drawLine(
                        color = Color(0xFFFF9800),
                        start = center,
                        end = needleEnd,
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )

                    // Center Hub Cap (Bezel screw)
                    drawCircle(
                        color = Color(0xFF3E414D),
                        radius = 16f,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFF1E2028),
                        radius = 10f,
                        center = center
                    )
                }

                // Digital center readout
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${animatedStrength.toInt()}%",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (isPhysicalSensor) "FIELD DEVIATION" else "SIMULATED SIGNAL",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Real-time Identification Indicator Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (detectedMetal != null) {
                            Color(detectedMetal.colorHex).copy(alpha = 0.15f)
                        } else {
                            Color(0xFF1E2026)
                        }
                    )
                    .border(
                        1.dp,
                        if (detectedMetal != null) Color(detectedMetal.colorHex).copy(alpha = 0.4f) else Color(0xFF2E313D),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(12.dp)
                    .testTag("identification_panel")
            ) {
                if (detectedMetal != null && signalStrength > 10f) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isPhysicalSensor) "ANOMALY DETECTED" else "SIMULATED TARGET",
                                color = Color(detectedMetal.colorHex),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = detectedMetal.label,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        }

                        if (detectedDepthCm != null) {
                            Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "TEMPLATE DEPTH",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$detectedDepthCm cm",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPhysicalSensor) "No magnetic-field anomaly." else "No simulated target under the sweep.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
