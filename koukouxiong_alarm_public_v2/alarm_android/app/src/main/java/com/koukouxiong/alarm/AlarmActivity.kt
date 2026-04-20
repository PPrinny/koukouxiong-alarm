package com.koukouxiong.alarm

import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 响铃界面 - 全屏显示，用户点击停止
 */
class AlarmActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        
        setContentView(R.layout.activity_alarm)
        
        val messageText = findViewById<TextView>(R.id.messageText)
        val stopBtn = findViewById<Button>(R.id.stopBtn)
        
        val message = intent.getStringExtra("message") ?: "时间到了"
        messageText.text = message
        
        stopBtn.setOnClickListener {
            AlarmService.stopAlarm()
            finish()
        }
    }
    
    override fun onBackPressed() {
        // 禁止返回键，必须点停止
    }
}
