package com.leetuagenda

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId

/** Programa el aviso diario y los recordatorios insistentes. */
object Scheduler {
    const val ACTION_DAILY = "com.leetuagenda.DAILY"
    const val ACTION_NAG = "com.leetuagenda.NAG"
    private const val RC_DAILY = 100
    const val ACTION_TEST = "com.leetuagenda.TEST"
    private const val RC_NAG = 200
    private const val RC_TEST = 300

    private fun alarmManager(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager(ctx).canScheduleExactAlarms()

    private fun pending(ctx: Context, action: String, rc: Int): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            ctx, rc, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setAt(ctx: Context, time: Long, pi: PendingIntent) {
        val am = alarmManager(ctx)
        if (canExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        } else {
            // Sin permiso de alarma exacta: puede retrasarse unos minutos
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        }
    }

    /** Próxima hora diaria configurada (hoy si aún no ha pasado, si no mañana). */
    fun nextDailyTime(ctx: Context): Long {
        val p = Prefs(ctx)
        val now = LocalDateTime.now()
        var t = now.toLocalDate().atTime(p.hour, p.minute)
        if (!t.isAfter(now)) t = t.plusDays(1)
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun scheduleDaily(ctx: Context) {
        val p = Prefs(ctx)
        val pi = pending(ctx, ACTION_DAILY, RC_DAILY)
        if (!p.enabled) {
            alarmManager(ctx).cancel(pi)
            return
        }
        setAt(ctx, nextDailyTime(ctx), pi)
    }

    fun scheduleNag(ctx: Context) {
        val p = Prefs(ctx)
        if (p.nagMinutes <= 0 || p.isDoneToday()) return
        val time = System.currentTimeMillis() + p.nagMinutes * 60_000L
        // No insistir de madrugada: si se pasa de la hora límite, se deja para mañana
        val limit = LocalDateTime.now().toLocalDate().atTime(p.quietHour, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (time >= limit) return
        setAt(ctx, time, pending(ctx, ACTION_NAG, RC_NAG))
    }

    /** Prueba: lanza el aviso dentro de unos segundos (para comprobarlo con la pantalla apagada). */
    fun scheduleTest(ctx: Context, seconds: Int) {
        setAt(ctx, System.currentTimeMillis() + seconds * 1000L, pending(ctx, ACTION_TEST, RC_TEST))
    }

    fun cancelNag(ctx: Context) {
        alarmManager(ctx).cancel(pending(ctx, ACTION_NAG, RC_NAG))
    }
}
