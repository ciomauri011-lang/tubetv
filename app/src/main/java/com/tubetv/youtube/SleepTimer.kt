package com.tubetv.youtube

import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Sleep timer para TV: BACK largo cicla 15→30→60→90→120→off.
 * Al vencer: pausa el video y vuelve al home (el apagado total del TV
 * requiere permiso de sistema; esto detiene reproducción y sale de la app).
 */
class SleepTimer(
    private val activity: MainActivity,
    private val onSleep: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var fireAt = 0L
    private var minutes = 0

    private val fire = Runnable {
        minutes = 0
        fireAt = 0L
        Toast.makeText(activity, "Sleep: hora de dormir", Toast.LENGTH_LONG).show()
        onSleep()
    }

    fun cycle() {
        minutes = when (minutes) {
            0 -> 15
            15 -> 30
            30 -> 60
            60 -> 90
            90 -> 120
            else -> 0
        }
        handler.removeCallbacks(fire)
        if (minutes == 0) {
            fireAt = 0L
            Toast.makeText(activity, "Sleep: off", Toast.LENGTH_SHORT).show()
        } else {
            fireAt = System.currentTimeMillis() + minutes * 60_000L
            handler.postDelayed(fire, minutes * 60_000L)
            Toast.makeText(activity, "Sleep en $minutes min (BACK largo cambia)", Toast.LENGTH_LONG).show()
        }
    }

    fun remainingMin(): Int {
        if (fireAt == 0L) return 0
        return ((fireAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0).toInt()
    }
}
