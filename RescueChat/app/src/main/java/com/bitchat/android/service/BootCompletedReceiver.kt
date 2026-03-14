package com.bitchat.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bitchat.android.sos.SosDetectionService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Ensure preferences are initialized on cold boot before reading values
        try { MeshServicePreferences.init(context.applicationContext) } catch (_: Exception) { }

        // Keep fall/crash monitoring active after reboot or app update without opening UI.
        runCatching { SosDetectionService.start(context.applicationContext) }

        if (MeshServicePreferences.isAutoStartEnabled(true)) {
            MeshForegroundService.start(context.applicationContext)
        }
    }
}
