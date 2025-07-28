package com.entangle.gpsoutput

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import com.entangle.gpsoutput.databinding.ActivityMainBinding
import android.widget.LinearLayout
import org.json.JSONObject
import java.io.InputStream

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var isSending = false

    private lateinit var gpsStatusText: TextView
    private lateinit var gpsLatLngText: TextView
    private var currentLocation: Location? = null

    private val prefsName = "gps_output_settings"
    private val keyIp = "ip"
    private val keyPort = "port"
    private val keyTimeout = "timeout"
    private val keySendType = "sendType"

    private lateinit var errorReceiver: android.content.BroadcastReceiver
    private lateinit var locationReceiver: android.content.BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.d("MainActivity", "onCreate called")
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // GPS情報表示用TextViewを動的に追加
        gpsStatusText = TextView(this).apply {
            text = ""
            textSize = 16f
        }
        gpsLatLngText = TextView(this).apply {
            text = ""
            textSize = 16f
        }
        // rootLayoutを画面の一番下に追加
        val rootLayout = findViewById<LinearLayout>(R.id.root_layout)
        rootLayout.addView(gpsStatusText)
        rootLayout.addView(gpsLatLngText)

        checkAndRequestPermissions()

        // Wi-Fi送信設定UI
        val ipEditText = findViewById<EditText>(R.id.ip_edittext)
        val portEditText = findViewById<EditText>(R.id.port_edittext)
        val timeoutEditText = findViewById<EditText>(R.id.timeout_edittext)
        val sendTypeSpinner = findViewById<Spinner>(R.id.send_type_spinner)
        val sendTypeAdapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf("http", "https", "tcp", "udp")
        )
        sendTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sendTypeSpinner.adapter = sendTypeAdapter
        sendTypeSpinner.setSelection(0)

        // 設定値の読み込み（保存値があればそちらを優先）
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        // config.jsonの値を初回のみSharedPreferencesへ保存
        if (!prefs.contains(keyIp) || !prefs.contains(keyPort) || !prefs.contains(keyTimeout) || !prefs.contains(keySendType)) {
            val config = readConfigJson()
            if (config != null) {
                android.util.Log.d("MainActivity", "Config loaded: $config")
                prefs.edit(true) {
                    putString(keyIp, config.optString("ip", AppConstants.DEFAULT_IP))
                    putInt(keyPort, config.optInt("port", AppConstants.DEFAULT_PORT))
                    putLong(keyTimeout, config.optLong("timeout", AppConstants.DEFAULT_TIMEOUT))
                    putString(keySendType, config.optString("sendType", AppConstants.DEFAULT_SEND_TYPE))
                }
            }
        }
        // SharedPreferencesから値を取得してUIに反映
        ipEditText.setText(prefs.getString(keyIp, AppConstants.DEFAULT_IP))
        portEditText.setText(prefs.getInt(keyPort, AppConstants.DEFAULT_PORT).toString())
        timeoutEditText.setText(prefs.getLong(keyTimeout, AppConstants.DEFAULT_TIMEOUT).toString())
        val prefsSendType = prefs.getString(keySendType, AppConstants.DEFAULT_SEND_TYPE)
        sendTypeSpinner.setSelection(listOf("http", "https", "tcp", "udp").indexOf(prefsSendType))

        val startButton = findViewById<Button>(R.id.start_button)
        val stopButton = findViewById<Button>(R.id.stop_button)
        startButton.isEnabled = true
        stopButton.isEnabled = false

        // 設定ファイルからスライダーの値を取得
        val minInterval = prefs.getInt("minInterval", AppConstants.DEFAULT_MIN_INTERVAL)
        val maxInterval = prefs.getInt("maxInterval", AppConstants.DEFAULT_MAX_INTERVAL)
        val defaultInterval = prefs.getInt("defaultInterval", AppConstants.DEFAULT_INTERVAL)
        val intervalSeekBar = findViewById<SeekBar>(R.id.interval_seekbar)
        intervalSeekBar.min = minInterval
        intervalSeekBar.max = maxInterval
        intervalSeekBar.progress = defaultInterval

        val intervalValueView = findViewById<TextView>(R.id.interval_value)
        intervalValueView.text = getString(R.string.send_interval, defaultInterval)

        intervalSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                intervalValueView.text = getString(R.string.send_interval, progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        startButton.setOnClickListener {
            val ip = ipEditText.text.toString().ifEmpty { AppConstants.DEFAULT_IP }
            val port = portEditText.text.toString().toIntOrNull() ?: AppConstants.DEFAULT_PORT
            val timeout = timeoutEditText.text.toString().toLongOrNull() ?: AppConstants.DEFAULT_TIMEOUT
            val sendType = sendTypeSpinner.selectedItem.toString()
            var scheme = AppConstants.DEFAULT_SCHEME
            var type = sendType
            val interval = intervalSeekBar.progress.toLong()
            if (sendType == "http" || sendType == "https") {
                scheme = sendType
                type = "http"
            }
            val intent = Intent(this, GpsService::class.java)
            intent.putExtra("serverAddress", ip)
            intent.putExtra("serverPort", port)
            intent.putExtra("sendType", type)
            intent.putExtra("intervalMillis", interval * 1000)
            intent.putExtra("timeoutMillis", timeout)
            intent.putExtra("scheme", scheme)
            // 設定値の保存（即時反映）
            prefs.edit(true) {
                putString(keyIp, ip)
                putInt(keyPort, port)
                putLong(keyTimeout, timeout)
                putString(keySendType, sendType)
            }
            startForegroundService(intent)
            Toast.makeText(this, "GPS送信サービス開始", Toast.LENGTH_SHORT).show()
            isSending = true
            setSendingStatus(true)
            startButton.isEnabled = false
            stopButton.isEnabled = true
        }
        stopButton.setOnClickListener {
            stopService(Intent(this, GpsService::class.java))
            Toast.makeText(this, "GPS送信サービス停止", Toast.LENGTH_SHORT).show()
            isSending = false
            setSendingStatus(false)
            startButton.isEnabled = true
            stopButton.isEnabled = false
        }

        errorReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.entangle.gpsOutput.ERROR") {
                    val errorMsg = intent.getStringExtra("error_message") ?: getString(R.string.send_error)
                    setSendingStatus(false)
                    findViewById<Button>(R.id.start_button).isEnabled = true
                    Toast.makeText(this@MainActivity, getString(R.string.send_error_message, errorMsg), Toast.LENGTH_LONG).show()
                }
            }
        }
        val filter = android.content.IntentFilter("com.entangle.gpsOutput.ERROR")
        registerReceiver(errorReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        // 位置情報受信用BroadcastReceiverの登録
        locationReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.entangle.gpsOutput.LOCATION") {
                    val lat = intent.getDoubleExtra("latitude", 0.0)
                    val lng = intent.getDoubleExtra("longitude", 0.0)
                    gpsStatusText.text = getString(R.string.gps_acquired)
                    gpsLatLngText.text = getString(R.string.lat_lng, lat, lng)
                    currentLocation = Location("service").apply {
                        latitude = lat
                        longitude = lng
                    }
                }
            }
        }
        val locFilter = android.content.IntentFilter("com.entangle.gpsOutput.LOCATION")
        registerReceiver(locationReceiver, locFilter, Context.RECEIVER_NOT_EXPORTED)

        // テキストボックス変更時に即保存
        ipEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val ip = ipEditText.text.toString().ifEmpty { AppConstants.DEFAULT_IP }
                prefs.edit(true) { putString(keyIp, ip) }
            }
        }
        portEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val port = portEditText.text.toString().toIntOrNull() ?: AppConstants.DEFAULT_PORT
                android.util.Log.d("MainActivity", "Port changed: $port")
                prefs.edit(true) { putInt(keyPort, port) }
            }
        }
        timeoutEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val timeout = timeoutEditText.text.toString().toLongOrNull() ?: AppConstants.DEFAULT_TIMEOUT
                prefs.edit(true) { putLong(keyTimeout, timeout) }
            }
        }
        sendTypeSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val sendType = parent.getItemAtPosition(position).toString()
                prefs.edit(true) { putString(keySendType, sendType) }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        })
    }

    // 権限リクエストの結果を受け取る
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun checkAndRequestPermissions() {
        android.util.Log.d("MainActivity", "checkAndRequestPermissions called")
        val permissionsToRequest = mutableListOf<String>()
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        val permissionsNotGranted = permissionsToRequest.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNotGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNotGranted.toTypedArray(), 100)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        android.util.Log.d("MainActivity", "onCreateOptionsMenu called")
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        android.util.Log.d("MainActivity", "onOptionsItemSelected called")
        return when (item.itemId) {
            R.id.action_background -> {
                moveTaskToBack(true)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setSendingStatus(isSending: Boolean) {
        android.util.Log.d("MainActivity", "setSendingStatus called: isSending=$isSending")
        gpsStatusText.text = if (isSending) "送信中" else ""
        if (isSending && currentLocation != null) {
            gpsLatLngText.text = getString(R.string.lat_lng, currentLocation!!.latitude, currentLocation!!.longitude)
        } else if (!isSending) {
            gpsLatLngText.text = getString(R.string.lat_lng_not_acquired)
        }
    }

    private fun readConfigJson(): JSONObject? {
        return try {
            val inputStream: InputStream = assets.open("config.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val json = String(buffer, Charsets.UTF_8)
            JSONObject(json)
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(errorReceiver)
        unregisterReceiver(locationReceiver)
        // アプリ終了時にGPS送信サービスを停止
        val stopIntent = Intent(this, GpsService::class.java)
        stopService(stopIntent)
    }
}
