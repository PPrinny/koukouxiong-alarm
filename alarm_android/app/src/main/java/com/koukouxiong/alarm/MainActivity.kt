package com.koukouxiong.alarm

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var serverInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var connectBtn: Button
    private lateinit var testBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        serverInput = findViewById(R.id.serverInput)
        tokenInput = findViewById(R.id.tokenInput)
        connectBtn = findViewById(R.id.connectBtn)
        testBtn = findViewById(R.id.testBtn)

        val prefs = getSharedPreferences("config", MODE_PRIVATE)
        serverInput.setText(prefs.getString("server", ""))
        tokenInput.setText(prefs.getString("token", ""))

        connectBtn.setOnClickListener {
            val server = serverInput.text.toString().trim()
            val token = tokenInput.text.toString().trim()
            if (server.isEmpty()) {
                Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (token.isEmpty()) {
                Toast.makeText(this, "请输入连接密钥", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString("server", server)
                .putString("token", token)
                .apply()

            val intent = Intent(this, AlarmService::class.java).apply {
                putExtra("server", server)
                putExtra("token", token)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            requestBatteryOptimization()

            Toast.makeText(this, "服务已启动", Toast.LENGTH_SHORT).show()
        }

        testBtn.setOnClickListener {
            if (AlarmService.isConnected) {
                Toast.makeText(this, "已连接服务器，可以测试", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "未连接服务器", Toast.LENGTH_SHORT).show()
            }
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        statusText.text = if (AlarmService.isConnected) {
            "状态: 已连接"
        } else {
            "状态: 未连接"
        }
    }

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
