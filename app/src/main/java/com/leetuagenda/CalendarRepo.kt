package com.leetuagenda

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

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

/** Lee los eventos de HOY de todos los calendarios visibles del móvil (Google, Samsung, etc.). */
object CalendarRepo {

    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun today(ctx: Context): List<AgendaItem> {
        if (!hasPermission(ctx)) return emptyList()

        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone)
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        // Los eventos de "todo el día" se guardan en UTC
        val utcDayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        // Margen para no perder eventos de todo el día por la diferencia horaria
        ContentUris.appendId(builder, start - 86_400_000L)
        ContentUris.appendId(builder, end + 86_400_000L)

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
        )
        val selection = "${CalendarContract.Instances.VISIBLE} = 1"

        val result = ArrayList<AgendaItem>()
        ctx.contentResolver.query(
            builder.build(), projection, selection, null,
            "${CalendarContract.Instances.ALL_DAY} DESC, ${CalendarContract.Instances.BEGIN} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val allDay = c.getInt(3) == 1
                val b = c.getLong(1)
                val e = c.getLong(2)
                val status = if (c.isNull(9)) 0 else c.getInt(9)
                if (status == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue

                val isToday = if (allDay) {
                    b <= utcDayStart && e > utcDayStart
                } else {
                    b < end && e > start || (b == e && b in start until end)
                }
                if (!isToday) continue

                result.add(
                    AgendaItem(
                        eventId = c.getLong(0),
                        begin = b,
                        end = e,
                        allDay = allDay,
                        title = c.getString(4)?.trim().takeUnless { it.isNullOrEmpty() } ?: "(sin título)",
                        location = c.getString(5)?.trim() ?: "",
                        description = cleanDescription(c.getString(6)),
                        calendarName = c.getString(7) ?: "",
                        color = if (c.isNull(8)) 0xFF3949AB.toInt() else c.getInt(8),
                    )
                )
            }
        }
        return result.distinctBy { it.key }
    }

    /** Algunas descripciones vienen con HTML (Google Calendar). Las dejamos en texto plano. */
    private fun cleanDescription(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val text = android.text.Html.fromHtml(raw, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        return text.replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
