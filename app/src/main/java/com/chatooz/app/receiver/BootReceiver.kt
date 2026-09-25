package com.chatooz.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.chatooz.app.data.ChatoozStorage
import com.chatooz.app.service.ChatoozBackgroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i("BootReceiver", "Device booted or app updated, starting ChatoozBackgroundService")
            val storage = ChatoozStorage(context)
            if (!storage.getSessionUserId().isNullOrBlank()) {
                ChatoozBackgroundService.start(context)
            }
        }
    }
}
