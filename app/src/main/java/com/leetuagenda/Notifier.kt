package com.leetuagenda

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings

/** Lanza la pantalla de la agenda: a pantalla completa con el móvil bloqueado,
 *  o directamente encima si se ha dado el permiso "Mostrar sobre otras apps". */
object Notifier {
    private const val CHANNEL = "agenda_v1"
    const val NOTIF_ID = 42

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "Revisión de la agenda", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Abre la agenda del día a pantalla completa"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }

    fun canFullScreen(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    fun canNotify(ctx: Context): Boolean =
        ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    fun canOverlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun agendaIntent(ctx: Context): Intent =
        Intent(ctx, AgendaActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

    /** Muestra el aviso y abre la agenda. */
    fun show(ctx: Context, pending: Int) {
        ensureChannel(ctx)
        val pi = PendingIntent.getActivity(
            ctx, 1, agendaIntent(ctx),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (pending > 0) "Tienes $pending entradas por leer. Tócalas una a una."
                   else "Revisa tu agenda de hoy."
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("📅 Lee tu agenda de hoy")
            .setContentText(text)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setOngoing(true)       // no se puede descartar deslizando
            .setAutoCancel(false)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
        } catch (e: SecurityException) { /* sin permiso de notificaciones */ }

        // Con el permiso de superposición, Android deja abrir la pantalla aunque estés usando el móvil
        if (canOverlay(ctx)) {
            try { ctx.startActivity(agendaIntent(ctx)) } catch (e: Exception) { }
        }
    }

    fun clear(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }
}
