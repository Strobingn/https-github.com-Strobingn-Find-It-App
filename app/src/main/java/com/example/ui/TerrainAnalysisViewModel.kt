package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.analysis.AiTerrainInterpreter
import com.example.analysis.TerrainAnalysisEngine
import com.example.analysis.TerrainAnalysisLayer
import com.example.analysis.TerrainAnalysisOptions
import com.example.analysis.TerrainAnalysisRenderer
import com.example.analysis.TerrainAnalysisType
import com.example.data.ElevationGrid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TerrainAnalysisViewModel(application: Application) : AndroidViewModel(application) {
    private val aiInterpreter = AiTerrainInterpreter()

    private val _selectedType = MutableStateFlow(TerrainAnalysisType.MULTI_HILLSHADE)
    val selectedType: StateFlow<TerrainAnalysisType> = _selectedType.asStateFlow()

    private val _options = MutableStateFlow(TerrainAnalysisOptions())
    val options: StateFlow<TerrainAnalysisOptions> = _options.asStateFlow()

    private val _layer = MutableStateFlow<TerrainAnalysisLayer?>(null)
    val layer: StateFlow<TerrainAnalysisLayer?> = _layer.asStateFlow()

    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap: StateFlow<Bitmap?> = _bitmap.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _status = MutableStateFlow("Choose an analysis layer, then run it on the active terrain.")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _aiInterpretation = MutableStateFlow<String?>(null)
    val aiInterpretation: StateFlow<String?> = _aiInterpretation.asStateFlow()

    private val _isAiRunning = MutableStateFlow(false)
    val isAiRunning: StateFlow<Boolean> = _isAiRunning.asStateFlow()

    private var analysisJob: Job? = null
    private var aiJob: Job? = null
    private var lastGridIdentity: Int? = null

    fun selectType(type: TerrainAnalysisType) {
        if (_selectedType.value == type) return
        _selectedType.value = type
        _aiInterpretation.value = null
        _status.value = "${type.title} selected. Run analysis to calculate this layer."
    }

    fun updateLocalRadius(value: Float) {
        _options.value = _options.value.copy(localRadiusMeters = value.coerceIn(1f, 100f))
    }

    fun updateHorizonRadius(value: Float) {
        _options.value = _options.value.copy(horizonRadiusMeters = value.coerceIn(5f, 250f))
    }

    fun updateDirectionCount(value: Int) {
        _options.value = _options.value.copy(directionCount = value.coerceIn(8, 24))
    }

    fun updateErosionIterations(value: Int) {
        _options.value = _options.value.copy(erosionIterations = value.coerceIn(1, 100))
    }

    fun updateRainfallFactor(value: Float) {
        _options.value = _options.value.copy(rainfallFactor = value.coerceIn(0.1f, 5f))
    }

    fun runAnalysis(grid: ElevationGrid) {
        analysisJob?.cancel()
        aiJob?.cancel()
        _aiInterpretation.value = null
        val type = _selectedType.value
        val optionsSnapshot = _options.value
        val gridIdentity = System.identityHashCode(grid)
        lastGridIdentity = gridIdentity
        analysisJob = viewModelScope.launch {
            _isRunning.value = true
            _status.value = "Calculating ${type.title} locally…"
            try {
                val result = withContext(Dispatchers.Default) {
                    TerrainAnalysisEngine.analyze(grid, type, optionsSnapshot)
                }
                val rendered = withContext(Dispatchers.Default) {
                    TerrainAnalysisRenderer.render(result)
                }
                if (lastGridIdentity == gridIdentity && _selectedType.value == type) {
                    _layer.value = result
                    _bitmap.value = rendered
                    _status.value = result.summary
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _status.value = error.message ?: "Terrain analysis failed."
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun requestAiInterpretation(terrainSummary: String) {
        val currentLayer = _layer.value ?: run {
            _status.value = "Run a terrain analysis before requesting AI interpretation."
            return
        }
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _isAiRunning.value = true
            _aiInterpretation.value = null
            aiInterpreter.interpret(
                layer = currentLayer,
                terrainSummary = terrainSummary,
            ).onSuccess { interpretation ->
                _aiInterpretation.value = interpretation
            }.onFailure { error ->
                _aiInterpretation.value = "AI interpretation unavailable: ${error.message ?: "Unknown error"}"
            }
            _isAiRunning.value = false
        }
    }

    override fun onCleared() {
        analysisJob?.cancel()
        aiJob?.cancel()
        super.onCleared()
    }
}
