package com.koukouxiong.alarm

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Vibrator
import android.os.VibrationEffect
import androidx.core.app.NotificationCompat
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AlarmService : Service() {

    companion object {
        var isConnected: Boolean = false
            private set
        private var instance: AlarmService? = null
        fun stopAlarm() {
            instance?.stopAlarmInternal()
        }
    }

    private var webSocket: WebSocketClient? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentAlarmId: String? = null
    private var serverAddress: String? = null
    private var authToken: String? = null
    private var isAlarmActive = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "KoukouxiongAlarm::WakeLock"
        )

        startForeground(1, createNotification("服务运行中"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_ALARM") {
            stopAlarmInternal()
            return START_STICKY
        }

        val server = intent?.getStringExtra("server")
        val token = intent?.getStringExtra("token")
        if (!server.isNullOrEmpty() && server != serverAddress) {
            serverAddress = server
            authToken = token
            connectToServer(server, token)
        }
        return START_STICKY
    }

    private fun connectToServer(server: String, token: String?) {
        webSocket?.close()

        try {
            val wsUrl = when {
                server.startsWith("ws://") || server.startsWith("wss://") -> {
                    if (!token.isNullOrEmpty()) {
                        val separator = if (server.contains("?")) "&" else "?"
                        "$server${separator}token=$token"
                    } else {
                        server
                    }
                }
                server.contains(":") -> {
                    if (!token.isNullOrEmpty()) {
                        "ws://$server?token=$token"
                    } else {
                        "ws://$server"
                    }
                }
                else -> {
                    if (!token.isNullOrEmpty()) {
                        "wss://$server?token=$token"
                    } else {
                        "wss://$server"
                    }
                }
            }

            val uri = URI.create(wsUrl)
            webSocket = object : WebSocketClient(uri) {
                override fun onOpen(handshake: ServerHandshake?) {
                    isConnected = true
                    updateNotification("已连接服务器")
                }

                override fun onMessage(message: String?) {
                    message?.let { handleMessage(it) }
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    isConnected = false
                    updateNotification("连接断开，重连中...")
                    android.os.Handler(mainLooper).postDelayed({
                        serverAddress?.let { srv ->
                            connectToServer(srv, authToken)
                        }
                    }, 5000)
                }

                override fun onError(ex: Exception?) {
                    isConnected = false
                    updateNotification("连接错误: ${ex?.message}")
                }
            }
            webSocket?.connect()
        } catch (e: Exception) {
            e.printStackTrace()
            updateNotification("连接失败: ${e.message}")
        }
    }

    private fun verifySignature(data: JSONObject): Boolean {
        val token = authToken ?: return false
        val sig = data.optString("signature", null) ?: return false
        val payload = "${data.optString("type", "")}|${data.optString("alarm_id", "")}|${data.optString("message", "")}|${data.optString("timestamp", "")}"
        return try {
            val keySpec = SecretKeySpec(token.toByteArray(), "HmacSHA256")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(keySpec)
            val hash = mac.doFinal(payload.toByteArray()).joinToString("") { "%02x".format(it) }
            hash == sig
        } catch (e: Exception) {
            false
        }
    }

    private fun handleMessage(message: String) {
        try {
            val json = JSONObject(message)
            if (!verifySignature(json)) {
                return
            }
            when (json.getString("type")) {
                "alarm" -> {
                    currentAlarmId = json.getString("alarm_id")
                    val msg = json.optString("message", "时间到了")
                    startAlarm(msg)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startAlarm(message: String) {
        if (!isAlarmActive) {
            wakeLock?.acquire(10 * 60 * 1000L)
            isAlarmActive = true
        }

        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (vibrator?.hasVibrator() == true) {
            val pattern = longArrayOf(0, 1000, 500, 1000, 500)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val intent = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("message", message)
            putExtra("alarm_id", currentAlarmId)
        }
        startActivity(intent)
    }

    private fun stopAlarmInternal() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null

        try {
            vibrator?.cancel()
        } catch (e: Exception) {}

        try {
            wakeLock?.release()
        } catch (e: Exception) {}

        currentAlarmId?.let { id ->
            try {
                webSocket?.send(JSONObject().apply {
                    put("type", "alarm_stopped")
                    put("alarm_id", id)
                }.toString())
            } catch (e: Exception) {}
        }

        currentAlarmId = null
        isAlarmActive = false
        updateNotification("服务运行中")
    }

    private fun createNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "alarm_service",
                "提醒服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, "alarm_service")
            .setContentTitle("扣扣熊提醒")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(1, createNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        webSocket?.close()
        stopAlarmInternal()
    }
}
