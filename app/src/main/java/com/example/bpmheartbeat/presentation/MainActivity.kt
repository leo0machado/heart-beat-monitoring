

/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

package com.example.bpmheartbeat.presentation

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.ui.res.stringResource
import androidx.concurrent.futures.await
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.getCapabilities
import com.example.bpmheartbeat.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL


class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var heartRateTextView: TextView
    private lateinit var heartRateValueTextView: TextView
    private lateinit var volumeUpButton: ImageButton
    private lateinit var volumeDownButton: ImageButton
    private lateinit var frequencyButton: Button
    private lateinit var bluetoothButton: Button
    private lateinit var volumeProgressBar: SemicircleProgressBar

    private var volumeSound = 5
    private var frequencyTime = 10
    private val frequencyOptions = arrayOf("10", "30", "60")
    private var heartRateValue = 60
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var deviceAddress = "00:00:00:00:00:00"


    // Check App Permissions
    private val PERMISSIONS_STORAGE = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_PRIVILEGED
    )
    private val PERMISSIONS_LOCATION = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_PRIVILEGED
    )
    private fun checkPermissions() {
        val permission1 = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val permission2 = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
        if (permission1 != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                this,
                PERMISSIONS_STORAGE,
                1
            )
        } else if (permission2 != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                PERMISSIONS_LOCATION,
                1
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        heartRateTextView = findViewById(R.id.heartRateTextView)
        heartRateValueTextView = findViewById(R.id.heartRateValueTextView)
        volumeUpButton = findViewById(R.id.volumeUpButton)
        volumeDownButton = findViewById(R.id.volumeDownButton)
        frequencyButton = findViewById(R.id.frequencyButton)
        bluetoothButton = findViewById(R.id.bluetoothButton)
        volumeProgressBar = findViewById(R.id.volumeProgressBar)
        volumeProgressBar.progress = volumeSound
        heartRateValueTextView.text = "$heartRateValue"

        // Check and request permissions
//        checkPermissions()
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BODY_SENSORS), 1)

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.INTERNET), 1)

        // Initialize SensorManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Check if the device has a heart rate sensor
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (heartRateSensor == null) {
            Toast.makeText(this, "Heart rate sensor not available", Toast.LENGTH_SHORT).show()
        } else {
            // Register sensor listener
            sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_UI, 30000000)
        }

        // Button click listeners
        volumeUpButton.setOnClickListener {
            increaseVolume()
            Log.d("","Volume Aumentado")
        }

        volumeDownButton.setOnClickListener {
            decreaseVolume()
            Log.d("","Volume Abaixado")
        }

        frequencyButton.setOnClickListener {
            showFrequencyOptionsDialog()
        }

        bluetoothButton.setOnClickListener {
            sendDataViaWifi()
        }
    }
    override fun onSensorChanged(event: SensorEvent) {
        Log.d("","Bateu no SensorChanged" + event.values[0])

        if (event.sensor.type == Sensor.TYPE_HEART_RATE && event.values[0] != 0f) {
            val heartRateValue = event.values[0].toInt()
            heartRateValueTextView.text = "$heartRateValue"
        }
        sensorManager.unregisterListener(this)
    }

    private fun increaseVolume() {
        volumeSound = (volumeSound + 1).coerceIn(0, 10)
        updateVolumeTextView()
    }

    private fun decreaseVolume() {
        volumeSound = (volumeSound - 1).coerceIn(0, 10)
        updateVolumeTextView()
    }

    private fun updateVolumeTextView() {
        volumeProgressBar.updateProgress(volumeSound)
    }

    private fun showFrequencyOptionsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Frequency Time")
        builder.setItems(frequencyOptions) { dialog, which ->
            // Update the frequencyTime variable based on the selected option
            frequencyTime = frequencyOptions[which].toInt()
            Toast.makeText(this, "Frequency Time set to $frequencyTime", Toast.LENGTH_SHORT).show()
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun sendDataViaWifi(){
        val jsonData = JSONObject().apply {
            put("batida", 50)
            put("vol", 10)
            put("tempo", 10)
        }.toString()

        val url = "http://192.168.4.1/json"
//        val client = OkHttpClient()
//
//        val jsonType = "application/json".toMediaType();
//
//        val body = jsonData.toRequestBody(jsonType);
//        val request = Request.Builder()
//            .url(url)
//            .post(body)
//            .build();
//        try {
//            val response = client.newCall(request).execute()
//            Log.d("Response HTTP", response.message);
//            Log.d("Response HTTP", response.body.toString());
//        } catch (e: Exception){Log.d("Response HTTP","Error: ${e.message}")}




        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        Log.d("Response HTTP", "Antes do  outputStream")
        Log.d("Response HTTP", jsonData)

        try {
            val outputStream = BufferedWriter(OutputStreamWriter(connection.outputStream, "UTF-8"))
            Log.d("Response HTTP", "antes do Flush")
            outputStream.write(jsonData)
            outputStream.flush()
            Log.d("Response HTTP", "depois do Flush")
            Toast.makeText(this, "Sending Value", Toast.LENGTH_SHORT).show()
            val responseCode = connection.responseCode
            val response = StringBuilder()

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
            } else {
                response.append("POST request failed with error code: $responseCode")
            }

            connection.disconnect()
            Log.d("Response HTTP", response.toString())
        } catch (e: Exception){
            Log.d("Response HTTP","Error: ${e.message}")}

    }



    override fun onPause(){
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_UI, 30000000)
    }

    override fun onDestroy() {
        super.onDestroy()
//         Unregister sensor listener to release resources
        sensorManager.unregisterListener(this)
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        TODO("Not yet implemented")
    }
}