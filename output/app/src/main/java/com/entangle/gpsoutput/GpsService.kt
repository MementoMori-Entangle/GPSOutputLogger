package com.entangle.gpsoutput

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class GpsService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var intervalMillis: Long = AppConstants.DEFAULT_INTERVAL_MILLIS
    private var sendType: String = AppConstants.DEFAULT_SEND_TYPE // "http", "tcp", "udp"
    private var serverAddress: String = AppConstants.DEFAULT_IP // IP
    private var serverPort: Int = AppConstants.DEFAULT_PORT // HTTP: 5000, TCP/UDP: 任意
    private var gpsReady: Boolean = false
    private var scheme: String = AppConstants.DEFAULT_SCHEME // スキーム保持
    private var errorCount: Int = 0
    private val maxErrorCount: Int = AppConstants.MAX_ERROR_COUNT // デフォルト最大回数
    private var tcpSocket: java.net.Socket? = null
    private var tcpWriter: java.io.OutputStream? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        android.util.Log.d("GpsService", "onCreate called")
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("GpsService", "onStartCommand called")
        try {
            intervalMillis = intent?.getLongExtra("intervalMillis", AppConstants.DEFAULT_INTERVAL_MILLIS) ?: AppConstants.DEFAULT_INTERVAL_MILLIS
            sendType = intent?.getStringExtra("sendType") ?: AppConstants.DEFAULT_SEND_TYPE
            serverAddress = intent?.getStringExtra("serverAddress") ?: AppConstants.DEFAULT_IP
            serverPort = intent?.getIntExtra("serverPort", AppConstants.DEFAULT_PORT) ?: AppConstants.DEFAULT_PORT
            scheme = intent?.getStringExtra("scheme") ?: AppConstants.DEFAULT_SCHEME // スキーム取得
            startForegroundService()
            startLocationUpdates()
        } catch (e: Exception) {
            android.util.Log.e("GpsService", "Exception in onStartCommand: ${e.message}", e)
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        android.util.Log.d("GpsService", "startForegroundService called")
        val channelId = "gps_service_channel"
        val channel = NotificationChannel(channelId, "GPS Service", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("GPS送信中")
            .setContentText("位置情報を送信しています")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        startForeground(1, notification)
    }

    private fun sendGpsOverHttp(latitude: Double, longitude: Double, timestamp: Long) {
        Thread {
            try {
                val url = java.net.URL("$scheme://$serverAddress:$serverPort/gps")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val json = "{\"lat\":$latitude,\"lon\":$longitude,\"time\":$timestamp}"
                conn.outputStream.use { it.write(json.toByteArray()) }
                conn.inputStream.close()
                errorCount = 0 // 成功時はリセット
            } catch (e: Exception) {
                errorCount++
                android.util.Log.e("GpsService", "HTTP送信エラー: ${e.message}")
                sendErrorBroadcast(e.message ?: "HTTP送信エラー")
                if (errorCount >= maxErrorCount) {
                    sendErrorBroadcast("送信失敗が${maxErrorCount}回連続したため送信を停止します")
                    stopSelf()
                }
            }
        }.start()
    }

    private fun sendGpsOverTcp(latitude: Double, longitude: Double, timestamp: Long) {
        Thread {
            try {
                if (tcpSocket == null || tcpSocket!!.isClosed) {
                    tcpSocket = java.net.Socket(serverAddress, serverPort)
                    tcpWriter = tcpSocket!!.getOutputStream()
                }
                val data = "$latitude,$longitude,$timestamp\n"
                tcpWriter?.write(data.toByteArray())
                tcpWriter?.flush()
                errorCount = 0
            } catch (e: Exception) {
                errorCount++
                android.util.Log.e("GpsService", "TCP送信エラー: ${e.message}")
                sendErrorBroadcast(e.message ?: "TCP送信エラー")
                if (errorCount >= maxErrorCount) {
                    sendErrorBroadcast("送信失敗が${maxErrorCount}回連続したため送信を停止します")
                    stopSelf()
                }
            }
        }.start()
    }

    private fun sendGpsOverUdp(latitude: Double, longitude: Double, timestamp: Long) {
        Thread {
            try {
                val socket = java.net.DatagramSocket()
                val data = "$latitude,$longitude,$timestamp"
                val packet = java.net.DatagramPacket(data.toByteArray(), data.length, java.net.InetAddress.getByName(serverAddress), serverPort)
                socket.send(packet)
                socket.close()
                errorCount = 0
            } catch (e: Exception) {
                errorCount++
                android.util.Log.e("GpsService", "UDP送信エラー: ${e.message}")
                sendErrorBroadcast(e.message ?: "UDP送信エラー")
                if (errorCount >= maxErrorCount) {
                    sendErrorBroadcast("送信失敗が${maxErrorCount}回連続したため送信を停止します")
                    stopSelf()
                }
            }
        }.start()
    }

    private fun sendErrorBroadcast(message: String) {
        val intent = Intent("com.entangle.gpsOutput.ERROR")
        intent.putExtra("error_message", message)
        sendBroadcast(intent)
    }

    private fun startLocationUpdates() {
        android.util.Log.d("GpsService", "startLocationUpdates called")
        gpsReady = false
        val request = LocationRequest.Builder(intervalMillis)
            .setIntervalMillis(intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()
        android.util.Log.d("GpsService", "LocationCallback before")
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                android.util.Log.d("GpsService", "onLocationResult called")
                var sentDummy = false
                for (location in result.locations) {
                    if (!gpsReady && location.accuracy < 50) {
                        gpsReady = true
                        android.util.Log.d("GpsService", "GPS ready: ${location.latitude},${location.longitude}")
                    }
                    val lat = location.latitude
                    val lon = location.longitude
                    val time = location.time
                    // MainActivityへ位置情報をBroadcast
                    val locIntent = Intent("com.entangle.gpsOutput.LOCATION")
                    locIntent.putExtra("latitude", lat)
                    locIntent.putExtra("longitude", lon)
                    sendBroadcast(locIntent)
                    if (gpsReady) {
                        when (sendType) {
                            "http" -> sendGpsOverHttp(lat, lon, time)
                            "tcp" -> sendGpsOverTcp(lat, lon, time)
                            "udp" -> sendGpsOverUdp(lat, lon, time)
                        }
                    } else if (!sentDummy) {
                        // GPS未取得時はダミーデータ送信
                        when (sendType) {
                            "http" -> sendGpsOverHttp(0.0, 0.0, 0)
                            "tcp" -> sendGpsOverTcp(0.0, 0.0, 0)
                            "udp" -> sendGpsOverUdp(0.0, 0.0, 0)
                        }
                        sentDummy = true
                    }
                }
            }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.d("GpsService", "ACCESS_FINE_LOCATION not granted, stopping service")
            stopSelf()
            return
        }
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    override fun onDestroy() {
        android.util.Log.d("GpsService", "onDestroy called")
        super.onDestroy()
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        // TCP接続をクリーンアップ
        try {
            tcpWriter?.close()
            tcpSocket?.close()
        } catch (_: Exception) {}
    }
}
