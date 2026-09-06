package com.neonorbit.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.neonorbit.app.R
import com.neonorbit.app.game.GameEvent

class FeedbackPlayer(private val context: Context) : AutoCloseable {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(5)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val loaded = mutableSetOf<Int>()
    private val tap: Int
    private val collect: Int
    private val surge: Int
    private val crash: Int

    init {
        // Register the callback before scheduling loads. Very small local WAVs can
        // otherwise finish before the listener exists on fast devices.
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded += sampleId
        }
        tap = soundPool.load(context, R.raw.sfx_switch, 1)
        collect = soundPool.load(context, R.raw.sfx_collect, 1)
        surge = soundPool.load(context, R.raw.sfx_surge, 1)
        crash = soundPool.load(context, R.raw.sfx_crash, 1)
    }

    fun onEvent(event: GameEvent, soundEnabled: Boolean, hapticsEnabled: Boolean) {
        when (event) {
            GameEvent.LaneSwitched -> {
                play(tap, soundEnabled, 0.38f, 1.06f)
                vibrate(hapticsEnabled, 10L, 55)
            }
            GameEvent.ShardCollected -> {
                play(collect, soundEnabled, 0.55f, 1.0f)
                vibrate(hapticsEnabled, 14L, 80)
            }
            GameEvent.SurgeStarted -> {
                play(surge, soundEnabled, 0.72f, 1.0f)
                vibrate(hapticsEnabled, 42L, 125)
            }
            GameEvent.HazardDestroyed -> {
                play(surge, soundEnabled, 0.5f, 1.18f)
                vibrate(hapticsEnabled, 24L, 105)
            }
            GameEvent.Crashed -> {
                play(crash, soundEnabled, 0.8f, 0.94f)
                vibrate(hapticsEnabled, 70L, 180)
            }
            GameEvent.NewBest -> Unit
        }
    }

    private fun play(id: Int, enabled: Boolean, volume: Float, rate: Float) {
        if (enabled && id in loaded) {
            soundPool.play(id, volume, volume, 1, 0, rate)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(enabled: Boolean, duration: Long, amplitude: Int) {
        if (!enabled) return
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude.coerceIn(1, 255)))
    }

    override fun close() {
        soundPool.release()
    }
}
