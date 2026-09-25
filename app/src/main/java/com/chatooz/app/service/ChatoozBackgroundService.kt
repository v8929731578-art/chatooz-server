package com.chatooz.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chatooz.app.MainActivity
import com.chatooz.app.R
import com.chatooz.app.call.CallManager
import com.chatooz.app.data.ChatoozStorage
import com.chatooz.app.notification.NotificationHelper
import kotlinx.coroutines.*

class ChatoozBackgroundService : Service() {

    companion object {
        private const val TAG = "ChatoozBackgroundService"
        const val ACTION_START_SERVICE = "com.chatooz.app.action.START_SERVICE"
        const val ACTION_STOP_SERVICE  = "com.chatooz.app.action.STOP_SERVICE"
        const val ACTION_ACCEPT_CALL   = "com.chatooz.app.action.ACCEPT_CALL"
        const val ACTION_DECLINE_CALL  = "com.chatooz.app.action.DECLINE_CALL"

        var isRunning = false
            private set

        fun start(context: Context) {
            try {
                val intent = Intent(context, ChatoozBackgroundService::class.java).apply {
                    action = ACTION_START_SERVICE
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ChatoozBackgroundService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, ChatoozBackgroundService::class.java).apply {
                    action = ACTION_STOP_SERVICE
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var storage: ChatoozStorage

    private var messageSyncJob: Job? = null
    private val notifiedMessageIds = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.init(this)
        storage = ChatoozStorage(this)
        CallManager.init(this)
        Log.i(TAG, "ChatoozBackgroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVICE

        when (action) {
            ACTION_ACCEPT_CALL -> {
                Log.i(TAG, "Handling ACTION_ACCEPT_CALL from notification")
                NotificationHelper.dismissCallNotification(this)
                CallManager.acceptCall(serviceScope)
                // Bring MainActivity to foreground
                val openIntent = Intent(this, MainActivity::class.java).apply {
                    this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    putExtra("action", "ACTIVE_CALL")
                }
                startActivity(openIntent)
                return START_STICKY
            }

            ACTION_DECLINE_CALL -> {
                Log.i(TAG, "Handling ACTION_DECLINE_CALL from notification")
                NotificationHelper.dismissCallNotification(this)
                CallManager.declineCall(serviceScope)
                return START_STICKY
            }

            ACTION_STOP_SERVICE -> {
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START_SERVICE -> {
                startForegroundNotification()
                startBackgroundTasks()
            }
        }

        return START_STICKY
    }

    private fun startForegroundNotification() {
        try {
            val openIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif: Notification = NotificationCompat.Builder(this, NotificationHelper.SERVICE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Chatooz")
                .setContentText("Connected for instant calls & messages")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentIntent(pi)
                .setOngoing(true)
                .build()

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                    NotificationHelper.SERVICE_NOTIFICATION_ID,
                    notif,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notif)
            }
            isRunning = true
        } catch (e: Exception) {
            Log.e(TAG, "startForegroundNotification error: ${e.message}")
        }
    }

    private var sessionWatcherJob: Job? = null

    private fun startBackgroundTasks() {
        sessionWatcherJob?.cancel()
        sessionWatcherJob = serviceScope.launch {
            while (isActive) {
                val currentUserId = storage.getSessionUserId()
                if (!currentUserId.isNullOrBlank()) {
                    Log.d(TAG, "Active user session found: $currentUserId. Starting pollers.")
                    // 1. Start continuous Incoming Call Listener
                    CallManager.startBackgroundIncomingCallPoll(serviceScope, currentUserId)
                    // 2. Start Message Sync & Notification Poller
                    startMessageSyncPoller(currentUserId)
                    break
                }
                delay(2000L)
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        try {
            val restartServiceIntent = Intent(applicationContext, ChatoozBackgroundService::class.java).apply {
                setPackage(packageName)
                action = ACTION_START_SERVICE
            }
            val restartPendingIntent = PendingIntent.getService(
                applicationContext, 101, restartServiceIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
            alarmManager?.set(
                android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + 1000,
                restartPendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "onTaskRemoved restart error: ${e.message}")
        }
    }

    private fun startMessageSyncPoller(userId: String) {
        messageSyncJob?.cancel()
        messageSyncJob = serviceScope.launch {
            // Populate initial existing message IDs so we don't notify on old messages
            val existing = storage.getAllMessages()
            existing.forEach { notifiedMessageIds.add(it.id) }

            while (isActive) {
                try {
                    delay(3000L)
                    val beforeSyncIds = storage.getAllMessages().map { it.id }.toSet()
                    
                    // Sync with cloud
                    val synced = storage.syncWithCloud()
                    if (synced) {
                        val allMsgs = storage.getAllMessages()
                        val now = System.currentTimeMillis()

                        for (msg in allMsgs) {
                            val isGroup = msg.chatId.startsWith("group_")
                            val isDirectChat = msg.chatId.contains(userId)
                            val isRelevant = (isGroup || isDirectChat) && msg.senderId != userId && !msg.isDeletedForEveryone

                            if (isRelevant &&
                                msg.id !in beforeSyncIds &&
                                msg.id !in notifiedMessageIds &&
                                (now - msg.timestamp) < 60_000L
                            ) {
                                notifiedMessageIds.add(msg.id)

                                val senderUser = storage.getUserById(msg.senderId)
                                val senderName = senderUser?.name ?: "Chatooz User"
                                val senderUsername = senderUser?.username ?: ""

                                NotificationHelper.showMessageNotification(
                                    context = this@ChatoozBackgroundService,
                                    senderName = senderName,
                                    senderUsername = senderUsername,
                                    messageText = msg.text,
                                    chatId = msg.chatId,
                                    isGroup = isGroup
                                )
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Message sync loop error: ${e.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        messageSyncJob?.cancel()
        serviceJob.cancel()
        Log.i(TAG, "ChatoozBackgroundService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
