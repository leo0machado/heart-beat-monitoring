

/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

package com.example.bpmheartbeat.presentation

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.ParcelUuid
import android.system.Os.socket
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.bpmheartbeat.R
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import java.io.OutputStream


class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var heartRateTextView: TextView
    private lateinit var volumeTextView: TextView
    private lateinit var volumeUpButton: Button
    private lateinit var volumeDownButton: Button
    private lateinit var frequencyButton: Button
    private lateinit var bluetoothButton: Button

    private var volumeSound = 5
    private var frequencyTime = 10
    private val frequencyOptions = arrayOf("10", "30", "60")
    private var heartRateValue = 0
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
        volumeTextView = findViewById(R.id.volumeTextView)
        volumeUpButton = findViewById(R.id.volumeUpButton)
        volumeDownButton = findViewById(R.id.volumeDownButton)
        frequencyButton = findViewById(R.id.frequencyButton)
        bluetoothButton = findViewById(R.id.bluetoothButton)

        // Check and request permissions
        checkPermissions()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.BODY_SENSORS),
                REQUEST_PERMISSION_BODY_SENSORS)
        }

        // Initialize SensorManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Check if the device has a heart rate sensor
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (heartRateSensor == null) {
            // Heart rate sensor is not available on this device
            Toast.makeText(this, "Heart rate sensor not available", Toast.LENGTH_SHORT).show()
        } else {
            // Register sensor listener
            sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
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
            sendDataViaBluetooth()
        }
    }

//    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
//        Log.d("","Bateu no DataChanged")
//        for (event in dataEventBuffer) {
//            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == HEART_RATE_PATH) {
//                // Extract heart rate data from the event
//                heartRateValue = event.dataItem.toString()
//                heartRateTextView.text = "Heart Rate" + heartRateValue + "BPM"
//            }
//        }
//    }

    private fun increaseVolume() {
        volumeSound = (volumeSound + 1).coerceIn(0, 10)
        updateVolumeTextView()
    }

    private fun decreaseVolume() {
        volumeSound = (volumeSound - 1).coerceIn(0, 10)
        updateVolumeTextView()
    }

    private fun updateVolumeTextView() {
        volumeTextView.text = "Volume Sound:" + volumeSound.toString()
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

    private fun sendDataViaBluetooth() {

        val jsonData = JSONObject().apply {
            put("batida", heartRateValue)
            put("vol", volumeSound)
            put("tempo", frequencyTime)
        }

        val mainJsonObject = JSONObject().apply {
            put("smartwatch", jsonData)
        }


        // Get Bluetooth adapter
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        var bluetoothSocket: BluetoothSocket? = null

        // Ensure Bluetooth is supported on this device
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_SHORT).show()
            return
        }

        // Ensure Bluetooth is enabled
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Bluetooth is not enabled", Toast.LENGTH_SHORT).show()
            return
        }

        // Get the paired device you want to connect to
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            Log.d("BLUETOOTH PERMISSION ERROR", "Bluetooth Permission Not Granted")
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.BLUETOOTH_ADMIN),
                1)
        }

        val pairedDevices = bluetoothAdapter.bondedDevices.toList()

        // If bluetooth is not connected, show a Dialog to select Address.
        if (deviceAddress == "00:00:00:00:00:00") {
            val deviceNames = pairedDevices.map { it.name }.toTypedArray()

            val builder = AlertDialog.Builder(this)
            builder.setTitle("Select Bluetooth Device")
            builder.setItems(deviceNames) { _, which ->
                // User clicked on item at position 'which'
                val selectedDevice = pairedDevices[which]
                deviceAddress = selectedDevice.address
                Toast.makeText(this, "Selected device: $deviceAddress", Toast.LENGTH_SHORT).show()
            }



            val dialog = builder.create()
            dialog.show()
        }

        val bluetoothDevice = pairedDevices.find { it.address == deviceAddress }

        if (bluetoothDevice == null) {
            Toast.makeText(this, "Bluetooth is not enabled", Toast.LENGTH_SHORT).show()
            return
        }

        // Connect to the device
        try {
//            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            val uuids = bluetoothDevice.uuids as Array<ParcelUuid>

            Log.d("", uuids.toString() + "valor do UUID: " + uuids[0].uuid.toString())

            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuids[0].uuid)
//            bluetoothSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuids[0].uuid)
            bluetoothSocket.connect()

            // Send data
            val outputStream: OutputStream = bluetoothSocket!!.outputStream
            val output = mainJsonObject.toString().toByteArray()
            outputStream.write(output)

            Toast.makeText(this, "Data sent via Bluetooth", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send data via Bluetooth: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.d("", "Failed to send data via Bluetooth: ${e.message}")
        } finally {
            // Close the socket after sending data
            bluetoothSocket?.close()
        }
    }

    companion object {
        private const val REQUEST_PERMISSION_BODY_SENSORS = 1
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister sensor listener to release resources
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        Log.d("", "Acessou Sensor Changed")
        if (event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
            heartRateValue = event.values?.get(0)?.toInt()!!
        }
        heartRateTextView.text = "Heart Rate: " + heartRateValue + " BPM"
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        TODO("Not yet implemented")
    }
}