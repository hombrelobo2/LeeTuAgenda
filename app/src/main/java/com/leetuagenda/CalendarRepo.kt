package com.leetuagenda

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class AgendaItem(
    val eventId: Long,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val title: String,
    val location: String,
    val description: String,
    val calendarName: String,
    val color: Int,
) {
    /** Clave única de esta aparición (un evento repetido tiene varias). */
    val key: String get() = "${eventId}_$begin"
}

data class CalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
    val visible: Boolean,
    val synced: Boolean,
    val color: Int,
)

/** Lee los eventos de HOY de los calendarios del móvil (Google, Samsung, Outlook…). */
object CalendarRepo {

    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun calendars(ctx: Context): List<CalendarInfo> {
        if (!hasPermission(ctx)) return emptyList()
        val out = ArrayList<CalendarInfo>()
        val proj = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.SYNC_EVENTS,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, proj, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                out.add(
                    CalendarInfo(
                        id = c.getLong(0),
                        name = c.getString(1) ?: "(sin nombre)",
                        account = c.getString(2) ?: "",
                        visible = c.getInt(3) == 1,
                        synced = c.getInt(4) == 1,
                        color = if (c.isNull(5)) 0xFF3949AB.toInt() else c.getInt(5),
                    )
                )
            }
        }
        return out.sortedWith(compareBy({ it.account }, { it.name }))
    }

    /** Calendarios que se incluyen: los elegidos por el usuario, o por defecto todos los visibles. */
    fun includedIds(ctx: Context): Set<Long> {
        val p = Prefs(ctx)
        val chosen = p.chosenCalendars()
        return chosen ?: calendars(ctx).filter { it.visible }.map { it.id }.toSet()
    }

    private class Raw(
        val eventId: Long, val begin: Long, val end: Long, val allDay: Boolean,
        val title: String, val location: String, val description: String?,
        val calendarName: String, val color: Int, val status: Int, val calendarId: Long,
    )

    private fun queryRaw(ctx: Context, from: Long, to: Long): List<Raw> {
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, from)
        ContentUris.appendId(builder, to)
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.CALENDAR_ID,
        )
        val out = ArrayList<Raw>()
        ctx.contentResolver.query(
            builder.build(), projection, null, null,
            "${CalendarContract.Instances.ALL_DAY} DESC, ${CalendarContract.Instances.BEGIN} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                out.add(
                    Raw(
                        eventId = c.getLong(0),
                        begin = c.getLong(1),
                        end = c.getLong(2),
                        allDay = c.getInt(3) == 1,
                        title = c.getString(4)?.trim().takeUnless { it.isNullOrEmpty() } ?: "(sin título)",
                        location = c.getString(5)?.trim() ?: "",
                        description = c.getString(6),
                        calendarName = c.getString(7) ?: "",
                        color = if (c.isNull(8)) 0xFF3949AB.toInt() else c.getInt(8),
                        status = if (c.isNull(9)) 0 else c.getInt(9),
                        calendarId = c.getLong(10),
                    )
                )
            }
        }
        return out
    }

    private class Day(val start: Long, val end: Long, val utcStart: Long, val utcEnd: Long)

    private fun today(): Day {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone)
        return Day(
            start = date.atStartOfDay(zone).toInstant().toEpochMilli(),
            end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            // Los eventos de "todo el día" se guardan normalmente en UTC
            utcStart = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            utcEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
    }

    /** null = es de hoy; si no, el motivo por el que se descarta. */
    private fun reject(r: Raw, d: Day, included: Set<Long>): String? {
        if (r.calendarId !in included) return "calendario no elegido"
        if (r.status == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) return "invitación rechazada"
        val isToday = if (r.allDay) {
            // Solapa con el día de hoy (en UTC o en hora local, según cómo lo guardó la app)
            (r.begin < d.utcEnd && r.end > d.utcStart) &&
                (r.begin < d.end + 12 * 3_600_000L && r.end > d.start - 12 * 3_600_000L)
        } else {
            (r.begin < d.end && r.end > d.start) || (r.begin == r.end && r.begin in d.start until d.end)
        }
        return if (isToday) null else "no es de hoy"
    }

    fun today(ctx: Context): List<AgendaItem> {
        if (!hasPermission(ctx)) return emptyList()
        val d = today()
        val included = includedIds(ctx)
        return queryRaw(ctx, d.start - 86_400_000L, d.end + 86_400_000L)
            .filter { reject(it, d, included) == null }
            .map {
                AgendaItem(
                    eventId = it.eventId, begin = it.begin, end = it.end, allDay = it.allDay,
                    title = it.title, location = it.location,
                    description = cleanDescription(it.description),
                    calendarName = it.calendarName, color = it.color,
                )
            }
            .distinctBy { it.key }
    }

    /** Informe para ver qué calendarios y eventos encuentra la app y por qué descarta cada uno. */
    fun diagnose(ctx: Context): String {
        if (!hasPermission(ctx)) return "Sin permiso de calendario."
        val sb = StringBuilder()
        val d = today()
        val included = includedIds(ctx)
        val fmt = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())
        val fmtUtc = DateTimeFormatter.ofPattern("dd/MM HH:mm'Z'").withZone(ZoneOffset.UTC)

        sb.append("CALENDARIOS\n")
        calendars(ctx).forEach {
            sb.append(if (it.id in included) "✓ " else "✗ ")
            sb.append("${it.name}  [${it.account}]")
            if (!it.visible) sb.append("  (oculto)")
            if (!it.synced) sb.append("  (sin sincronizar)")
            sb.append("\n")
        }
        sb.append("\nEVENTOS (ayer–mañana)\n")
        val cals = calendars(ctx).associateBy { it.id }
        queryRaw(ctx, d.start - 86_400_000L, d.end + 86_400_000L).forEach { r ->
            val why = reject(r, d, included)
            sb.append(if (why == null) "✓ " else "✗ ")
            sb.append(r.title)
            sb.append("\n    ")
            if (r.allDay) sb.append("todo el día ${fmtUtc.format(Instant.ofEpochMilli(r.begin))} → ${fmtUtc.format(Instant.ofEpochMilli(r.end))}")
            else sb.append("${fmt.format(Instant.ofEpochMilli(r.begin))} → ${fmt.format(Instant.ofEpochMilli(r.end))}")
            sb.append("\n    ${cals[r.calendarId]?.name ?: r.calendarName}")
            if (why != null) sb.append(" · $why")
            sb.append("\n")
        }
        return sb.toString()
    }

    /** Algunas descripciones vienen con HTML (Google Calendar). Las dejamos en texto plano. */
    private fun cleanDescription(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val text = android.text.Html.fromHtml(raw, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        return text.replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
