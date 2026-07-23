package com.example.analysis

/** Every locally-computed LiDAR/DEM product exposed by the Analysis screen. */
enum class TerrainAnalysisType(
    val title: String,
    val description: String,
    val unit: String,
    val diverging: Boolean = false,
    val categorical: Boolean = false,
) {
    MULTI_HILLSHADE(
        title = "Multi-layer hillshade",
        description = "Blends illumination from four directions to reduce directional bias.",
        unit = "intensity",
    ),
    SKY_VIEW_FACTOR(
        title = "Sky-View Factor",
        description = "Estimates how much of the sky hemisphere is visible from each cell.",
        unit = "fraction",
    ),
    LOCAL_RELIEF_MODEL(
        title = "Local Relief Model",
        description = "Removes broad terrain trend to expose subtle banks, pits, platforms and walls.",
        unit = "m",
        diverging = true,
    ),
    POSITIVE_OPENNESS(
        title = "Positive openness",
        description = "Highlights exposed ridges, banks and raised structures.",
        unit = "degrees",
    ),
    NEGATIVE_OPENNESS(
        title = "Negative openness",
        description = "Highlights enclosed hollows, ditches, pits and cellar depressions.",
        unit = "degrees",
    ),
    SLOPE(
        title = "Slope",
        description = "Maximum terrain gradient calculated with a Horn 3x3 operator.",
        unit = "degrees",
    ),
    CURVATURE(
        title = "Curvature",
        description = "Second derivative surface separating convex and concave landforms.",
        unit = "1/m",
        diverging = true,
    ),
    ASPECT(
        title = "Aspect",
        description = "Downslope compass direction; flat cells are marked neutral.",
        unit = "degrees",
        categorical = true,
    ),
    RUGGEDNESS_INDEX(
        title = "Ruggedness Index",
        description = "RMS elevation difference between each cell and its neighbors.",
        unit = "m",
    ),
    FLOW_ACCUMULATION(
        title = "Flow accumulation",
        description = "D8 upstream contributing-cell count using the bare-earth surface.",
        unit = "cells",
    ),
    WATERSHED(
        title = "Watersheds",
        description = "Labels drainage basins by their terminal outlet or internal sink.",
        unit = "basin",
        categorical = true,
    ),
    DEPRESSION_DEPTH(
        title = "Depression finder",
        description = "Priority-flood fill depth for closed pits and enclosed depressions.",
        unit = "m",
    ),
    ANCIENT_STREAM_LIKELIHOOD(
        title = "Ancient stream reconstruction",
        description = "Ranks valley-like corridors from flow, concavity and low-gradient terrain.",
        unit = "score",
    ),
    EROSION_SIMULATION(
        title = "Erosion simulation",
        description = "Relative erosion/deposition estimate from drainage, slope and curvature.",
        unit = "relative change",
        diverging = true,
    ),
}

data class TerrainAnalysisOptions(
    val localRadiusMeters: Float = 12f,
    val horizonRadiusMeters: Float = 40f,
    val directionCount: Int = 12,
    val erosionIterations: Int = 30,
    val rainfallFactor: Float = 1f,
) {
    fun normalized(cellSizeMeters: Float): TerrainAnalysisOptions = copy(
        localRadiusMeters = localRadiusMeters.coerceAtLeast(cellSizeMeters),
        horizonRadiusMeters = horizonRadiusMeters.coerceAtLeast(cellSizeMeters * 2f),
        directionCount = directionCount.coerceIn(8, 24),
        erosionIterations = erosionIterations.coerceIn(1, 100),
        rainfallFactor = rainfallFactor.coerceIn(0.1f, 5f),
    )
}

data class TerrainAnalysisLayer(
    val type: TerrainAnalysisType,
    val width: Int,
    val height: Int,
    val values: FloatArray,
    val validData: BooleanArray,
    val cellSizeMeters: Float,
    val minimum: Float,
    val maximum: Float,
    val mean: Float,
    val percentile95: Float,
    val summary: String,
) {
    init {
        require(values.size == width * height)
        require(validData.size == width * height)
    }

    fun aiSummary(): String = buildString {
        append(type.title)
        append(": ")
        append(summary)
        append(" Grid ")
        append(width)
        append('x')
        append(height)
        append(", cell size ")
        append("%.2f".format(cellSizeMeters))
        append(" m, min ")
        append("%.4f".format(minimum))
        append(", mean ")
        append("%.4f".format(mean))
        append(", p95 ")
        append("%.4f".format(percentile95))
        append(", max ")
        append("%.4f".format(maximum))
        append(' ')
        append(type.unit)
        append('.')
    }
}
