package com.example.data

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Represents a 2D grid of elevation points.
 * Supports ground point classification filtering, hillshading computations,
 * and high-performance Bitmap rendering.
 */
class ElevationGrid(
    val width: Int,
    val height: Int,
    val bareEarth: FloatArray, // Bare earth elevation values (DEM)
    val canopySpikes: FloatArray // Height of trees/vegetation on top of bare earth (DSM)
) {
    init {
        require(bareEarth.size == width * height) { "bareEarth size must be width * height" }
        require(canopySpikes.size == width * height) { "canopySpikes size must be width * height" }
    }

    /**
     * Gets the elevation at a specific coordinate (col, row),
     * applying the vegetation filter (0.0 = show full trees, 1.0 = fully classified bare ground).
     */
    fun getElevationAt(col: Int, row: Int, vegetationFilter: Float): Float {
        val c = col.coerceIn(0, width - 1)
        val r = row.coerceIn(0, height - 1)
        val idx = r * width + c
        // As vegetationFilter goes to 1.0, the vegetation spikes are filtered out
        return bareEarth[idx] + canopySpikes[idx] * (1.0f - vegetationFilter)
    }

    /**
     * Generates a hillshaded visual representation of the grid as a Bitmap.
     * Uses Horn's formula for slope and aspect calculation.
     *
     * @param sunAzimuth Angle of the sun in degrees (0-360, 0 is North, 90 is East)
     * @param sunAltitude Angle of the sun above the horizon in degrees (0-90)
     * @param vegetationFilter Strength of vegetation filtering (0.0 to 1.0)
     * @param palette Color theme for the elevation tinting (0 = Slate-Grey Clay, 1 = Lidar Copper, 2 = Terra Earth)
     * @param contrast Contrast multiplier for hillshade shadow depth (1.0 to 2.5)
     */
    fun renderHillshade(
        sunAzimuth: Float,
        sunAltitude: Float,
        vegetationFilter: Float,
        palette: Int,
        contrast: Float = 1.5f
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        // Convert sun angles to radians
        // GIS Azimuth starts at North (0 deg) and goes clockwise.
        // Trig angles start at East (0 rad) and go counter-clockwise.
        val sunAzRad = Math.toRadians((360.0 - sunAzimuth + 90.0) % 360.0).toFloat()
        val sunAltRad = Math.toRadians(sunAltitude.toDouble()).toFloat()

        val cosSunAlt = cos(sunAltRad)
        val sinSunAlt = sin(sunAltRad)

        // Constants for Horn's method gradient cell size
        val cellDistance = 1.0f

        // Precompute elevations with vegetation filter applied to speed up loops
        val filteredElevations = FloatArray(width * height)
        for (i in 0 until width * height) {
            filteredElevations[i] = bareEarth[i] + canopySpikes[i] * (1.0f - vegetationFilter)
        }

        // Find min and max elevations for hypsometric tinting
        var minElev = Float.MAX_VALUE
        var maxElev = Float.MIN_VALUE
        for (e in filteredElevations) {
            if (e < minElev) minElev = e
            if (e > maxElev) maxElev = e
        }
        val elevRange = if (maxElev - minElev > 0f) maxElev - minElev else 1f

        for (y in 0 until height) {
            for (x in 0 until width) {
                // Get 3x3 neighbors for Horn's slope algorithm
                val z00 = filteredElevations[coerceIndex(x - 1, y - 1)]
                val z01 = filteredElevations[coerceIndex(x, y - 1)]
                val z02 = filteredElevations[coerceIndex(x + 1, y - 1)]
                val z10 = filteredElevations[coerceIndex(x - 1, y)]
                val z12 = filteredElevations[coerceIndex(x + 1, y)]
                val z20 = filteredElevations[coerceIndex(x - 1, y + 1)]
                val z21 = filteredElevations[coerceIndex(x, y + 1)]
                val z22 = filteredElevations[coerceIndex(x + 1, y + 1)]

                // Horn's method for rate of change
                val dz_dx = ((z02 + 2f * z12 + z22) - (z00 + 2f * z10 + z20)) / (8f * cellDistance)
                val dz_dy = ((z20 + 2f * z21 + z22) - (z00 + 2f * z01 + z02)) / (8f * cellDistance)

                // Slope and Aspect
                val slope = atan(sqrt(dz_dx * dz_dx + dz_dy * dz_dy))
                val aspect = if (dz_dx != 0f) {
                    atan2(dz_dy, -dz_dx)
                } else {
                    if (dz_dy > 0) Math.PI.toFloat() / 2f else -Math.PI.toFloat() / 2f
                }

                // Hillshade illumination (0.0 to 1.0)
                var hillshade = cosSunAlt * cos(slope) + sinSunAlt * sin(slope) * cos(sunAzRad - aspect)
                hillshade = hillshade.coerceIn(0f, 1f)

                // Apply adjustable contrast/intensity to shadow relief
                if (contrast != 1.0f) {
                    hillshade = ((hillshade - 0.5f) * contrast + 0.5f).coerceIn(0f, 1f)
                }

                // Elevation percentage for color tinting
                val currentElev = filteredElevations[y * width + x]
                val elevPct = ((currentElev - minElev) / elevRange).coerceIn(0f, 1f)

                // Determine base color based on palette
                val baseColor = getPaletteColor(palette, elevPct)

                // Blend base color with the hillshade illumination
                // Low hillshade (0.0) -> dark shadow, High hillshade (1.0) -> fully lit highlight
                val r = (Color.red(baseColor) * hillshade).toInt().coerceIn(0, 255)
                val g = (Color.green(baseColor) * hillshade).toInt().coerceIn(0, 255)
                val b = (Color.blue(baseColor) * hillshade).toInt().coerceIn(0, 255)

                pixels[y * width + x] = Color.rgb(r, g, b)
            }
        }

        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }

    private fun coerceIndex(x: Int, y: Int): Int {
        val cx = x.coerceIn(0, width - 1)
        val cy = y.coerceIn(0, height - 1)
        return cy * width + cx
    }

    /**
     * Color palettes for LiDAR elevation mapping.
     */
    private fun getPaletteColor(palette: Int, pct: Float): Int {
        return when (palette) {
            // 0: Clay / Greyscale (Excellent for archeology foundations)
            0 -> {
                val shade = (150 + (pct * 60)).toInt().coerceIn(0, 255)
                Color.rgb(shade, shade, shade)
            }
            // 1: LiDAR Copper (Deep Navy blue/black to hot glowing copper)
            1 -> {
                // Multi-stop gradient: Navy (0) -> Maroon (0.3) -> Copper (0.7) -> Gold (1.0)
                if (pct < 0.3f) {
                    val p = pct / 0.3f
                    Color.rgb(
                        (15 * (1f - p) + 80 * p).toInt(),
                        (20 * (1f - p) + 15 * p).toInt(),
                        (45 * (1f - p) + 20 * p).toInt()
                    )
                } else if (pct < 0.7f) {
                    val p = (pct - 0.3f) / 0.4f
                    Color.rgb(
                        (80 * (1f - p) + 200 * p).toInt(),
                        (15 * (1f - p) + 90 * p).toInt(),
                        (20 * (1f - p) + 40 * p).toInt()
                    )
                } else {
                    val p = (pct - 0.7f) / 0.3f
                    Color.rgb(
                        (200 * (1f - p) + 250 * p).toInt(),
                        (90 * (1f - p) + 180 * p).toInt(),
                        (40 * (1f - p) + 70 * p).toInt()
                    )
                }
            }
            // 2: Terra / Topographical (Green valleys to brown mountains, white peaks)
            2 -> {
                if (pct < 0.2f) { // Valley: Deep green
                    val p = pct / 0.2f
                    Color.rgb(
                        (34 * (1f - p) + 46 * p).toInt(),
                        (112 * (1f - p) + 139 * p).toInt(),
                        (63 * (1f - p) + 87 * p).toInt()
                    )
                } else if (pct < 0.6f) { // Forest/Plain: Olive Green to Yellow
                    val p = (pct - 0.2f) / 0.4f
                    Color.rgb(
                        (46 * (1f - p) + 196 * p).toInt(),
                        (139 * (1f - p) + 183 * p).toInt(),
                        (87 * (1f - p) + 101 * p).toInt()
                    )
                } else if (pct < 0.85f) { // Slopes: Yellow to Terracotta Brown
                    val p = (pct - 0.6f) / 0.25f
                    Color.rgb(
                        (196 * (1f - p) + 130 * p).toInt(),
                        (183 * (1f - p) + 80 * p).toInt(),
                        (101 * (1f - p) + 40 * p).toInt()
                    )
                } else { // Peaks: Mountain Dark Brown to Snow-ish Grey
                    val p = (pct - 0.85f) / 0.15f
                    Color.rgb(
                        (130 * (1f - p) + 210 * p).toInt(),
                        (80 * (1f - p) + 210 * p).toInt(),
                        (40 * (1f - p) + 215 * p).toInt()
                    )
                }
            }
            else -> Color.GRAY
        }
    }
}
