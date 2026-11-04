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
    val accountType: String = "",
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
            CalendarContract.Calendars.ACCOUNT_TYPE,
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
                        accountType = c.getString(6) ?: "",
                    )
                )
            }
        }
        return out.sortedWith(compareBy({ it.account }, { it.name }))
    }


    /** ¿Tiene Android activada la sincronización de calendario para la cuenta de este calendario? */
    fun accountSyncOn(c: CalendarInfo): Boolean? = try {
        if (c.accountType.isEmpty() || c.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL) null
        else android.content.ContentResolver.getSyncAutomatically(
            android.accounts.Account(c.account, c.accountType), CalendarContract.AUTHORITY
        ) && android.content.ContentResolver.getMasterSyncAutomatically()
    } catch (e: Exception) { null }

    /** Número de eventos guardados en el móvil para cada calendario (cualquier fecha). */
    fun eventCounts(ctx: Context): Map<Long, Int> {
        val out = HashMap<Long, Int>()
        ctx.contentResolver.query(
            CalendarContract.Events.CONTENT_URI, arrayOf(CalendarContract.Events.CALENDAR_ID),
            "${CalendarContract.Events.DELETED} = 0", null, null
        )?.use { c -> while (c.moveToNext()) out.merge(c.getLong(0), 1, Int::plus) }
        return out
    }

    /** Calendarios que se incluyen: los elegidos por el usuario, o por defecto todos menos festivos.
     *  (La marca "visible" del sistema no coincide con lo que muestra Google Calendar, así que no se usa.) */
    fun includedIds(ctx: Context): Set<Long> {
        val chosen = Prefs(ctx).chosenCalendars()
        return chosen ?: calendars(ctx).filter { !isHolidays(it) }.map { it.id }.toSet()
    }

    private fun isHolidays(c: CalendarInfo): Boolean {
        val n = c.name.lowercase()
        return "festivo" in n || "holiday" in n || "#holiday@" in c.account
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


    /** Android no incluye en Instances los calendarios marcados como ocultos (aunque Google Calendar
     *  sí los muestre). Para esos se leen los eventos directamente de la tabla Events. */
    private fun queryHiddenDirect(ctx: Context, from: Long, to: Long, calIds: Collection<Long>): List<Raw> {
        if (calIds.isEmpty()) return emptyList()
        val cals = calendars(ctx).associateBy { it.id }
        val out = ArrayList<Raw>()
        val proj = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DISPLAY_COLOR,
            CalendarContract.Events.SELF_ATTENDEE_STATUS,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.DURATION,
        )
        val sel = "${CalendarContract.Events.CALENDAR_ID} IN (${calIds.joinToString(",")}) AND " +
            "${CalendarContract.Events.DELETED} = 0 AND " +
            "(${CalendarContract.Events.STATUS} IS NULL OR ${CalendarContract.Events.STATUS} != ${CalendarContract.Events.STATUS_CANCELED})"
        ctx.contentResolver.query(CalendarContract.Events.CONTENT_URI, proj, sel, null, null)?.use { c ->
            while (c.moveToNext()) {
                val begin = c.getLong(1)
                val rrule = c.getString(10)
                val end = when {
                    !c.isNull(2) -> c.getLong(2)
                    else -> begin + parseDuration(c.getString(11))
                }
                val allDay = c.getInt(3) == 1
                val calId = c.getLong(9)
                if (rrule.isNullOrEmpty()) {
                    if (begin < to && end > from) out.add(rawOf(c, begin, end, allDay, calId, cals))
                } else {
                    // Evento repetido: se expande con las reglas más habituales
                    RecurrenceLite.occurrences(rrule, begin, end - begin, allDay, from, to).forEach { (b, e) ->
                        out.add(rawOf(c, b, e, allDay, calId, cals))
                    }
                }
            }
        }
        return out
    }

    private fun rawOf(
        c: android.database.Cursor, b: Long, e: Long, allDay: Boolean, calId: Long, cals: Map<Long, CalendarInfo>
    ) = Raw(
        eventId = c.getLong(0), begin = b, end = e, allDay = allDay,
        title = c.getString(4)?.trim().takeUnless { it.isNullOrEmpty() } ?: "(sin título)",
        location = c.getString(5)?.trim() ?: "",
        description = c.getString(6),
        calendarName = cals[calId]?.name ?: "",
        color = if (c.isNull(7)) (cals[calId]?.color ?: 0xFF3949AB.toInt()) else c.getInt(7),
        status = if (c.isNull(8)) 0 else c.getInt(8),
        calendarId = calId,
    )

    /** Duraciones RFC 5545 tipo "P1D", "PT3600S", "PT1H30M". */
    private fun parseDuration(d: String?): Long {
        if (d.isNullOrEmpty()) return 0
        val m = Regex("P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?").find(d) ?: return 0
        fun g(i: Int) = m.groupValues[i].toLongOrNull() ?: 0L
        return ((g(1) * 7 + g(2)) * 86400 + g(3) * 3600 + g(4) * 60 + g(5)) * 1000
    }

    /** Todas las apariciones en el rango: Instances (calendarios visibles) + lectura directa (ocultos). */
    private fun allRaw(ctx: Context, from: Long, to: Long, included: Set<Long>): List<Raw> {
        val hidden = calendars(ctx).filter { !it.visible && it.id in included }.map { it.id }
        val viaInstances = queryRaw(ctx, from, to)
        val keys = viaInstances.map { "${it.eventId}_${it.begin}" }.toHashSet()
        val direct = queryHiddenDirect(ctx, from, to, hidden).filter { "${it.eventId}_${it.begin}" !in keys }
        return (viaInstances + direct).sortedWith(compareBy({ !it.allDay }, { it.begin }))
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
        return allRaw(ctx, d.start - 86_400_000L, d.end + 86_400_000L, included)
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

        val version = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "?" }
        sb.append("Versión $version (${BuildConfig.COMMIT})\n\n")
        val counts = eventCounts(ctx)
        sb.append("CALENDARIOS\n")
        calendars(ctx).forEach {
            sb.append(if (it.id in included) "✓ " else "✗ ")
            sb.append("${it.name}  [${it.account}]")
            if (!it.visible) sb.append("  (oculto)")
            if (!it.synced) sb.append("  (sin sincronizar)")
            sb.append("  · ${counts[it.id] ?: 0} eventos en el móvil")
            if (accountSyncOn(it) == false) sb.append("  ⚠ SINCRONIZACIÓN DE LA CUENTA DESACTIVADA EN ANDROID")
            sb.append("\n")
        }
        sb.append("\nEVENTOS (ayer–mañana)\n")
        val cals = calendars(ctx).associateBy { it.id }
        allRaw(ctx, d.start - 86_400_000L, d.end + 86_400_000L, included).forEach { r ->
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
