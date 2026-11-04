package com.leetuagenda

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val p = Prefs(ctx)
        when (intent.action) {
            Scheduler.ACTION_DAILY -> {
                Scheduler.scheduleDaily(ctx)   // dejar listo el de mañana
                trigger(ctx, p)
            }
            Scheduler.ACTION_NAG -> {
                if (p.enabled) trigger(ctx, p)
            }
            Scheduler.ACTION_TEST -> {
                val checked = p.checkedToday()
                Notifier.show(ctx, CalendarRepo.today(ctx).count { it.key !in checked })
            }
        }
    }

    private fun trigger(ctx: Context, p: Prefs) {
        if (p.isDoneToday()) return
        val items = CalendarRepo.today(ctx)
        if (items.isEmpty() && p.skipEmpty && CalendarRepo.hasPermission(ctx)) {
            p.markDoneToday()
            return
        }
        val checked = p.checkedToday()
        val pending = items.count { it.key !in checked }
        Notifier.show(ctx, pending)
        Scheduler.scheduleNag(ctx)
    }
}
