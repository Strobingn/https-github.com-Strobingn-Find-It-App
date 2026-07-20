package com.example.data

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

object DemGenerator {
    const val GRID_SIZE = 100

    /**
     * Generates a pre-configured archaeological site.
     * @param siteType 0 = Old Homestead, 1 = Civil War Fort, 2 = Roman Villa
     */
    fun generateSite(siteType: Int): ElevationGrid {
        val bareEarth = FloatArray(GRID_SIZE * GRID_SIZE)
        val canopySpikes = FloatArray(GRID_SIZE * GRID_SIZE)

        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                val idx = y * GRID_SIZE + x
                val xf = x.toFloat()
                val yf = y.toFloat()

                // 1. Natural Base Terrain (Hills, ridges, and slopes)
                var baseTerrain = 0f
                when (siteType) {
                    0 -> { // Homestead: Gentle rolling hill slope
                        baseTerrain = 12f + (xf * 0.05f) + (yf * 0.03f) + 3f * sin(xf * 0.08f) * cos(yf * 0.06f)
                    }
                    1 -> { // Fortification: Tactical ridge crest across the center
                        // A ridge running diagonally
                        val distToRidge = (xf + yf - GRID_SIZE) / sqrt(2f)
                        baseTerrain = 15f + 6f * exp(-(distToRidge * distToRidge) / 600f) + (xf * 0.02f)
                    }
                    2 -> { // Roman Villa: Large, flat agricultural plateau
                        baseTerrain = 25f + 1.5f * sin(xf * 0.04f) + 1.5f * cos(yf * 0.04f)
                        // A depressed stream bed/valley on the right
                        if (x > 75) {
                            val streamDist = (xf - 85f)
                            baseTerrain -= (8f - 0.1f * streamDist * streamDist).coerceAtLeast(0f)
                        }
                    }
                }

                // 2. Archaeological Foundations & Earthworks (Subtle details carved into DEM)
                var ruinsDepth = 0f
                when (siteType) {
                    0 -> { // Old Homestead (Cellar hole, Chimney, Well, Path)
                        // Cellar hole at (45, 45), size 14x14
                        if (x in 38..52 && y in 38..52) {
                            ruinsDepth = -3.2f // Cellar excavation
                            // Leave a chimney mound at (45, 39)
                            if (x in 44..46 && y in 38..40) {
                                ruinsDepth = 1.8f
                            }
                        }
                        // Surrounding low fieldstone wall (mound border around cellar hole, 24x24)
                        val dx = Math.abs(xf - 45f)
                        val dy = Math.abs(yf - 45f)
                        if ((dx in 11.5f..13.5f && dy <= 13.5f) || (dy in 11.5f..13.5f && dx <= 13.5f)) {
                            ruinsDepth = 0.8f
                        }
                        // Circular Well at (25, 65)
                        val distToWell = sqrt((xf - 25f) * (xf - 25f) + (yf - 65f) * (yf - 65f))
                        if (distToWell < 3f) {
                            ruinsDepth = -4.5f
                        } else if (distToWell >= 3f && distToWell < 4.5f) {
                            ruinsDepth = 0.6f // Well stone collar ring
                        }

                        // Disturbed old trail: winding depression
                        val trailOffset = 15f * sin(yf * 0.06f) + 20f
                        val distToTrail = Math.abs(xf - trailOffset)
                        if (distToTrail < 2.5f && y > 30) {
                            ruinsDepth = -0.5f // slightly packed/sunken path
                        }
                    }
                    1 -> { // Civil War Fort (Chevron earthworks, defensive ditch, gun pit)
                        // Chevron shaped breastwork (embankment) pointed toward top-right (80, 20)
                        // Formula: y = -x + 100 is the diagonal. Fort centered around (50, 50).
                        val d1 = yf - (100f - xf) // Line 1
                        val d2 = yf - xf // Line 2
                        
                        // Let's create an arrowhead fort pointing North (50, 30)
                        // Chevron walls
                        val armLeft = Math.abs((yf - 30f) - 0.8f * (xf - 50f))
                        val armRight = Math.abs((yf - 30f) + 0.8f * (xf - 50f))
                        
                        val isInsideArms = yf > 30f + 0.8f * Math.abs(xf - 50f) && yf < 70f
                        val isNearLeftWall = armLeft < 3.5f && xf <= 50f && yf in 30f..65f
                        val isNearRightWall = armRight < 3.5f && xf >= 50f && yf in 30f..65f
                        
                        if (isNearLeftWall || isNearRightWall) {
                            ruinsDepth = 2.8f // Tall earthen ramparts
                        }
                        
                        // Defensive trench/moat running parallel, just outside the chevron
                        val isNearLeftTrench = armLeft >= 4f && armLeft < 8f && xf <= 51f && yf in 27f..66f
                        val isNearRightTrench = armRight >= 4f && armRight < 8f && xf >= 49f && yf in 27f..66f
                        if (isNearLeftTrench || isNearRightTrench) {
                            ruinsDepth = -1.8f // Excavated ditch
                        }

                        // Gun emplacement / Artillery mound at (50, 52)
                        val distToGun = sqrt((xf - 50f) * (xf - 50f) + (yf - 52f) * (yf - 52f))
                        if (distToGun < 4f) {
                            ruinsDepth = -0.8f // Pit inside gun platform
                        } else if (distToGun >= 4f && distToGun < 7f) {
                            ruinsDepth = 1.6f // Circular earthen parapet protecting the gun
                        }
                    }
                    2 -> { // Roman Villa (Rectangular foundation walls, shrine, straight Roman road)
                        // Villa rectangular walls at (40, 45) sized 28x18
                        val dx = Math.abs(xf - 40f)
                        val dy = Math.abs(yf - 45f)
                        
                        var isWall = false
                        // Main outer walls
                        if ((dx in 13.5f..14.5f && dy <= 9.5f) || (dy in 8.5f..9.5f && dx <= 14.5f)) {
                            isWall = true
                        }
                        // Inner room dividers
                        if (dx in 0f..1f && dy <= 9f) { // central divider
                            isWall = true
                        }
                        if (dy in 0f..1f && dx > 0f && dx < 14f) { // room 1/2 divider
                            isWall = true
                        }
                        
                        if (isWall) {
                            ruinsDepth = 1.1f // Uncovered stone foundation wall
                        } else if (dx < 13.5f && dy < 8.5f) {
                            ruinsDepth = -0.3f // Intentionally flat courtyard floor
                        }

                        // Circular Temple shrine outline at (68, 30)
                        val distToShrine = sqrt((xf - 68f) * (xf - 68f) + (yf - 30f) * (yf - 30f))
                        if (distToShrine >= 5f && distToShrine <= 6.5f) {
                            ruinsDepth = 0.9f // circular stone wall
                        } else if (distToShrine < 5f) {
                            ruinsDepth = 0.2f // raised marble floor pedestal
                        }

                        // Straight ancient Roman paved road diagonal from (0, 90) to (50, 100) or let's say straight up the west side
                        // A paved raised road running from bottom-left to top-middle-left
                        val roadLine = Math.abs(xf - (yf * 0.4f + 10f))
                        if (roadLine < 3f) {
                            ruinsDepth = 0.6f // raised agger (road mound)
                        }
                    }
                }

                bareEarth[idx] = baseTerrain + ruinsDepth

                // 3. Canopy Spikes (Vegetation trees/shrub height to overlay DSM)
                // We'll use high frequency sinusoids combined with noise to simulate dense trees
                var canopyVal = 0f
                val seed = (x * 73 + y * 31) % 100
                if (seed > 65) { // 35% density of trees/brush
                    // Generate height of trees (from 4 meters to 12 meters)
                    canopyVal = 5f + 7f * sin(xf * 0.5f) * cos(yf * 0.5f) + (seed % 10).toFloat() * 0.4f
                    
                    // Do not put trees inside the cellar holes or on top of well rings for historical accuracy,
                    // but in fort and villa they can grow on top of abandoned mounds!
                    if (siteType == 0) {
                        val inCellar = x in 38..52 && y in 38..52
                        val nearWell = sqrt((xf - 25f) * (xf - 25f) + (yf - 65f) * (yf - 65f)) < 4f
                        if (inCellar || nearWell) {
                            canopyVal = 0.5f // only minor weeds
                        }
                    }
                } else if (seed > 40) { // Shorter scrub/weeds (0.5m to 2m)
                    canopyVal = 0.5f + (seed % 5).toFloat() * 0.3f
                }
                
                canopySpikes[idx] = canopyVal.coerceAtLeast(0f)
            }
        }

        return ElevationGrid(GRID_SIZE, GRID_SIZE, bareEarth, canopySpikes)
    }

    /**
     * Parses custom elevation grid from pasted CSV text data.
     * Robustly handles different sizes and formats, fallback to default.
     */
    fun parseCustomGrid(csvText: String): ElevationGrid? {
        try {
            val lines = csvText.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (lines.isEmpty()) return null

            val rowData = ArrayList<FloatArray>()
            var colCount = -1

            for (line in lines) {
                // Split by commas, tabs, or spaces
                val tokens = line.split(Regex("[,\\s\\t]+"))
                    .filter { it.isNotEmpty() }
                
                if (tokens.isEmpty()) continue

                val floats = FloatArray(tokens.size)
                for (i in tokens.indices) {
                    floats[i] = tokens[i].toFloatOrNull() ?: 0f
                }

                if (colCount == -1) {
                    colCount = floats.size
                } else if (floats.size != colCount) {
                    // Non-matching columns, truncate or pad to maintain grid
                    val adjustedFloats = FloatArray(colCount)
                    System.arraycopy(floats, 0, adjustedFloats, 0, Math.min(floats.size, colCount))
                    rowData.add(adjustedFloats)
                    continue
                }
                rowData.add(floats)
            }

            if (rowData.isEmpty() || colCount <= 0) return null

            val width = colCount
            val height = rowData.size

            val bareEarth = FloatArray(width * height)
            val canopySpikes = FloatArray(width * height) // Custom files don't have separate canopy, so 0

            for (r in 0 until height) {
                val row = rowData[r]
                System.arraycopy(row, 0, bareEarth, r * width, width)
            }

            return ElevationGrid(width, height, bareEarth, canopySpikes)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
