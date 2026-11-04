package com.leetuagenda

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Al reiniciar el móvil (o cambiar la hora) las alarmas se borran: las volvemos a programar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Scheduler.scheduleDaily(ctx)
        val p = Prefs(ctx)
        // Si ya pasó la hora de hoy y no se ha revisado, volver a insistir
        val pastTodaysTime = java.time.LocalTime.now().isAfter(java.time.LocalTime.of(p.hour, p.minute))
        if (p.enabled && !p.isDoneToday() && pastTodaysTime) Scheduler.scheduleNag(ctx)
    }
}
