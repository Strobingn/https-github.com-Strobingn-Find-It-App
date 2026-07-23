package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.analysis.TerrainAnalysisType
import kotlin.math.roundToInt

@Composable
fun TerrainAnalysisScreen(
    terrainViewModel: HillshadeViewModel,
    analysisViewModel: TerrainAnalysisViewModel,
    padding: PaddingValues,
) {
    val grid by terrainViewModel.elevationGrid.collectAsStateWithLifecycle()
    val terrainSummary by terrainViewModel.activeTerrainSummary.collectAsStateWithLifecycle()
    val selectedType by analysisViewModel.selectedType.collectAsStateWithLifecycle()
    val options by analysisViewModel.options.collectAsStateWithLifecycle()
    val layer by analysisViewModel.layer.collectAsStateWithLifecycle()
    val bitmap by analysisViewModel.bitmap.collectAsStateWithLifecycle()
    val isRunning by analysisViewModel.isRunning.collectAsStateWithLifecycle()
    val status by analysisViewModel.status.collectAsStateWithLifecycle()
    val aiInterpretation by analysisViewModel.aiInterpretation.collectAsStateWithLifecycle()
    val isAiRunning by analysisViewModel.isAiRunning.collectAsStateWithLifecycle()
    val menuExpanded = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "LiDAR Analysis",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = terrainSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${grid.width}×${grid.height} cells · ${"%.2f".format(grid.cellSizeMeters)} m/cell",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
        )

        Column {
            OutlinedButton(
                onClick = { menuExpanded.value = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(selectedType.title)
            }
            DropdownMenu(
                expanded = menuExpanded.value,
                onDismissRequest = { menuExpanded.value = false },
            ) {
                TerrainAnalysisType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(type.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    type.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            analysisViewModel.selectType(type)
                            menuExpanded.value = false
                        },
                    )
                }
            }
        }

        Text(
            text = selectedType.description,
            style = MaterialTheme.typography.bodyMedium,
        )

        AnalysisParameterControls(
            selectedType = selectedType,
            localRadius = options.localRadiusMeters,
            horizonRadius = options.horizonRadiusMeters,
            directionCount = options.directionCount,
            erosionIterations = options.erosionIterations,
            rainfallFactor = options.rainfallFactor,
            onLocalRadiusChanged = analysisViewModel::updateLocalRadius,
            onHorizonRadiusChanged = analysisViewModel::updateHorizonRadius,
            onDirectionCountChanged = analysisViewModel::updateDirectionCount,
            onErosionIterationsChanged = analysisViewModel::updateErosionIterations,
            onRainfallFactorChanged = analysisViewModel::updateRainfallFactor,
        )

        Button(
            onClick = { analysisViewModel.runAnalysis(grid) },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 10.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(if (isRunning) "Calculating…" else "Run ${selectedType.title}")
        }

        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        bitmap?.let { rendered ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Image(
                    bitmap = rendered.asImageBitmap(),
                    contentDescription = "${selectedType.title} result",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(rendered.width.toFloat() / rendered.height.coerceAtLeast(1)),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        layer?.let { result ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Layer statistics", fontWeight = FontWeight.Bold)
                    Text("Minimum: ${format(result.minimum)} ${result.type.unit}")
                    Text("Mean: ${format(result.mean)} ${result.type.unit}")
                    Text("95th percentile: ${format(result.percentile95)} ${result.type.unit}")
                    Text("Maximum: ${format(result.maximum)} ${result.type.unit}")
                    HorizontalDivider()
                    Text(result.summary, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Button(
                onClick = { analysisViewModel.requestAiInterpretation(terrainSummary) },
                enabled = !isAiRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isAiRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 10.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(if (isAiRunning) "Asking ChatGPT…" else "Interpret with ChatGPT")
            }
        }

        aiInterpretation?.let { interpretation ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("ChatGPT interpretation", fontWeight = FontWeight.Bold)
                    Text(interpretation, style = MaterialTheme.typography.bodyMedium)
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
    erosionIterations: Int,
    rainfallFactor: Float,
    onLocalRadiusChanged: (Float) -> Unit,
    onHorizonRadiusChanged: (Float) -> Unit,
    onDirectionCountChanged: (Int) -> Unit,
    onErosionIterationsChanged: (Int) -> Unit,
    onRainfallFactorChanged: (Float) -> Unit,
) {
    val usesLocalRadius = selectedType == TerrainAnalysisType.LOCAL_RELIEF_MODEL
    val usesHorizon = selectedType == TerrainAnalysisType.SKY_VIEW_FACTOR ||
        selectedType == TerrainAnalysisType.POSITIVE_OPENNESS ||
        selectedType == TerrainAnalysisType.NEGATIVE_OPENNESS
    val usesErosion = selectedType == TerrainAnalysisType.EROSION_SIMULATION

    if (!usesLocalRadius && !usesHorizon && !usesErosion) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Analysis parameters", fontWeight = FontWeight.Bold)
            if (usesLocalRadius) {
                SliderControl(
                    title = "Local radius",
                    valueLabel = "${localRadius.roundToInt()} m",
                    value = localRadius,
                    valueRange = 1f..100f,
                    onValueChange = onLocalRadiusChanged,
                )
            }
            if (usesHorizon) {
                SliderControl(
                    title = "Horizon radius",
                    valueLabel = "${horizonRadius.roundToInt()} m",
                    value = horizonRadius,
                    valueRange = 5f..250f,
                    onValueChange = onHorizonRadiusChanged,
                )
                SliderControl(
                    title = "Directions",
                    valueLabel = directionCount.toString(),
                    value = directionCount.toFloat(),
                    valueRange = 8f..24f,
                    steps = 15,
                    onValueChange = { onDirectionCountChanged(it.roundToInt()) },
                )
            }
            if (usesErosion) {
                SliderControl(
                    title = "Simulation iterations",
                    valueLabel = erosionIterations.toString(),
                    value = erosionIterations.toFloat(),
                    valueRange = 1f..100f,
                    steps = 98,
                    onValueChange = { onErosionIterationsChanged(it.roundToInt()) },
                )
                SliderControl(
                    title = "Rainfall factor",
                    valueLabel = "${"%.1f".format(rainfallFactor)}×",
                    value = rainfallFactor,
                    valueRange = 0.1f..5f,
                    onValueChange = onRainfallFactorChanged,
                )
            }
        }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title)
            Text(valueLabel, fontFamily = FontFamily.Monospace)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

private fun format(value: Float): String = when {
    kotlin.math.abs(value) >= 1000f -> "%.0f".format(value)
    kotlin.math.abs(value) >= 10f -> "%.2f".format(value)
    else -> "%.4f".format(value)
}
