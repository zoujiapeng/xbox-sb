package com.padmax.controller

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.concurrent.atomic.AtomicReference

class SensorFusion(context: Context) : SensorEventListener, AutoCloseable {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val values = AtomicReference(FloatArray(6))
    private var enabled = false
    fun start(){if(enabled)return;enabled=true;accel?.let{sensorManager.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)};gyro?.let{sensorManager.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)}}
    fun stop(){if(!enabled)return;enabled=false;sensorManager.unregisterListener(this)}
    fun applyTo(state:ControllerState){val v=values.get();state.accelX=v[0];state.accelY=v[1];state.accelZ=v[2];state.gyroX=v[3];state.gyroY=v[4];state.gyroZ=v[5]}
    override fun onSensorChanged(event:SensorEvent){val old=values.get().copyOf();when(event.sensor.type){Sensor.TYPE_ACCELEROMETER->{old[0]=event.values.getOrElse(0){0f};old[1]=event.values.getOrElse(1){0f};old[2]=event.values.getOrElse(2){0f}};Sensor.TYPE_GYROSCOPE->{old[3]=event.values.getOrElse(0){0f};old[4]=event.values.getOrElse(1){0f};old[5]=event.values.getOrElse(2){0f}}};values.set(old)}
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int)=Unit
    override fun close()=stop()
}
