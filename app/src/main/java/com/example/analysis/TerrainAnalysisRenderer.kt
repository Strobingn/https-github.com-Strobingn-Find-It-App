package com.example.analysis

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.ln1p

object TerrainAnalysisRenderer {

    fun render(layer: TerrainAnalysisLayer): Bitmap {
        val bitmap = Bitmap.createBitmap(layer.width, layer.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(layer.values.size)
        val range = (layer.maximum - layer.minimum).takeIf { abs(it) > 0.000001f } ?: 1f
        val symmetricScale = maxOf(abs(layer.minimum), abs(layer.maximum), 0.000001f)
        val logMaximum = ln1p(layer.maximum.coerceAtLeast(0f).toDouble()).toFloat().coerceAtLeast(0.000001f)

        for (index in layer.values.indices) {
            if (!layer.validData[index]) {
                pixels[index] = Color.TRANSPARENT
                continue
            }
            val value = layer.values[index]
            pixels[index] = when (layer.type) {
                TerrainAnalysisType.ASPECT -> aspectColor(value)
                TerrainAnalysisType.WATERSHED -> watershedColor(value.toInt())
                TerrainAnalysisType.FLOW_ACCUMULATION -> {
                    val normalized = (ln1p(value.coerceAtLeast(0f).toDouble()).toFloat() / logMaximum)
                        .coerceIn(0f, 1f)
                    sequentialColor(normalized)
                }
                TerrainAnalysisType.MULTI_HILLSHADE -> grayscale(value.coerceIn(0f, 1f))
                TerrainAnalysisType.LOCAL_RELIEF_MODEL,
                TerrainAnalysisType.CURVATURE,
                TerrainAnalysisType.EROSION_SIMULATION,
                -> divergingColor((value / symmetricScale).coerceIn(-1f, 1f))
                TerrainAnalysisType.DEPRESSION_DEPTH,
                TerrainAnalysisType.ANCIENT_STREAM_LIKELIHOOD,
                -> hotspotColor(((value - layer.minimum) / range).coerceIn(0f, 1f))
                else -> sequentialColor(((value - layer.minimum) / range).coerceIn(0f, 1f))
            }
        }
        bitmap.setPixels(pixels, 0, layer.width, 0, 0, layer.width, layer.height)
        return bitmap
    }

    private fun grayscale(value: Float): Int {
        val channel = (35f + value * 220f).toInt().coerceIn(0, 255)
        return Color.rgb(channel, channel, channel)
    }

    private fun sequentialColor(value: Float): Int = when {
        value < 0.25f -> blend(Color.rgb(20, 28, 58), Color.rgb(25, 94, 145), value / 0.25f)
        value < 0.5f -> blend(Color.rgb(25, 94, 145), Color.rgb(35, 170, 160), (value - 0.25f) / 0.25f)
        value < 0.75f -> blend(Color.rgb(35, 170, 160), Color.rgb(213, 210, 90), (value - 0.5f) / 0.25f)
        else -> blend(Color.rgb(213, 210, 90), Color.rgb(250, 245, 220), (value - 0.75f) / 0.25f)
    }

    private fun hotspotColor(value: Float): Int = when {
        value < 0.33f -> blend(Color.rgb(20, 28, 58), Color.rgb(40, 120, 190), value / 0.33f)
        value < 0.66f -> blend(Color.rgb(40, 120, 190), Color.rgb(255, 193, 7), (value - 0.33f) / 0.33f)
        else -> blend(Color.rgb(255, 193, 7), Color.rgb(220, 45, 45), (value - 0.66f) / 0.34f)
    }

    private fun divergingColor(value: Float): Int {
        val neutral = Color.rgb(220, 220, 215)
        return if (value < 0f) {
            blend(neutral, Color.rgb(35, 100, 210), -value)
        } else {
            blend(neutral, Color.rgb(220, 55, 45), value)
        }
    }

    private fun aspectColor(value: Float): Int {
        if (value < 0f) return Color.rgb(135, 135, 135)
        return Color.HSVToColor(floatArrayOf(value % 360f, 0.82f, 0.95f))
    }

    private fun watershedColor(label: Int): Int {
        val hue = ((label * 137.508f) % 360f + 360f) % 360f
        return Color.HSVToColor(floatArrayOf(hue, 0.58f, 0.9f))
    }

    private fun blend(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt().coerceIn(0, 255),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt().coerceIn(0, 255),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt().coerceIn(0, 255),
        )
    }
}
