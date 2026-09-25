package com.chatooz.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log

object SoundManager {

    private const val TAG = "SoundManager"
    private var callMediaPlayer: MediaPlayer? = null
    private var callRingtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    /**
     * Plays a short notification chime/tone and vibrates when a new incoming message arrives.
     */
    fun playMessageTone(context: Context) {
        try {
            val notifUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: Settings.System.DEFAULT_NOTIFICATION_URI

            if (notifUri != null) {
                val r = RingtoneManager.getRingtone(context.applicationContext, notifUri)
                if (r != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        r.audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    }
                    r.play()
                } else {
                    playToneGeneratorBeep()
                }
            } else {
                playToneGeneratorBeep()
            }
        } catch (e: Exception) {
            Log.w(TAG, "playMessageTone error: ${e.message}")
            playToneGeneratorBeep()
        }

        // Short message vibration (150ms)
        try {
            val vib = getVibrator(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib?.vibrate(VibrationEffect.createOneShot(150L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib?.vibrate(150L)
            }
        } catch (_: Exception) {}
    }

    private fun playToneGeneratorBeep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
        } catch (_: Exception) {}
    }

    /**
     * Starts continuous ringing and rhythmic vibration for incoming voice or video calls.
     */
    fun startIncomingCallRingtone(context: Context) {
        stopIncomingCallRingtone()

        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        try {
            audioManager?.mode = AudioManager.MODE_RINGTONE
        } catch (_: Exception) {}

        var played = false

        // 1. Try RingtoneManager
        try {
            val ringtoneUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: Settings.System.DEFAULT_RINGTONE_URI
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALL)

            val rt = RingtoneManager.getRingtone(appContext, ringtoneUri)
            if (rt != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    rt.isLooping = true
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    rt.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setLegacyStreamType(AudioManager.STREAM_RING)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    rt.streamType = AudioManager.STREAM_RING
                }
                rt.play()
                callRingtone = rt
                played = true
                Log.i(TAG, "Incoming call Ringtone started successfully")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Ringtone play failed: ${e.message}")
        }

        // 2. Fallback to MediaPlayer if Ringtone failed
        if (!played) {
            try {
                val ringtoneUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: Settings.System.DEFAULT_RINGTONE_URI

                val player = MediaPlayer().apply {
                    setDataSource(appContext, ringtoneUri)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setLegacyStreamType(AudioManager.STREAM_RING)
                                .build()
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        setAudioStreamType(AudioManager.STREAM_RING)
                    }
                    isLooping = true
                    prepare()
                    start()
                }
                callMediaPlayer = player
                played = true
                Log.i(TAG, "Incoming call MediaPlayer started successfully")
            } catch (ex: Exception) {
                Log.w(TAG, "MediaPlayer fallback failed: ${ex.message}")
            }
        }

        // Looping call vibration (1 sec vibrate, 1 sec pause)
        try {
            vibrator = getVibrator(appContext)
            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 means repeat from index 0
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration start error: ${e.message}")
        }
    }

    /**
     * Stops call ringtone and cancels vibration immediately.
     */
    fun stopIncomingCallRingtone() {
        try {
            callMediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {}
        callMediaPlayer = null

        try {
            callRingtone?.stop()
        } catch (_: Exception) {}
        callRingtone = null

        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
        vibrator = null
    }

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }
}
