package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sqrt

/**
 * Monitors the physical magnetometer sensor of the Android device.
 * Calculates total magnetic flux density in microTesla (uT) and deviations.
 * Includes a baseline calibration system to filter out ambient Earth magnetic fields.
 */
class MagnetometerMonitor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val magneticSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _magneticFieldStrength = MutableStateFlow(45f) // uT (ambient baseline)
    val magneticFieldStrength: StateFlow<Float> = _magneticFieldStrength

    private val _ambientBaseline = MutableStateFlow(45f)
    val ambientBaseline: StateFlow<Float> = _ambientBaseline

    private val _isSensorAvailable = MutableStateFlow(magneticSensor != null)
    val isSensorAvailable: StateFlow<Boolean> = _isSensorAvailable

    private var filterWindow = ArrayList<Float>()
    private val filterSize = 25

    fun startListening() {
        if (magneticSensor != null) {
            sensorManager.registerListener(
                this,
                magneticSensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    /**
     * Resets baseline using current ambient magnetic field readings.
     */
    fun calibrateBaseline() {
        _ambientBaseline.value = _magneticFieldStrength.value
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Calculate total magnitude (hypotenuse of 3D vector)
            val magnitude = sqrt(x * x + y * y + z * z)

            // Apply light moving-average filter to smooth jitter
            filterWindow.add(magnitude)
            if (filterWindow.size > filterSize) {
                filterWindow.removeAt(0)
            }
            val smoothed = filterWindow.average().toFloat()

            _magneticFieldStrength.value = smoothed
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
