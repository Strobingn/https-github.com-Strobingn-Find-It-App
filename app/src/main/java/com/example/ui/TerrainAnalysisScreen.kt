package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.analysis.AnalysisPalette
import com.example.analysis.TerrainAnalysisType
import com.example.analysis.TerrainRenderOptions
import kotlin.math.roundToInt

enum class AnalysisDisplayMode(val title: String) {
    TERRAIN("Terrain"),
    ANALYSIS("Analysis"),
    BLENDED("Blended"),
}

@Composable
fun TerrainAnalysisScreen(
    terrainViewModel: HillshadeViewModel,
    analysisViewModel: TerrainAnalysisViewModel,
    padding: PaddingValues,
) {
    val grid by terrainViewModel.elevationGrid.collectAsStateWithLifecycle()
    val terrainBitmap by terrainViewModel.hillshadeBitmap.collectAsStateWithLifecycle()
    val terrainSummary by terrainViewModel.activeTerrainSummary.collectAsStateWithLifecycle()
    val selectedType by analysisViewModel.selectedType.collectAsStateWithLifecycle()
    val options by analysisViewModel.options.collectAsStateWithLifecycle()
    val renderOptions by analysisViewModel.renderOptions.collectAsStateWithLifecycle()
    val layer by analysisViewModel.layer.collectAsStateWithLifecycle()
    val bitmap by analysisViewModel.bitmap.collectAsStateWithLifecycle()
    val isRunning by analysisViewModel.isRunning.collectAsStateWithLifecycle()
    val status by analysisViewModel.status.collectAsStateWithLifecycle()
    val cacheEntryCount by analysisViewModel.cacheEntryCount.collectAsStateWithLifecycle()
    val lastResultWasCached by analysisViewModel.lastResultWasCached.collectAsStateWithLifecycle()
    val exportStatus by analysisViewModel.exportStatus.collectAsStateWithLifecycle()
    val isExporting by analysisViewModel.isExporting.collectAsStateWithLifecycle()
    val aiInterpretation by analysisViewModel.aiInterpretation.collectAsStateWithLifecycle()
    val isAiRunning by analysisViewModel.isAiRunning.collectAsStateWithLifecycle()
    val menuExpanded = remember { mutableStateOf(false) }
    val displayMode = remember { mutableStateOf(AnalysisDisplayMode.BLENDED) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Phase 3 · Terrain Analysis", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Validated calculations and professional rendering",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (cacheEntryCount > 0) {
                TextButton(onClick = analysisViewModel::clearCache) { Text("Clear ($cacheEntryCount)") }
            }
        }

        Text(terrainSummary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${grid.width}×${grid.height} cells · ${"%.2f".format(grid.cellSizeMeters)} m/cell",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
        )

        Column {
            OutlinedButton(onClick = { menuExpanded.value = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedType.title)
            }
            DropdownMenu(expanded = menuExpanded.value, onDismissRequest = { menuExpanded.value = false }) {
                TerrainAnalysisType.phaseOneEntries.forEach { type ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(type.title, fontWeight = FontWeight.SemiBold)
                                Text(type.description, style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        onClick = {
                            analysisViewModel.selectType(type)
                            analysisViewModel.runAnalysis(grid)
                            menuExpanded.value = false
                        },
                    )
                }
            }
        }

        Text(selectedType.description, style = MaterialTheme.typography.bodyMedium)

        AnalysisParameterControls(
            selectedType = selectedType,
            localRadius = options.localRadiusMeters,
            horizonRadius = options.horizonRadiusMeters,
            directionCount = options.directionCount,
            onLocalRadiusChanged = analysisViewModel::updateLocalRadius,
            onHorizonRadiusChanged = analysisViewModel::updateHorizonRadius,
            onDirectionCountChanged = analysisViewModel::updateDirectionCount,
        )

        AnalysisVisualizationControls(
            options = renderOptions,
            displayMode = displayMode.value,
            onDisplayModeChanged = { displayMode.value = it },
            onPaletteChanged = analysisViewModel::updateAnalysisPalette,
            onContrastChanged = analysisViewModel::updateAnalysisContrast,
            onBrightnessChanged = analysisViewModel::updateAnalysisBrightness,
            onOpacityChanged = analysisViewModel::updateAnalysisOpacity,
            onInvertedChanged = analysisViewModel::setAnalysisPaletteInverted,
        )

        if (isRunning) {
            OutlinedButton(onClick = analysisViewModel::cancelAnalysis, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp), strokeWidth = 2.dp)
                Text("Cancel ${selectedType.title}")
            }
        } else {
            Button(onClick = { analysisViewModel.runAnalysis(grid) }, modifier = Modifier.fillMaxWidth()) {
                Text("Run ${selectedType.title}")
            }
        }

        Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (lastResultWasCached) {
            Text("Cached result · no recalculation required", color = MaterialTheme.colorScheme.primary)
        }

        bitmap?.let { analysisBitmap ->
            val previewBitmap = when (displayMode.value) {
                AnalysisDisplayMode.TERRAIN -> terrainBitmap
                else -> analysisBitmap
            }
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(previewBitmap.width.toFloat() / previewBitmap.height.coerceAtLeast(1)),
                    ) {
                        if (displayMode.value != AnalysisDisplayMode.ANALYSIS) {
                            Image(
                                bitmap = terrainBitmap.asImageBitmap(),
                                contentDescription = "Terrain hillshade",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        if (displayMode.value != AnalysisDisplayMode.TERRAIN) {
                            Image(
                                bitmap = analysisBitmap.asImageBitmap(),
                                contentDescription = "${selectedType.title} result",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                    if (displayMode.value != AnalysisDisplayMode.TERRAIN) {
                        AnalysisLegend(selectedType, renderOptions)
                    } else {
                        Text(
                            "Original terrain hillshade",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }

        layer?.let { result ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Layer statistics", fontWeight = FontWeight.Bold)
                    Text("Valid cells: ${result.validCellCount} (${format(result.coveragePercent)}%)")
                    Text("Minimum: ${format(result.minimum)} ${result.type.unit}")
                    Text("Mean: ${format(result.mean)} ${result.type.unit}")
                    Text("Standard deviation: ${format(result.standardDeviation)} ${result.type.unit}")
                    Text("95th percentile: ${format(result.percentile95)} ${result.type.unit}")
                    Text("Maximum: ${format(result.maximum)} ${result.type.unit}")
                    HorizontalDivider()
                    Text(result.summary)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = analysisViewModel::exportCurrentPng,
                    enabled = !isExporting,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isExporting) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    Text(if (isExporting) "Saving…" else "Save PNG")
                }
                Button(
                    onClick = { analysisViewModel.requestAiInterpretation(terrainSummary) },
                    enabled = !isAiRunning,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isAiRunning) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    Text(if (isAiRunning) "Analyzing…" else "ChatGPT")
                }
            }
        }

        exportStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        aiInterpretation?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ChatGPT interpretation", fontWeight = FontWeight.Bold)
                    Text(it)
                }
            }
        }
    }
}

@Composable
private fun AnalysisParameterControls(
    selectedType: TerrainAnalysisType,
    localRadius: Float,
    horizonRadius: Float,
    directionCount: Int,
    onLocalRadiusChanged: (Float) -> Unit,
    onHorizonRadiusChanged: (Float) -> Unit,
    onDirectionCountChanged: (Int) -> Unit,
) {
    if (!selectedType.usesLocalRadius && !selectedType.usesHorizon) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Analysis parameters", fontWeight = FontWeight.Bold)
            if (selectedType.usesLocalRadius) {
                SliderControl("Local radius", "${localRadius.roundToInt()} m", localRadius, 1f..100f, onValueChange = onLocalRadiusChanged)
            }
            if (selectedType.usesHorizon) {
                SliderControl("Horizon radius", "${horizonRadius.roundToInt()} m", horizonRadius, 5f..250f, onValueChange = onHorizonRadiusChanged)
                SliderControl("Directions", directionCount.toString(), directionCount.toFloat(), 8f..24f, 15) {
                    onDirectionCountChanged(it.roundToInt())
                }
            }
            Text("Tap Run after changing an analysis parameter.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AnalysisVisualizationControls(
    options: TerrainRenderOptions,
    displayMode: AnalysisDisplayMode,
    onDisplayModeChanged: (AnalysisDisplayMode) -> Unit,
    onPaletteChanged: (AnalysisPalette) -> Unit,
    onContrastChanged: (Float) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onInvertedChanged: (Boolean) -> Unit,
) {
    val paletteMenuExpanded = remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Visualization", fontWeight = FontWeight.Bold)
            Text("Preview mode", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AnalysisDisplayMode.entries.forEach { mode ->
                    if (mode == displayMode) {
                        Button(onClick = { onDisplayModeChanged(mode) }, modifier = Modifier.weight(1f)) { Text(mode.title) }
                    } else {
                        OutlinedButton(onClick = { onDisplayModeChanged(mode) }, modifier = Modifier.weight(1f)) { Text(mode.title) }
                    }
                }
            }
            Column {
                Text("Palette", style = MaterialTheme.typography.labelMedium)
                OutlinedButton(onClick = { paletteMenuExpanded.value = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(options.palette.title)
                }
                DropdownMenu(expanded = paletteMenuExpanded.value, onDismissRequest = { paletteMenuExpanded.value = false }) {
                    AnalysisPalette.entries.forEach { palette ->
                        DropdownMenuItem(text = { Text(palette.title) }, onClick = {
                            onPaletteChanged(palette)
                            paletteMenuExpanded.value = false
                        })
                    }
                }
            }
            SliderControl("Contrast", "${"%.1f".format(options.contrast)}×", options.contrast, 0.5f..3f, onValueChange = onContrastChanged)
            SliderControl("Brightness", "${"%.2f".format(options.brightness)}×", options.brightness, 0.5f..1.75f, onValueChange = onBrightnessChanged)
            SliderControl("Overlay opacity", "${(options.opacity * 100f).roundToInt()}%", options.opacity, 0.1f..1f, onValueChange = onOpacityChanged)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Invert palette")
                    Text("Changes colors only; terrain values are unchanged.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = options.inverted, onCheckedChange = onInvertedChanged)
            }
            Text("Blended mode places the analysis over the original hillshade. Rendering controls do not recalculate terrain.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AnalysisLegend(type: TerrainAnalysisType, options: TerrainRenderOptions) {
    val colors = legendColors(type, options)
    val labels = when {
        options.palette == AnalysisPalette.GRAYSCALE -> "Dark  ←  ${type.unit}  →  light"
        type == TerrainAnalysisType.ASPECT -> "N · E · S · W · flat cells neutral"
        type == TerrainAnalysisType.CURVATURE -> "Convex / raised  ←  neutral  →  concave / depressed"
        type.diverging -> "Negative  ←  neutral  →  positive"
        type == TerrainAnalysisType.MULTI_HILLSHADE -> "Shadow  ←  illumination  →  bright"
        type == TerrainAnalysisType.SKY_VIEW_FACTOR -> "Enclosed  ←  sky visibility  →  open"
        type == TerrainAnalysisType.POSITIVE_OPENNESS -> "Enclosed  ←  exposed ridges / mounds  →  open"
        type == TerrainAnalysisType.NEGATIVE_OPENNESS -> "Open  ←  enclosed pits / ditches  →  strong"
        else -> "Low  ←  ${type.unit}  →  high"
    }
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(Brush.horizontalGradient(if (options.inverted) colors.reversed() else colors), RoundedCornerShape(7.dp)),
        )
        Text(if (options.inverted) "$labels · inverted" else labels, fontFamily = FontFamily.Monospace)
        Text("Brightness ${"%.2f".format(options.brightness)}× · opacity ${(options.opacity * 100f).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
    }
}

private fun legendColors(type: TerrainAnalysisType, options: TerrainRenderOptions): List<Color> {
    if (options.palette == AnalysisPalette.GRAYSCALE) return listOf(Color(0xFF141414), Color(0xFF888888), Color.White)
    if (options.palette == AnalysisPalette.HIGH_CONTRAST) {
        return if (type.diverging || type == TerrainAnalysisType.CURVATURE) {
            listOf(Color.Blue, Color.White, Color.Red)
        } else {
            listOf(Color.Black, Color(0xFF23005F), Color(0xFF0050E6), Color(0xFF00E6B9), Color.Yellow, Color.White)
        }
    }
    return when (type) {
        TerrainAnalysisType.ASPECT -> listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
        TerrainAnalysisType.CURVATURE,
        TerrainAnalysisType.LOCAL_RELIEF_MODEL,
        TerrainAnalysisType.EROSION_SIMULATION,
        -> listOf(Color(0xFF1952CD), Color(0xFFE1E1DA), Color(0xFFDC2A26))
        TerrainAnalysisType.SKY_VIEW_FACTOR -> listOf(Color(0xFF0F1630), Color(0xFF225CA0), Color(0xFF46BECD), Color(0xFFFAFAE1))
        TerrainAnalysisType.POSITIVE_OPENNESS,
        TerrainAnalysisType.NEGATIVE_OPENNESS,
        -> listOf(Color(0xFF1E1232), Color(0xFF6E2D87), Color(0xFFEB7337), Color(0xFFFFF4B4))
        TerrainAnalysisType.DEPRESSION_DEPTH,
        TerrainAnalysisType.ANCIENT_STREAM_LIKELIHOOD,
        -> listOf(Color(0xFF141C3A), Color(0xFF2878BE), Color(0xFFFFC107), Color(0xFFDC2D2D))
        TerrainAnalysisType.MULTI_HILLSHADE -> listOf(Color(0xFF232323), Color(0xFF888888), Color(0xFFFAFAFA))
        else -> listOf(Color(0xFF141C3A), Color(0xFF195E91), Color(0xFF23AAA0), Color(0xFFD5D25A), Color(0xFFFAF5DC))
    }
}

@Composable
private fun SliderControl(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title)
            Text(valueLabel, fontFamily = FontFamily.Monospace)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps)
    }
}

private fun format(value: Float): String = when {
    kotlin.math.abs(value) >= 1000f -> "%.0f".format(value)
    kotlin.math.abs(value) >= 10f -> "%.2f".format(value)
    else -> "%.4f".format(value)
}
