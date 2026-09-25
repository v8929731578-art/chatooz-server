package com.chatooz.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chatooz.app.MainActivity
import com.chatooz.app.R
import com.chatooz.app.service.ChatoozBackgroundService
import com.chatooz.app.util.SoundManager

object NotificationHelper {

    const val CALL_CHANNEL_ID = "chatooz_calls_v2"
    const val MESSAGE_CHANNEL_ID = "chatooz_messages_v2"
    const val SERVICE_CHANNEL_ID = "chatooz_service_channel"

    const val CALL_NOTIFICATION_ID = 9001
    const val SERVICE_NOTIFICATION_ID = 9002

    fun init(context: Context) {
        createChannels(context)
    }

    private fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

                // 1. Incoming Calls Channel (High Priority with Ringtone Sound & Vibration)
                val ringtoneUri: Uri? = try {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                        ?: Settings.System.DEFAULT_RINGTONE_URI
                } catch (_: Exception) { null }

                val callAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val callChannel = NotificationChannel(
                    CALL_CHANNEL_ID,
                    "Incoming Voice & Video Calls",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Chatooz incoming call alerts with ringing and heads-up banner"
                    if (ringtoneUri != null) {
                        setSound(ringtoneUri, callAttributes)
                    }
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 1000, 1000)
                    enableLights(true)
                    lightColor = Color.GREEN
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setShowBadge(true)
                }
                nm.createNotificationChannel(callChannel)

                // 2. Messages Channel (High Priority with Notification Chime)
                val notifUri: Uri? = try {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        ?: Settings.System.DEFAULT_NOTIFICATION_URI
                } catch (_: Exception) { null }

                val msgAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val msgChannel = NotificationChannel(
                    MESSAGE_CHANNEL_ID,
                    "New Messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Chatooz new chat messages notifications"
                    if (notifUri != null) {
                        setSound(notifUri, msgAttributes)
                    }
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 150, 100, 150)
                    enableLights(true)
                    lightColor = Color.BLUE
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
                    setShowBadge(true)
                }
                nm.createNotificationChannel(msgChannel)

                // 3. Background Sync Service Channel (Low Priority / Silent)
                val serviceChannel = NotificationChannel(
                    SERVICE_CHANNEL_ID,
                    "Background Connection",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps Chatooz connected for instant messages and calls"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                }
                nm.createNotificationChannel(serviceChannel)
            } catch (e: Exception) {
                android.util.Log.e("NotificationHelper", "createChannels error: ${e.message}")
            }
        }
    }

    /**
     * Shows a Heads-Up Notification for an Incoming Voice or Video Call.
     * Includes Accept (Green) and Decline (Red) action buttons.
     */
    fun showIncomingCallNotification(
        context: Context,
        callId: String,
        callerName: String,
        callerUsername: String,
        isVideo: Boolean
    ) {
        try {
            // Content & Fullscreen Intent (opens MainActivity)
            val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("action", "INCOMING_CALL")
                putExtra("callId", callId)
                putExtra("callerName", callerName)
                putExtra("callerUsername", callerUsername)
                putExtra("isVideo", isVideo)
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                CALL_NOTIFICATION_ID,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Accept Call Action Intent
            val acceptIntent = Intent(context, ChatoozBackgroundService::class.java).apply {
                action = ChatoozBackgroundService.ACTION_ACCEPT_CALL
                putExtra("callId", callId)
            }
            val acceptPendingIntent = PendingIntent.getService(
                context,
                101,
                acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Decline Call Action Intent
            val declineIntent = Intent(context, ChatoozBackgroundService::class.java).apply {
                action = ChatoozBackgroundService.ACTION_DECLINE_CALL
                putExtra("callId", callId)
            }
            val declinePendingIntent = PendingIntent.getService(
                context,
                102,
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val callTypeStr = if (isVideo) "Incoming Video Call 📹" else "Incoming Voice Call 📞"

            val notif = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(callTypeStr)
                .setContentText("$callerName (@$callerUsername) is calling you…")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .setColor(Color.parseColor("#6366F1"))
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Decline ❌",
                    declinePendingIntent
                )
                .addAction(
                    android.R.drawable.ic_menu_call,
                    "Accept ✅",
                    acceptPendingIntent
                )
                .build()

            NotificationManagerCompat.from(context).notify(CALL_NOTIFICATION_ID, notif)
            SoundManager.startIncomingCallRingtone(context)
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Failed to show call notification: ${e.message}")
        }
    }

    /**
     * Cancels the active incoming call notification and stops ringing.
     */
    fun dismissCallNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(CALL_NOTIFICATION_ID)
        } catch (_: Exception) {}
        SoundManager.stopIncomingCallRingtone()
    }

    /**
     * Shows a Heads-Up Notification when a new Message arrives.
     */
    fun showMessageNotification(
        context: Context,
        senderName: String,
        senderUsername: String,
        messageText: String,
        chatId: String,
        isGroup: Boolean = false
    ) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("action", "OPEN_CHAT")
                putExtra("chatId", chatId)
                putExtra("isGroup", isGroup)
            }
            val notifId = chatId.hashCode()
            val pendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (isGroup) "👥 $senderName" else senderName
            val snippet = if (messageText.isBlank()) "Sent an attachment" else messageText

            val notif = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(snippet)
                .setSubText("@$senderUsername")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setColor(Color.parseColor("#6366F1"))
                .build()

            NotificationManagerCompat.from(context).notify(notifId, notif)
            SoundManager.playMessageTone(context)
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Failed to show message notification: ${e.message}")
        }
    }
}
