package com.example.potholedetection

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.example.potholedetection.ui.theme.PotholeDetectionTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationRequest.Builder.IMPLICIT_MIN_UPDATE_INTERVAL
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin

private lateinit var fusedLocationClient: FusedLocationProviderClient

@SuppressLint("SimpleDateFormat")
val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
var date = Date()

var lastKnownLocation by mutableStateOf(Pair(0.0, 0.0))
var locationLastUpdated: String by mutableStateOf(formatter.format(date))

var accelerometerReading: String by mutableStateOf("0, 0, 0")

var potHoleFound by mutableStateOf("")

var potHoles: MutableList<Pair<Double, Double>> = mutableListOf()
var distanceToNearest: Double by mutableDoubleStateOf(Double.MAX_VALUE)

const val MAX_WARNING_DISTANCE = 100
var ACCELERATION_POTHOLE_THRESHOLD: Float by mutableFloatStateOf(300f)

fun toRadians(n: Double): Double {
    return n * 2 * PI / 360
}

fun haversine(a: Pair<Double, Double>, b: Pair<Double, Double>): Double{
    return 2*6371000*asin((sin(toRadians(a.first - b.first)).pow(2)+ kotlin.math.cos(
        toRadians(a.first)
    ) * kotlin.math.cos(
        toRadians(b.first)
    )*sin(toRadians((a.second - b.second) /2)).pow(2)).pow(0.5))
}


class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest

    private var requestingLocationUpdates: Boolean = true

    private fun getLocationFunc(potholeFound: Boolean = false) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusedLocationClient.lastLocation // this uses the last location stored
        //fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null) // this requests a current location but takes time
            .addOnSuccessListener { location: Location? ->
                // Got last known location. In some rare situations this can be null.
                println("location is $location")
                if (location != null) {
                    lastKnownLocation = Pair(location.latitude, location.longitude)
                    date = Date()
                    locationLastUpdated = formatter.format(date)
                    if (potholeFound) {
                        potHoleFound = "Pothole found at: $lastKnownLocation"
                        ToneGenerator(AudioManager.STREAM_MUSIC, 100).startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                        var alreadyFound = false
                        loop@ for (item in potHoles) {
                            val distance = haversine(item, lastKnownLocation)
                            if (distance < 10) {
                                alreadyFound = true
                                break@loop
                            }
                        }
                        if (!alreadyFound) {
                            potHoles.add(lastKnownLocation)
                        }
                        distanceToNearest = 0.0
                    } else {
                        distanceToNearest = Double.MAX_VALUE
                        for (item in potHoles){
                            val distance = haversine(item, lastKnownLocation)
                            if (distance < distanceToNearest){
                                distanceToNearest = distance
                            }
                        }
                    }
                }
            }
        return
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PotholeDetectionTheme {
                Column {
                    Title(modifier = Modifier.padding(50.dp))
                    LocationText(modifier = Modifier.padding(18.dp))
                    LocationUpdateText(modifier = Modifier.padding(18.dp))
                    // GetLocationButton(modifier = Modifier.padding(18.dp))
                    Button(onClick = {
                        getLocationFunc()
                        println("Button pressed. Location is $lastKnownLocation")
                    }) {
                        Text(
                            text = "Get Latest Location",
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                    AccelerometerReadingText(modifier = Modifier.padding(18.dp))
                    PotholesFoundText(modifier = Modifier.padding(18.dp))
                    PotholeWarningText(modifier = Modifier.padding(18.dp))
                    ThresholdSlider()
                }
            }
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)
                getLocationFunc()
            }
        }

        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        println(event)
        if (event != null) {
            if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                accelerometerReading = "${event.values[0]}, ${event.values[1]}, ${event.values[2]}"
                if (event.values[0].pow(2) + event.values[1].pow(2) + event.values[2].pow(2) >= ACCELERATION_POTHOLE_THRESHOLD) {
                    getLocationFunc(potholeFound = true)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        println("Accuracy has changed")
    }

    override fun onResume() {
        super.onResume()
        if (requestingLocationUpdates) startLocationUpdates()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }

        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500)
            .apply {
                setWaitForAccurateLocation(true)
                setMinUpdateIntervalMillis(IMPLICIT_MIN_UPDATE_INTERVAL)
            }.build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}

@Composable
fun Title(modifier: Modifier = Modifier) {
    Text(
        text = "Pothole Detection",
        modifier = modifier,
        fontSize = 30.sp
    )
}

@Composable
fun LocationText(modifier: Modifier = Modifier) {
    Text(
        text = "Current location is: $lastKnownLocation",
        modifier = modifier
    )
}

@Composable
fun LocationUpdateText(modifier: Modifier = Modifier) {
    Text(
        text = "Current location last updated: $locationLastUpdated",
        modifier = modifier
    )
}
/*
@Composable
fun GetLocationButton(modifier: Modifier = Modifier, glf: () -> ) {
    Button(onClick = {
    println("Button pressed. Location is $lastKnownLocation")
    }) {
        Text(
            text = "Get Latest Location",
            modifier = modifier
        )
    }
}*/

@Composable
fun AccelerometerReadingText(modifier: Modifier = Modifier) {
    Text(
        text = "Current acceleration value is : $accelerometerReading",
        modifier = modifier
    )
}

@Composable
fun PotholesFoundText(modifier: Modifier = Modifier) {
    Text(
        text = potHoleFound,
        modifier = modifier
    )
    Text(
        text = "Number of potholes that have been found : ${potHoles.size}",
        modifier = modifier
    )
}

@Composable
fun PotholeWarningText(modifier: Modifier = Modifier){
    var redVal = 0
    if (distanceToNearest < MAX_WARNING_DISTANCE){
        redVal = 255-(distanceToNearest/ MAX_WARNING_DISTANCE * 255).toInt()
    }
    val modifier2 = modifier.background(color = Color(255, 0, 0, redVal))
    Text(
        text = "Distance to the nearest pothole : ${round(distanceToNearest)}m, $redVal",
        modifier = modifier2
    )
}

@Composable
fun ThresholdSlider(){
    Row {
        Slider(
            value = ACCELERATION_POTHOLE_THRESHOLD,
            onValueChange = { ACCELERATION_POTHOLE_THRESHOLD = it },
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .padding(15.dp, 0.dp),
            valueRange = 0f..900f,
            steps = 90
        )
        Text(text = "Threshold: $ACCELERATION_POTHOLE_THRESHOLD")
    }
}
