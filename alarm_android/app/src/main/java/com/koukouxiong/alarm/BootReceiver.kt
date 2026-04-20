package com.koukouxiong.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)
        val server = prefs.getString("server", null)
        val token = prefs.getString("token", null)

        if (!server.isNullOrEmpty() && !token.isNullOrEmpty()) {
            val serviceIntent = Intent(context, AlarmService::class.java).apply {
                putExtra("server", server)
                putExtra("token", token)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
