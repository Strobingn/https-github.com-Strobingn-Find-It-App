package com.example.data

import android.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ElevationGridTest {
    @Test
    fun flatGroundGetsBrighterAsSunRises() {
        val grid = ElevationGrid(
            width = 3,
            height = 3,
            bareEarth = FloatArray(9) { 10f },
            canopySpikes = FloatArray(9),
        )

        val lowSun = grid.renderHillshade(315f, 10f, 1f, palette = 0, contrast = 1f)
        val highSun = grid.renderHillshade(315f, 80f, 1f, palette = 0, contrast = 1f)

        assertTrue(Color.red(highSun.getPixel(1, 1)) > Color.red(lowSun.getPixel(1, 1)))
    }
}
