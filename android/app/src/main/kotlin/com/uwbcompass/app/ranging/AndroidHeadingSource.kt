package com.uwbcompass.app.ranging

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Emits the device heading in degrees (0 = magnetic North, clockwise) from the
 * TYPE_ROTATION_VECTOR sensor. This feeds [com.uwbcompass.core.CompassFusion] so the
 * arrow can be re-rendered at sensor rate between (slower) UWB samples.
 *
 * The fusion math is in :core and unit-tested; this class is just the Android sensor plumbing.
 */
class AndroidHeadingSource(private val sensorManager: SensorManager) {

    fun headingDegrees(): Flow<Double> = callbackFlow {
        val rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val matrix = FloatArray(9)
        val orientation = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, orientation)
                val azimuthRad = orientation[0].toDouble() // -pi..pi
                var deg = Math.toDegrees(azimuthRad)
                if (deg < 0) deg += 360.0
                trySend(deg)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
