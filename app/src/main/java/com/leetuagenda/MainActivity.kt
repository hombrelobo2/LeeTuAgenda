package com.leetuagenda

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Pantalla de configuración: permisos, hora diaria y opciones. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var content: LinearLayout

    private val indigo = Color.parseColor("#3949AB")
    private val ok = Color.parseColor("#2E9E6A")
    private val warn = Color.parseColor("#D9822B")
    private val textMain = Color.parseColor("#1B1D29")
    private val textSoft = Color.parseColor("#646A80")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        Notifier.ensureChannel(this)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F4F5FA"))
            addView(content)
            setOnApplyWindowInsetsListener { v, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val b = insets.getInsets(WindowInsets.Type.systemBars())
                    v.setPadding(b.left, b.top, b.right, b.bottom)
                }
                insets
            }
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun dp(v: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            setColor(Color.WHITE); cornerRadius = dp(16).toFloat()
        }
    }

    private fun addCard(c: LinearLayout) {
        content.addView(c, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(14) })
    }

    private fun sectionTitle(t: String) = TextView(this).apply {
        text = t.uppercase()
        setTextColor(textSoft)
        textSize = 12f
        letterSpacing = 0.08f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(4), dp(8), 0, dp(8))
    }

    private fun button(label: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(if (primary) Color.WHITE else indigo)
        background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            if (primary) setColor(indigo) else { setColor(Color.TRANSPARENT); setStroke(dp(1), indigo) }
        }
        setOnClickListener { onClick() }
    }

    // ---------- Pantalla ----------

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private fun render() {
        content.removeAllViews()

        content.addView(TextView(this).apply {
            text = "Lee tu agenda"
            textSize = 28f
            setTextColor(textMain)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        content.addView(TextView(this).apply {
            text = "Cada día, a la hora que elijas, tu agenda aparecerá a pantalla completa y tendrás que tocar cada entrada para darla por leída."
            textSize = 15f
            setTextColor(textSoft)
            setPadding(0, dp(6), 0, dp(14))
        })

        // --- Permisos ---
        content.addView(sectionTitle("1 · Permisos"))
        val perms = card()
        permRow(perms, "Leer el calendario", "Para ver tus eventos de hoy.", CalendarRepo.hasPermission(this), true) {
            requestRuntimePerms()
        }
        permRow(perms, "Notificaciones", "Para el aviso que abre la agenda.", Notifier.canNotify(this), true) {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) requestRuntimePerms()
            else openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS, false) {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        }
        permRow(perms, "Pantalla completa", "Para que salte sobre la pantalla de bloqueo.", Notifier.canFullScreen(this), true) {
            if (Build.VERSION.SDK_INT >= 34) openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, true)
        }
        permRow(perms, "Alarmas exactas", "Para que salte justo a su hora.", Scheduler.canExact(this), true) {
            if (Build.VERSION.SDK_INT >= 31) openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, true)
        }
        permRow(
            perms, "Mostrar sobre otras apps",
            "Recomendado: sin esto, si estás usando el móvil solo verás una notificación en vez de la pantalla completa.",
            Notifier.canOverlay(this), false
        ) {
            openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true)
        }
        val pm = getSystemService(PowerManager::class.java)
        permRow(
            perms, "Sin restricción de batería",
            "Evita que el sistema retrase o cancele el aviso (importante en Xiaomi, Samsung, Huawei…).",
            pm.isIgnoringBatteryOptimizations(packageName), false
        ) {
            @SuppressLint("BatteryLife")
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            safeStart(i)
        }
        addCard(perms)

        // --- Horario ---
        content.addView(sectionTitle("2 · Cuándo"))
        val sched = card()
        val enable = Switch(this).apply {
            text = "Activar el aviso diario"
            textSize = 17f
            setTextColor(textMain)
            isChecked = prefs.enabled
            setOnCheckedChangeListener { _, checked ->
                prefs.enabled = checked
                Scheduler.scheduleDaily(this@MainActivity)
                if (!checked) Scheduler.cancelNag(this@MainActivity)
                render()
            }
        }
        sched.addView(enable)

        sched.addView(settingRow("Hora del aviso", "%02d:%02d".format(prefs.hour, prefs.minute)) {
            TimePickerDialog(this, { _, h, m ->
                prefs.hour = h; prefs.minute = m
                Scheduler.scheduleDaily(this)
                render()
            }, prefs.hour, prefs.minute, true).show()
        })

        val nagLabel = if (prefs.nagMinutes == 0) "No insistir" else "Cada ${prefs.nagMinutes} min"
        sched.addView(settingRow("Si no la terminas, volver a abrirla", nagLabel) {
            val options = intArrayOf(5, 10, 15, 30, 60, 0)
            val idx = options.indexOf(prefs.nagMinutes)
            prefs.nagMinutes = options[(idx + 1) % options.size]
            render()
        })

        sched.addView(settingRow("Dejar de insistir a partir de", "%02d:00".format(prefs.quietHour)) {
            TimePickerDialog(this, { _, h, _ ->
                prefs.quietHour = if (h == 0) 23 else h
                render()
            }, prefs.quietHour, 0, true).show()
        })

        sched.addView(Switch(this).apply {
            text = "No avisar los días sin eventos"
            textSize = 15f
            setTextColor(textMain)
            isChecked = prefs.skipEmpty
            setPadding(0, dp(12), 0, 0)
            setOnCheckedChangeListener { _, c -> prefs.skipEmpty = c }
        })

        if (prefs.enabled) {
            val status = if (prefs.isDoneToday()) "✓ Hoy ya la has leído." else "Pendiente de leer hoy."
            sched.addView(TextView(this).apply {
                text = status
                textSize = 14f
                setTextColor(if (prefs.isDoneToday()) ok else warn)
                setPadding(0, dp(12), 0, 0)
            })
        }
        addCard(sched)

        // --- Calendarios ---
        content.addView(sectionTitle("3 · Calendarios"))
        val calCard = card()
        val cals = CalendarRepo.calendars(this)
        if (cals.isEmpty()) {
            calCard.addView(TextView(this).apply {
                text = "Concede el permiso del calendario para ver la lista."
                textSize = 14f; setTextColor(textSoft)
            })
        } else {
            val included = CalendarRepo.includedIds(this).toMutableSet()
            cals.forEach { cal ->
                calCard.addView(android.widget.CheckBox(this).apply {
                    text = cal.name + (if (cal.account.isNotEmpty() && cal.account != cal.name) "\n" + cal.account else "") +
                        (if (!cal.synced)
                            "\n⚠ No sincronizado en Android: sus eventos no llegan al móvil" else "")
                    textSize = 15f
                    setTextColor(textMain)
                    buttonTintList = android.content.res.ColorStateList.valueOf(cal.color or 0xFF000000.toInt())
                    isChecked = cal.id in included
                    setPadding(dp(4), dp(6), 0, dp(6))
                    setOnCheckedChangeListener { _, c ->
                        if (c) included.add(cal.id) else included.remove(cal.id)
                        prefs.setChosenCalendars(included)
                    }
                })
            }
        }
        calCard.addView(button("Diagnóstico: ¿qué eventos ve la app?") {
            val tv = TextView(this).apply {
                text = CalendarRepo.diagnose(this@MainActivity)
                textSize = 13f
                setTextIsSelectable(true)
                setPadding(dp(20), dp(12), dp(20), dp(12))
            }
            android.app.AlertDialog.Builder(this)
                .setTitle("Diagnóstico")
                .setView(ScrollView(this).apply { addView(tv) })
                .setPositiveButton("Cerrar", null)
                .show()
        }, fullWidth().apply { topMargin = dp(10) })
        addCard(calCard)

        // --- Probar ---
        content.addView(sectionTitle("4 · Probar"))
        val test = card()
        test.addView(button("Abrir la agenda ahora", primary = true) {
            startActivity(Notifier.agendaIntent(this))
        }, fullWidth())
        test.addView(button("Simular el aviso dentro de 15 s") {
            Scheduler.scheduleTest(this, 15)
            Toast.makeText(this, "Bloquea el móvil y espera 15 segundos", Toast.LENGTH_LONG).show()
        }, fullWidth().apply { topMargin = dp(10) })
        test.addView(button("Reiniciar el día de hoy") {
            getSharedPreferences("cfg", MODE_PRIVATE).edit()
                .remove("doneDate").remove("checked_" + prefs.todayKey()).apply()
            render()
        }, fullWidth().apply { topMargin = dp(10) })
        addCard(test)

        content.addView(TextView(this).apply {
            text = "Para salir de la agenda sin terminar (para llamar, por ejemplo) usa el botón 📞 o el botón de inicio. " +
                "Las llamadas entrantes y las alarmas del reloj se muestran encima con normalidad. " +
                "La agenda seguirá pendiente y volverá a abrirse sola."
            textSize = 13f
            setTextColor(textSoft)
            setPadding(dp(4), dp(4), dp(4), 0)
        })
    }

    private fun fullWidth() =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))

    private fun permRow(
        parent: LinearLayout, title: String, desc: String, granted: Boolean, required: Boolean, action: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = (if (granted) "✓  " else if (required) "⚠  " else "○  ") + title
            textSize = 16f
            setTextColor(if (granted) ok else if (required) warn else textMain)
            typeface = Typeface.DEFAULT_BOLD
        })
        texts.addView(TextView(this).apply {
            text = desc
            textSize = 13f
            setTextColor(textSoft)
        })
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (!granted) {
            row.addView(button("Conceder") { action() },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { marginStart = dp(10) })
        }
        parent.addView(row)
    }

    private fun settingRow(label: String, value: String, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(14), 0, dp(4))
        isClickable = true
        setOnClickListener { onClick() }
        addView(TextView(context).apply {
            text = label
            textSize = 15f
            setTextColor(textMain)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = value
            textSize = 15f
            setTextColor(indigo)
            typeface = Typeface.DEFAULT_BOLD
        })
    }

    // ---------- Permisos ----------

    private fun requestRuntimePerms() {
        val list = mutableListOf(Manifest.permission.READ_CALENDAR)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(list.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    private fun openSettings(action: String, withPackageUri: Boolean = false, extras: Intent.() -> Unit = {}) {
        val i = if (withPackageUri) Intent(action, Uri.parse("package:$packageName")) else Intent(action)
        i.extras()
        safeStart(i)
    }

    private fun safeStart(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            // Algunos fabricantes no tienen esa pantalla: abrir los ajustes de la app
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }
}
