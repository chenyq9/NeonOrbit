package com.neonorbit.app.data

import android.content.Context

data class UserSettings(
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val reducedFx: Boolean = false,
)

class PreferencesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun bestScore(): Int = prefs.getInt(KEY_BEST, 0)

    fun saveBestScore(value: Int) {
        if (value > bestScore()) prefs.edit().putInt(KEY_BEST, value).apply()
    }

    fun settings(): UserSettings = UserSettings(
        sound = prefs.getBoolean(KEY_SOUND, true),
        haptics = prefs.getBoolean(KEY_HAPTICS, true),
        reducedFx = prefs.getBoolean(KEY_REDUCED_FX, false),
    )

    fun saveSettings(settings: UserSettings) {
        prefs.edit()
            .putBoolean(KEY_SOUND, settings.sound)
            .putBoolean(KEY_HAPTICS, settings.haptics)
            .putBoolean(KEY_REDUCED_FX, settings.reducedFx)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "neon_orbit_preferences"
        const val KEY_BEST = "best_score"
        const val KEY_SOUND = "sound"
        const val KEY_HAPTICS = "haptics"
        const val KEY_REDUCED_FX = "reduced_fx"
    }
}
