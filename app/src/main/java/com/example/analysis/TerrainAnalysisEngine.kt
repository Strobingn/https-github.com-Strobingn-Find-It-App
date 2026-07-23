package com.example.analysis

import com.example.data.ElevationGrid
import java.util.PriorityQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * CPU-only terrain analysis engine. All raw LiDAR/DEM calculations run locally and offline.
 * OpenAI is used only by the optional interpretation client after these numeric products exist.
 */
object TerrainAnalysisEngine {

    fun analyze(
        grid: ElevationGrid,
        type: TerrainAnalysisType,
        options: TerrainAnalysisOptions = TerrainAnalysisOptions(),
    ): TerrainAnalysisLayer {
        val normalizedOptions = options.normalized(grid.cellSizeMeters)
        val elevations = grid.bareEarth.copyOf()
        val valid = grid.validData.copyOf()
        val values = when (type) {
            TerrainAnalysisType.MULTI_HILLSHADE -> multiHillshade(grid, elevations, valid)
            TerrainAnalysisType.SKY_VIEW_FACTOR -> skyViewFactor(grid, elevations, valid, normalizedOptions)
            TerrainAnalysisType.LOCAL_RELIEF_MODEL -> localRelief(grid, elevations, valid, normalizedOptions)
            TerrainAnalysisType.POSITIVE_OPENNESS -> openness(
                grid = grid,
                elevations = elevations,
                valid = valid,
                options = normalizedOptions,
                inverted = false,
            )
            TerrainAnalysisType.NEGATIVE_OPENNESS -> openness(
                grid = grid,
                elevations = elevations,
                valid = valid,
                options = normalizedOptions,
                inverted = true,
            )
            TerrainAnalysisType.SLOPE -> slope(grid, elevations, valid)
            TerrainAnalysisType.CURVATURE -> curvature(grid, elevations, valid)
            TerrainAnalysisType.ASPECT -> aspect(grid, elevations, valid)
            TerrainAnalysisType.RUGGEDNESS_INDEX -> ruggedness(grid, elevations, valid)
            TerrainAnalysisType.FLOW_ACCUMULATION -> {
                val receivers = d8Receivers(grid, elevations, valid)
                flowAccumulation(elevations, valid, receivers)
            }
            TerrainAnalysisType.WATERSHED -> {
                val receivers = d8Receivers(grid, elevations, valid)
                watershedLabels(valid, receivers)
            }
            TerrainAnalysisType.DEPRESSION_DEPTH -> depressionDepth(grid, elevations, valid)
            TerrainAnalysisType.ANCIENT_STREAM_LIKELIHOOD -> ancientStreamLikelihood(
                grid = grid,
                elevations = elevations,
                valid = valid,
            )
            TerrainAnalysisType.EROSION_SIMULATION -> erosionSimulation(
                grid = grid,
                elevations = elevations,
                valid = valid,
                options = normalizedOptions,
            )
        }
        return buildLayer(grid, type, values, valid)
    }

    private fun multiHillshade(
        grid: ElevationGrid,
        elevations: FloatArray,
        valid: BooleanArray,
    ): FloatArray {
        val output = FloatArray(elevations.size)
        for (y in 0 until grid.height) {
            for (x in 0 until grid.width) {
                val index = y * grid.width + x
                if (!valid[index]) continue
                val gradient = hornGradient(grid, elevations, valid, x, y)
                val shades = floatArrayOf(
                    shade(gradient.dx, gradient.dy, 315f, 35f),
                    shade(gradient.dx, gradient.dy, 45f, 35f),
                    shade(gradient.dx, gradient.dy, 135f, 35f),
                    shade(gradient.dx, gradient.dy, 225f, 35f),
                )
                output[index] = shades[0] * 0.4f + shades[1] * 0.25f +
                    shades[2] * 0.15f + shades[3] * 0.2f
            }
        }
        return output
    }

    private fun slope(
        grid: ElevationGrid,
        elevations: FloatArray,
        valid: BooleanArray,
    ): FloatArray {
        val output = FloatArray(elevations.size)
        for (y in 0 until grid.height) {
            for (x in 0 until grid.width) {
                val index = y * grid.width + x
                if (!valid[index]) continue
                val gradient = hornGradient(grid, elevations, valid, x, y)
                output[index] = Math.toDegrees(
                    atan(sqrt(gradient.dx * gradient.dx + gradient.dy * gradient.dy)).toDouble(),
                ).toFloat()
            }
        }
        return output
    }

    private fun aspect(
        grid: ElevationGrid,
        elevations: FloatArray,
        valid: BooleanArray,
    ): FloatArray {
        val output = FloatArray(elevations.size) { -1f }
        for (y in 0 until grid.height) {
            for (x in 0 until grid.width) {
                val index = y * grid.width + x
                if (!valid[index]) continue
                val gradient = hornGradient(grid, elevations, valid, x, y)
                val magnitude = sqrt(gradient.dx * gradient.dx + gradient.dy * gradient.dy)
                output[index] = if (magnitude < 0.0001f) {
                    -1f
                } else {
                    ((Math.toDegrees(atan2(gradient.dx, -gradient.dy).toDouble()).toFloat() + 360f) % 360f)
                }
            }
        }
        return output
    }

    private fun curvature(
        grid: ElevationGrid,
        elevations: FloatArray,
        valid: BooleanArray,
    ): FloatArray {
        val output = FloatArray(elevations.size)
        val divisor = grid.cellSizeMeters.coerceAtLeast(0.001f).let { it * it }
        for (y in 0 until grid.height) {
            for (x in 0 until grid.width) {
                val index = y * grid.width + x
                if (!valid[index]) continue
                val center = elevations[index]
                val left = sample(grid, elevations, valid, x - 1, y, center)
                val right = sample(grid, elevations, valid, x + 1, y, center)
                val up = sample(grid, elevations, valid, x, y - 1, center)
                val down = sample(grid, elevations, valid, x, y + 1, center)
                output[index] = (left + right + up + down - 4f * center) / divisor
            }
        }
        return output
    }

    private fun ruggedness(
        grid: ElevationGrid,
        elevations: FloatArray,
        valid: BooleanArray,
    ): FloatArray {
        val output = FloatArray(elevations.size)
        for (y in 0 until grid.height) {
            for (x in 0 until grid.width) {
                val index = y * grid.width + x
                if (!valid[index]) continue
                val center = elevations[index]
                var squaredDifference = 0.0
                var count = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx !in 0 until grid.width || ny !in 0 until grid.height) continue
                        val neighbor = ny * grid.width + nx
                        if (!valid[neighbor]) continue
                        val difference = elevations[neighbor] - center
                        squaredDifference += difference * difference
                        count++
                    }
                }
                output[index] = if (count == 0) 0f else sqrt(squaredDifference / count).toFloat()
            }
        }
        return output
    }

    private fun localRelief(
        grid: ElevationGrid,
        elevations: FloatArray,
        valid: BooleanArray,
        options: TerrainAnalysisOptions,
    ): FloatArray {
        val radius = (options.localRadiusMeters / grid.cellSizeMeters.coerceAtLeast(0.001f))
            .roundToInt()
            .coerceIn(1, max(1, min(grid.width, grid.height) / 3))
        val integral = integralImages(grid, elevations, valid)
        val output = FloatArray(elevations.size)
        for (y in 0 until grid.height) {
            for (x in 0 until grid.width) {
                val index = y * grid.width + x
                if (!valid[index]) continue
                val x0 = (x - radius).coerceAtLeast(0)
                val y0 = (y - radius).coerceAtLeast(0)
                val x1 = (x + radius).coerceAtMost(grid.width - 1)
                val y1 = (y + radius).coerceAtMost(grid.height - 1)
                val count = rectangleCount(integral.count, integral.stride, x0, y0, x1, y1)
                if (count > 0) {
                    val mean = rectangleSum(integral.sum, integral.stride, x0, y0, x1, y1) / count
                    output[index] = elevations[index] - mean.toFloat()
                }
            }
        }
        return output
    }

    private fun skyViewFactor(
        grid: ElevationGrid,
        elevations: FloatArray,
        valid: BooleanArray,
        options: TerrainAnalysisOptions,
    ): FloatArray {
        val horizons = directionalHorizons(grid, elevations, valid, options, inverted = false)
        val output = FloatArray(elevations.size)
        for (index in output.indices) {
            if (!valid[index]) continue
            var total = 0.0
            for (direction in 0 until options.directionCount) {
                val horizon = horizons[direction * elevations.size + index].coerceIn(0f, (PI / 2).toFloat())
                val cosine = cos(horizon.toDouble())
                total += cosine * cosine
            }
            output[index] = (total / options.directionCount).toFloat().coerceIn(0f, 1f)
        }
        return output
    }

    private fun openness(
        grid: ElevationGrid,
        elevations: FloatArray,
        valid: BooleanArray,
        options: TerrainAnalysisOptions,
        inverted: Boolean,
    ): FloatArray {
        val horizons = directionalHorizons(grid, elevations, valid, options, inverted)
        val output = FloatArray(elevations.size)
        for (index in output.indices) {
            if (!valid[index]) continue
            var total = 0.0
            for (direction in 0 until options.directionCount) {
                val horizon = horizons[direction * elevations.size + index]
                total += 90.0 - Math.toDegrees(horizon.toDouble())
            }
            output[index] = (total / options.directionCount).toFloat().coerceIn(0f, 180f)
        }
        return output
    }

    private fun directionalHorizons(
        grid: ElevationGrid,
        elevations: FloatArray,
        valid: BooleanArray,
        options: TerrainAnalysisOptions,
        inverted: Boolean,
    ): FloatArray {
        val directions = options.directionCount
        val radiusCells = (options.horizonRadiusMeters / grid.cellSizeMeters.coerceAtLeast(0.001f))
            .roundToInt()
            .coerceIn(2, max(2, min(grid.width, grid.height) / 2))
        val output = FloatArray(elevations.size * directions)
        for (direction in 0 until directions) {
            val angle = direction * (2.0 * PI / directions)
            val dx = cos(angle)
            val dy = sin(angle)
            val baseOffset = direction * elevations.size
            for (y in 0 until grid.height) {
                for (x in 0 until grid.width) {
                    val index = y * grid.width + x
                    if (!valid[index]) continue
                    val center = elevations[index]
                    var maximumAngle = (-PI / 2.0).toFloat()
                    var lastX = Int.MIN_VALUE
                    var lastY = Int.MIN_VALUE
                    for (step in 1..radiusCells) {
                        val nx = (x + dx * step).roundToInt()
                        val ny = (y + dy * step).roundToInt()
                        if (nx == lastX && ny == lastY) continue
                        lastX = nx
                        lastY = ny
                        if (nx !in 0 until grid.width || ny !in 0 until grid.height) break
                        val neighborIndex = ny * grid.width + nx
                        if (!valid[neighborIndex]) continue
                        val horizontalDistance = sqrt(
                            ((nx - x) * (nx - x) + (ny - y) * (ny - y)).toDouble(),
                        ) * grid.cellSizeMeters
                        if (horizontalDistance <= 0.0) continue
                        val difference = if (inverted) {
                            center - elevations[neighborIndex]
                        } else {
                            elevations[neighborIndex] - center
                        }
                        val candidate = atan2(difference.toDouble(), horizontalDistance).toFloat()
                        if (candidate > maximumAngle) maximumAngle = candidate
                    }
                    output[baseOffset + index] = maximumAngle
                }
            }
        }
        return output
    }

    private fun d8Receivers(
        grid: ElevationGrid,
        elevations: FloatArray,
        valid: BooleanArray,
    ): IntArray {
        val receivers = IntArray(elevations.size) { -1 }
        for (y in 0 until grid.height) {
            for (x in 0 until grid.width) {
                val index = y * grid.width + x
                if (!valid[index]) continue
                var bestSlope = 0f
                var bestReceiver = -1
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx !in 0 until grid.width || ny !in 0 until grid.height) continue
                        val neighbor = ny * grid.width + nx
                        if (!valid[neighbor]) continue
                        val drop = elevations[index] - elevations[neighbor]
                        if (drop <= 0f) continue
                        val distance = grid.cellSizeMeters * if (dx != 0 && dy != 0) SQRT_TWO else 1f
                        val candidateSlope = drop / distance.coerceAtLeast(0.001f)
                        if (candidateSlope > bestSlope) {
                            bestSlope = candidateSlope
                            bestReceiver = neighbor
                        }
                    }
                }
                receivers[index] = bestReceiver
            }
        }
        return receivers
    }

    private fun flowAccumulation(
        elevations: FloatArray,
        valid: BooleanArray,
        receivers: IntArray,
    ): FloatArray {
        val order = validIndices(valid)
        sortByElevationDescending(order, elevations, 0, order.lastIndex)
        val accumulation = FloatArray(elevations.size)
        for (index in order) accumulation[index] = 1f
        for (index in order) {
            val receiver = receivers[index]
            if (receiver >= 0) accumulation[receiver] += accumulation[index]
        }
        return accumulation
    }

    private fun watershedLabels(valid: BooleanArray, receivers: IntArray): FloatArray {
        val labels = IntArray(valid.size) { -1 }
        val sinkToLabel = HashMap<Int, Int>()
        var nextLabel = 0
        for (start in valid.indices) {
            if (!valid[start] || labels[start] >= 0) continue
            val path = ArrayList<Int>()
            var current = start
            while (current >= 0 && labels[current] < 0 && current !in path) {
                path += current
                current = receivers[current]
            }
            val label = when {
                current >= 0 && labels[current] >= 0 -> labels[current]
                else -> {
                    val sink = path.lastOrNull() ?: start
                    sinkToLabel.getOrPut(sink) { nextLabel++ }
                }
            }
            path.forEach { labels[it] = label }
        }
        return FloatArray(valid.size) { index -> if (valid[index]) labels[index].toFloat() else 0f }
    }

    private fun depressionDepth(
        grid: ElevationGrid,
        elevations: FloatArray,
        valid: BooleanArray,
    ): FloatArray {
        val filled = elevations.copyOf()
        val visited = BooleanArray(elevations.size)
        val queue = PriorityQueue<FloodCell>(compareBy { it.elevation })

        fun seed(index: Int) {
            if (!valid[index] || visited[index]) return
            visited[index] = true
            queue += FloodCell(index, filled[index])
        }

        for (y in 0 until grid.height) {
            for (x in 0 until grid.width) {
                val index = y * grid.width + x
                if (!valid[index]) continue
                val edge = x == 0 || y == 0 || x == grid.width - 1 || y == grid.height - 1
                val nextToNoData = !edge && NEIGHBORS.any { (dx, dy) ->
                    val nx = x + dx
                    val ny = y + dy
                    nx !in 0 until grid.width || ny !in 0 until grid.height || !valid[ny * grid.width + nx]
                }
                if (edge || nextToNoData) seed(index)
            }
        }

        while (queue.isNotEmpty()) {
            val cell = queue.remove()
            val x = cell.index % grid.width
            val y = cell.index / grid.width
            for ((dx, dy) in NEIGHBORS) {
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until grid.width || ny !in 0 until grid.height) continue
                val neighbor = ny * grid.width + nx
                if (!valid[neighbor] || visited[neighbor]) continue
                visited[neighbor] = true
                filled[neighbor] = max(elevations[neighbor], cell.elevation)
                queue += FloodCell(neighbor, filled[neighbor])
            }
        }

        return FloatArray(elevations.size) { index ->
            if (valid[index]) (filled[index] - elevations[index]).coerceAtLeast(0f) else 0f
        }
    }

    private fun ancientStreamLikelihood(
        grid: ElevationGrid,
        elevations: FloatArray,
        valid: BooleanArray,
    ): FloatArray {
        val receivers = d8Receivers(grid, elevations, valid)
        val flow = flowAccumulation(elevations, valid, receivers)
        val slopes = slope(grid, elevations, valid)
        val curves = curvature(grid, elevations, valid)
        val maxLogFlow = valid.indices
            .filter { valid[it] }
            .maxOfOrNull { ln1p(flow[it].toDouble()) }
            ?.coerceAtLeast(1.0) ?: 1.0
        val concavityScale = percentileMagnitude(curves, valid, 0.95f).coerceAtLeast(0.0001f)
        val output = FloatArray(elevations.size)
        for (index in output.indices) {
            if (!valid[index]) continue
            val flowScore = (ln1p(flow[index].toDouble()) / maxLogFlow).toFloat().coerceIn(0f, 1f)
            val concavity = (-curves[index] / concavityScale).coerceIn(0f, 1f)
            val lowGradient = (1f - slopes[index] / 18f).coerceIn(0f, 1f)
            output[index] = (flowScore * 0.62f + concavity * 0.23f + lowGradient * 0.15f)
                .coerceIn(0f, 1f)
        }
        return output
    }

    private fun erosionSimulation(
        grid: ElevationGrid,
        elevations: FloatArray,
        valid: BooleanArray,
        options: TerrainAnalysisOptions,
    ): FloatArray {
        val receivers = d8Receivers(grid, elevations, valid)
        val accumulation = flowAccumulation(elevations, valid, receivers)
        val order = validIndices(valid)
        sortByElevationDescending(order, elevations, 0, order.lastIndex)
        val terrain = elevations.copyOf()
        val maximumFlow = accumulation.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val effectiveIterations = options.erosionIterations.coerceAtMost(
            when {
                terrain.size > 750_000 -> 8
                terrain.size > 250_000 -> 15
                else -> options.erosionIterations
            },
        )

        repeat(effectiveIterations) {
            val delta = FloatArray(terrain.size)
            for (index in order) {
                val receiver = receivers[index]
                if (receiver < 0) continue
                val drop = (terrain[index] - terrain[receiver]).coerceAtLeast(0f)
                if (drop <= 0f) continue
                val distance = receiverDistance(grid, index, receiver)
                val localSlope = drop / distance.coerceAtLeast(0.001f)
                val flowFactor = sqrt((accumulation[index] / maximumFlow).coerceIn(0f, 1f))
                val eroded = (
                    options.rainfallFactor * flowFactor * sqrt(localSlope) * 0.004f
                    ).coerceIn(0f, 0.04f)
                delta[index] -= eroded
                delta[receiver] += eroded * 0.68f
            }
            for (index in terrain.indices) {
                if (valid[index]) terrain[index] += delta[index]
            }
        }

        return FloatArray(terrain.size) { index ->
            if (valid[index]) terrain[index] - elevations[index] else 0f
        }
    }

    private fun buildLayer(
        grid: ElevationGrid,
        type: TerrainAnalysisType,
        values: FloatArray,
        valid: BooleanArray,
    ): TerrainAnalysisLayer {
        val statistics = statistics(values, valid, type == TerrainAnalysisType.ASPECT)
        val summary = when (type) {
            TerrainAnalysisType.FLOW_ACCUMULATION ->
                "The strongest drainage cell receives approximately ${statistics.maximum.toInt()} upstream cells."
            TerrainAnalysisType.WATERSHED ->
                "${statistics.maximum.toInt() + 1} drainage basins were separated from D8 flow paths."
            TerrainAnalysisType.DEPRESSION_DEPTH ->
                "The deepest closed depression is ${"%.2f".format(statistics.maximum)} m below its spill elevation."
            TerrainAnalysisType.ANCIENT_STREAM_LIKELIHOOD ->
                "Higher scores combine convergent flow, concavity and low-gradient valley terrain."
            TerrainAnalysisType.EROSION_SIMULATION ->
                "Negative values indicate modeled erosion; positive values indicate modeled deposition."
            else -> type.description
        }
        return TerrainAnalysisLayer(
            type = type,
            width = grid.width,
            height = grid.height,
            values = values,
            validData = valid,
            cellSizeMeters = grid.cellSizeMeters,
            minimum = statistics.minimum,
            maximum = statistics.maximum,
            mean = statistics.mean,
            percentile95 = statistics.percentile95,
            summary = summary,
        )
    }

    private fun hornGradient(
        grid: ElevationGrid,
        elevations: FloatArray,
        valid: BooleanArray,
        x: Int,
        y: Int,
    ): Gradient {
        val center = elevations[y * grid.width + x]
        fun at(px: Int, py: Int): Float = sample(grid, elevations, valid, px, py, center)
        val z00 = at(x - 1, y - 1)
        val z01 = at(x, y - 1)
        val z02 = at(x + 1, y - 1)
        val z10 = at(x - 1, y)
        val z12 = at(x + 1, y)
        val z20 = at(x - 1, y + 1)
        val z21 = at(x, y + 1)
        val z22 = at(x + 1, y + 1)
        val distance = grid.cellSizeMeters.coerceAtLeast(0.001f)
        return Gradient(
            dx = ((z02 + 2f * z12 + z22) - (z00 + 2f * z10 + z20)) / (8f * distance),
            dy = ((z20 + 2f * z21 + z22) - (z00 + 2f * z01 + z02)) / (8f * distance),
        )
    }

    private fun shade(dx: Float, dy: Float, azimuthDegrees: Float, altitudeDegrees: Float): Float {
        val azimuth = Math.toRadians(azimuthDegrees.toDouble())
        val altitude = Math.toRadians(altitudeDegrees.toDouble())
        val normalLength = sqrt(dx * dx + dy * dy + 1f)
        val nx = -dx / normalLength
        val ny = -dy / normalLength
        val nz = 1f / normalLength
        val horizontal = cos(altitude).toFloat()
        val lx = (sin(azimuth) * horizontal).toFloat()
        val ly = (-cos(azimuth) * horizontal).toFloat()
        val lz = sin(altitude).toFloat()
        return (nx * lx + ny * ly + nz * lz).coerceIn(0f, 1f)
    }

    private fun sample(
        grid: ElevationGrid,
        elevations: FloatArray,
        valid: BooleanArray,
        x: Int,
        y: Int,
        fallback: Float,
    ): Float {
        val px = x.coerceIn(0, grid.width - 1)
        val py = y.coerceIn(0, grid.height - 1)
        val index = py * grid.width + px
        return if (valid[index]) elevations[index] else fallback
    }

    private fun integralImages(
        grid: ElevationGrid,
        elevations: FloatArray,
        valid: BooleanArray,
    ): IntegralImages {
        val stride = grid.width + 1
        val sum = DoubleArray((grid.height + 1) * stride)
        val count = IntArray((grid.height + 1) * stride)
        for (y in 0 until grid.height) {
            var rowSum = 0.0
            var rowCount = 0
            for (x in 0 until grid.width) {
                val source = y * grid.width + x
                if (valid[source]) {
                    rowSum += elevations[source]
                    rowCount++
                }
                val target = (y + 1) * stride + x + 1
                sum[target] = sum[y * stride + x + 1] + rowSum
                count[target] = count[y * stride + x + 1] + rowCount
            }
        }
        return IntegralImages(sum, count, stride)
    }

    private fun rectangleSum(
        integral: DoubleArray,
        stride: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
    ): Double = integral[(y1 + 1) * stride + x1 + 1] -
        integral[y0 * stride + x1 + 1] -
        integral[(y1 + 1) * stride + x0] +
        integral[y0 * stride + x0]

    private fun rectangleCount(
        integral: IntArray,
        stride: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
    ): Int = integral[(y1 + 1) * stride + x1 + 1] -
        integral[y0 * stride + x1 + 1] -
        integral[(y1 + 1) * stride + x0] +
        integral[y0 * stride + x0]

    private fun validIndices(valid: BooleanArray): IntArray {
        val output = IntArray(valid.count { it })
        var target = 0
        for (index in valid.indices) {
            if (valid[index]) output[target++] = index
        }
        return output
    }

    private fun sortByElevationDescending(
        indices: IntArray,
        elevations: FloatArray,
        low: Int,
        high: Int,
    ) {
        if (low >= high) return
        var left = low
        var right = high
        val pivot = elevations[indices[(low + high) ushr 1]]
        while (left <= right) {
            while (elevations[indices[left]] > pivot) left++
            while (elevations[indices[right]] < pivot) right--
            if (left <= right) {
                val temporary = indices[left]
                indices[left] = indices[right]
                indices[right] = temporary
                left++
                right--
            }
        }
        if (low < right) sortByElevationDescending(indices, elevations, low, right)
        if (left < high) sortByElevationDescending(indices, elevations, left, high)
    }

    private fun watershedPathContains(path: List<Int>, value: Int): Boolean = path.contains(value)

    private fun receiverDistance(grid: ElevationGrid, from: Int, to: Int): Float {
        val fromX = from % grid.width
        val fromY = from / grid.width
        val toX = to % grid.width
        val toY = to / grid.width
        return grid.cellSizeMeters * if (fromX != toX && fromY != toY) SQRT_TWO else 1f
    }

    private fun percentileMagnitude(values: FloatArray, valid: BooleanArray, percentile: Float): Float {
        val magnitudes = FloatArray(valid.count { it })
        var target = 0
        for (index in values.indices) {
            if (valid[index]) magnitudes[target++] = abs(values[index])
        }
        if (magnitudes.isEmpty()) return 0f
        magnitudes.sort()
        return magnitudes[((magnitudes.lastIndex) * percentile.coerceIn(0f, 1f)).roundToInt()]
    }

    private fun statistics(
        values: FloatArray,
        valid: BooleanArray,
        ignoreNegative: Boolean,
    ): Statistics {
        val selected = FloatArray(valid.count { it })
        var count = 0
        var sum = 0.0
        var minimum = Float.MAX_VALUE
        var maximum = -Float.MAX_VALUE
        for (index in values.indices) {
            if (!valid[index]) continue
            val value = values[index]
            if (!value.isFinite() || ignoreNegative && value < 0f) continue
            selected[count++] = value
            sum += value
            minimum = min(minimum, value)
            maximum = max(maximum, value)
        }
        if (count == 0) return Statistics(0f, 0f, 0f, 0f)
        val trimmed = selected.copyOf(count)
        trimmed.sort()
        val p95 = trimmed[((trimmed.lastIndex) * 0.95f).roundToInt()]
        return Statistics(minimum, maximum, (sum / count).toFloat(), p95)
    }

    private data class Gradient(val dx: Float, val dy: Float)
    private data class IntegralImages(val sum: DoubleArray, val count: IntArray, val stride: Int)
    private data class FloodCell(val index: Int, val elevation: Float)
    private data class Statistics(
        val minimum: Float,
        val maximum: Float,
        val mean: Float,
        val percentile95: Float,
    )

    private val NEIGHBORS = listOf(
        -1 to -1, 0 to -1, 1 to -1,
        -1 to 0, 1 to 0,
        -1 to 1, 0 to 1, 1 to 1,
    )
    private const val SQRT_TWO = 1.41421356f
}
