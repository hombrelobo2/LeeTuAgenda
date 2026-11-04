package com.leetuagenda

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Expansión sencilla de repeticiones (RRULE) para los calendarios ocultos, que Android no expande.
 * Cubre lo habitual: diario, semanal (con días), mensual, anual, INTERVAL, COUNT y UNTIL.
 */
object RecurrenceLite {

    fun occurrences(
        rrule: String, start: Long, duration: Long, allDay: Boolean, from: Long, to: Long
    ): List<Pair<Long, Long>> {
        val parts = rrule.split(';').mapNotNull {
            val i = it.indexOf('='); if (i > 0) it.substring(0, i).uppercase() to it.substring(i + 1) else null
        }.toMap()
        val freq = parts["FREQ"] ?: return emptyList()
        val interval = (parts["INTERVAL"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val count = parts["COUNT"]?.toIntOrNull()
        val until = parts["UNTIL"]?.let { parseUntil(it) }
        val zone: ZoneId = if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()
        val first = Instant.ofEpochMilli(start).atZone(zone)
        val byDay = parts["BYDAY"]?.split(',')?.mapNotNull { dayOf(it.takeLast(2)) }?.toSet()

        val out = ArrayList<Pair<Long, Long>>()
        var emitted = 0
        var step = 0L
        // Sin COUNT, saltar directamente cerca de hoy (eventos que empezaron hace años)
        if (count == null) {
            val periodDays = when (freq) { "DAILY" -> 1L; "WEEKLY" -> 7L; "MONTHLY" -> 28L; "YEARLY" -> 365L; else -> 1L }
            val periodMs = periodDays * interval * 86_400_000L
            step = ((from - start - duration) / periodMs - 2).coerceAtLeast(0)
        }
        val maxSteps = step + 400

        fun emit(z: ZonedDateTime): Boolean {
            val b = z.toInstant().toEpochMilli()
            if (b < start) return true
            if (until != null && b > until) return false
            if (count != null && emitted >= count) return false
            emitted++
            if (b < to && b + duration > from) out.add(b to b + duration)
            return b < to + 86_400_000L
        }

        while (step < maxSteps) {
            val base: ZonedDateTime = when (freq) {
                "DAILY" -> first.plusDays(step * interval)
                "WEEKLY" -> first.plusWeeks(step * interval)
                "MONTHLY" -> first.plusMonths(step * interval)
                "YEARLY" -> first.plusYears(step * interval)
                else -> return out
            }
            if (freq == "WEEKLY" && !byDay.isNullOrEmpty()) {
                // Recorrer la semana (lunes a domingo) de esta repetición
                val monday = base.minusDays((base.dayOfWeek.value - 1).toLong())
                for (i in 0..6) {
                    val d = monday.plusDays(i.toLong())
                    if (d.dayOfWeek in byDay && !emit(d)) return out
                }
            } else {
                if (!emit(base)) return out
            }
            step++
        }
        return out
    }

    private fun dayOf(s: String): DayOfWeek? = when (s.uppercase()) {
        "MO" -> DayOfWeek.MONDAY; "TU" -> DayOfWeek.TUESDAY; "WE" -> DayOfWeek.WEDNESDAY
        "TH" -> DayOfWeek.THURSDAY; "FR" -> DayOfWeek.FRIDAY; "SA" -> DayOfWeek.SATURDAY
        "SU" -> DayOfWeek.SUNDAY; else -> null
    }

    private fun parseUntil(s: String): Long? = try {
        when {
            s.length == 8 -> LocalDate.parse(s, DateTimeFormatter.BASIC_ISO_DATE)
                .plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1
            s.endsWith("Z") -> LocalDateTime.parse(s.dropLast(1), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                .toInstant(ZoneOffset.UTC).toEpochMilli()
            else -> LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    } catch (e: Exception) { null }
}
