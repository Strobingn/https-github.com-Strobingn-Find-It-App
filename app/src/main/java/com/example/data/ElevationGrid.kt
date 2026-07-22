package com.example.data

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A memory-bounded elevation raster used for terrain visualization and screening. */
class ElevationGrid(
    val width: Int,
    val height: Int,
    val bareEarth: FloatArray,
    val canopySpikes: FloatArray,
    val cellSizeMeters: Float = 1f,
    val validData: BooleanArray = BooleanArray(width * height) { true },
) {
    init {
        require(width > 0 && height > 0) { "Grid dimensions must be positive" }
        require(bareEarth.size == width * height) { "bareEarth size must be width * height" }
        require(canopySpikes.size == width * height) { "canopySpikes size must be width * height" }
        require(validData.size == width * height) { "validData size must be width * height" }
    }

    /** 0 shows the full surface model; 1 shows the extracted bare-earth model. */
    fun getElevationAt(col: Int, row: Int, vegetationFilter: Float): Float {
        val c = col.coerceIn(0, width - 1)
        val r = row.coerceIn(0, height - 1)
        val index = r * width + c
        return bareEarth[index] + canopySpikes[index] * (1f - vegetationFilter.coerceIn(0f, 1f))
    }

    /**
     * Render a terrain-analysis bitmap.
     *
     * Modes: 0 single hillshade, 1 multi-directional hillshade, 2 slope, 3 local relief,
     * 4 curvature, 5 disturbance candidates, 6 aspect, 7 elevation, and 8 canopy height.
     * Candidate views are screening aids, not proof of
     * archaeological origin; field verification and source-quality review remain essential.
     */
    fun renderHillshade(
        sunAzimuth: Float,
        sunAltitude: Float,
        vegetationFilter: Float,
        palette: Int,
        contrast: Float = 1.5f,
        visualizationMode: Int = 0,
        overlayType: Int = 0,


        overlayOpacity: Float = 0.5f,
        zScale: Float = 1f,
