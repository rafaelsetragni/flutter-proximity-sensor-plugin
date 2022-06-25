package dev.jeremyko.proximity_sensor

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.view.WindowManager
import androidx.annotation.RequiresApi
import io.flutter.Log
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import java.io.IOException
import java.lang.UnsupportedOperationException

////////////////////////////////////////////////////////////////////////////////////////////////////
class ProximityStreamHandler(
    private val applicationContext: Context,
    private val messenger: BinaryMessenger
): EventChannel.StreamHandler, SensorEventListener {

    var activity: Activity? = null
    private val powerManager: PowerManager =
        applicationContext.getSystemService(Context.POWER_SERVICE)!! as PowerManager

    private var wakeLock:WakeLock? = null

    private var eventSink: EventChannel.EventSink? = null
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        sensorManager =  applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?:
                throw UnsupportedOperationException("proximity sensor unavailable")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if(wakeLock == null)
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "lock:proximity_screen_off")

            if(!wakeLock!!.isHeld){
                wakeLock!!.acquire(10 * 60 * 1000L /*10 minutes*/)
                Log.d("ProdimityStreamHandler", "WakeLock("+wakeLock.hashCode()+") acquired")
            }
        }

        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onCancel(arguments: Any?) {
        sensorManager.unregisterListener(this, proximitySensor)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if(wakeLock != null && wakeLock!!.isHeld){
                Log.d("ProdimityStreamHandler", "WakeLock("+wakeLock.hashCode()+") released")
                wakeLock!!.release()
                wakeLock = null
            }
        }

        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val distance = event ?.values?.get(0)?.toInt() ?: return //get distance
        eventSink?.success(if (distance > 0) 0 else 1)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }
}