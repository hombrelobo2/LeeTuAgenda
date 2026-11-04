package com.leetuagenda

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/** Configuración y estado guardado en el móvil. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    var hour: Int
        get() = sp.getInt("hour", 8)
        set(v) = sp.edit().putInt("hour", v).apply()

    var minute: Int
        get() = sp.getInt("minute", 0)
        set(v) = sp.edit().putInt("minute", v).apply()

    /** Repetir el aviso cada X minutos hasta completar la revisión. 0 = no insistir. */
    var nagMinutes: Int
        get() = sp.getInt("nagMinutes", 15)
        set(v) = sp.edit().putInt("nagMinutes", v).apply()

    /** Si el día no tiene eventos, no molestar. */
    var skipEmpty: Boolean
        get() = sp.getBoolean("skipEmpty", true)
        set(v) = sp.edit().putBoolean("skipEmpty", v).apply()

    /** Hora límite (0-23) a partir de la cual deja de insistir ese día. */
    var quietHour: Int
        get() = sp.getInt("quietHour", 23)
        set(v) = sp.edit().putInt("quietHour", v).apply()

    fun todayKey(): String = LocalDate.now().toString()

    fun isDoneToday(): Boolean = sp.getString("doneDate", null) == todayKey()

    fun markDoneToday() {
        sp.edit()
            .putString("doneDate", todayKey())
            .putLong("doneAt", System.currentTimeMillis())
            .apply()
        cleanupOldChecks()
    }

    // --- Entradas marcadas como leídas hoy (sobrevive a salir de la app) ---

    fun checkedToday(): MutableSet<String> =
        HashSet(sp.getStringSet("checked_" + todayKey(), emptySet()) ?: emptySet())

    fun setChecked(key: String, checked: Boolean) {
        val set = checkedToday()
        if (checked) set.add(key) else set.remove(key)
        sp.edit().putStringSet("checked_" + todayKey(), set).apply()
    }

    private fun cleanupOldChecks() {
        val keep = "checked_" + todayKey()
        val ed = sp.edit()
        sp.all.keys.filter { it.startsWith("checked_") && it != keep }.forEach { ed.remove(it) }
        ed.apply()
    }

    // --- Eventos vistos alguna vez (para marcar los NUEVOS) ---
    // Se guarda "idEvento|primeraVez|últimaVez". Es NUEVO si su primera vez es hoy.

    /** Registra los eventos de hoy y devuelve los que son nuevos (vistos por primera vez hoy). */
    fun registerAndGetNew(eventIds: Collection<String>): Set<String> {
        val today = todayKey()
        val limit = LocalDate.now().minusDays(90).toString()
        val first = HashMap<String, String>()
        val last = HashMap<String, String>()
        (sp.getStringSet("seen", emptySet()) ?: emptySet()).forEach { entry ->
            val p = entry.split('|')
            if (p.size == 3) { first[p[0]] = p[1]; last[p[0]] = p[2] }
        }
        eventIds.forEach {
            if (!first.containsKey(it)) first[it] = today
            last[it] = today
        }
        // Olvidar lo que lleva 90 días sin aparecer
        val out = HashSet<String>()
        first.keys.forEach { id ->
            val l = last[id] ?: today
            if (l >= limit) out.add("$id|${first[id]}|$l")
        }
        val started = sp.getString("seenStarted", null) ?: today
        sp.edit().putStringSet("seen", out).putString("seenStarted", started).apply()
        // El primer día de uso todo sería "nuevo"; no tiene sentido marcarlo.
        if (started == today) return emptySet()
        return eventIds.filter { first[it] == today }.toSet()
    }
}
