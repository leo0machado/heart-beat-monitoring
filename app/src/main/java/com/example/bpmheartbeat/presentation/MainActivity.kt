

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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random


class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var heartRateValueTextView: TextView
    private lateinit var volumeUpButton: ImageButton
    private lateinit var volumeDownButton: ImageButton
    private lateinit var frequencyButton: ImageButton
    private lateinit var bluetoothButton: ImageButton
    private lateinit var volumeProgressBar: SemicircleProgressBar

    private var volumeSound = 5
    private var frequencyTime = 10
    private val frequencyOptions = arrayOf("10", "30", "60")
    private var heartRateValue = Random.nextInt(60,80)
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


    data class DataModel(
        val batida: Int,
        val vol: Int,
        val tempo: Int
    )

    interface ApiService {
        @POST("/json")
        fun sendJson(@Body data: DataModel): Call<Void>

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
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

    private fun sendDataViaWifi() {
        // Definir a URL do servidor
        val url = "http://192.168.4.1"
//        val jsonData = DataModel(50, 10, 10)
//        // Configurar o OkHttpClient com tempos limite personalizados e adicionar interceptor
//        val client = createOkHttpClient()
//
//        val retrofit = Retrofit.Builder()
//            .baseUrl(url)
//            .client(client) // Configurar o cliente OkHttpClient personalizado
//            .addConverterFactory(GsonConverterFactory.create())
//            .build()
//
//        val service = retrofit.create(ApiService::class.java)
//        //  val gson = Gson()
//        // val jsonData = gson.toJson(DataModel(50, 10, 10))
//        // Enviar uma solicitação POST com o JSON
//        service.sendJson(jsonData).enqueue(object : Callback<Void> {
//            override fun onResponse(call: Call<Void>, response: Response<Void>) {
//                if (response.isSuccessful) {
//                    // Sucesso na solicitação
//                    Toast.makeText(
//                        this@MainActivity,
//                        "Dados enviados com sucesso",
//                        Toast.LENGTH_SHORT
//                    ).show()
//                    Log.d("TAG", "Dados enviados com sucesso")
//                } else {
//                    // Código de resposta de erro
//                    val message = response.message()
//                    // Lidar com erro
//                    Toast.makeText(
//                        this@MainActivity,
//                        "Erro na solicitação: $message",
//                        Toast.LENGTH_SHORT
//                    ).show()
//                    Log.e("TAG", "Erro na solicitação: $message")
//                }
//            }
//
//            override fun onFailure(call: Call<Void>, t: Throwable) {
//                // Falha na solicitação
//                // Lidar com erro
//                Toast.makeText(
//                    this@MainActivity,
//                    "Falha na solicitação: ${t.message}",
//                    Toast.LENGTH_SHORT
//                ).show()
//                Log.e("TAG", "Falha na solicitação: ${t.message}", t)
//            }
//        })
    }


    override fun onPause(){
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onResume() {
        super.onResume()
         heartRateValueTextView.text = Random.nextInt(70,101).toString()
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