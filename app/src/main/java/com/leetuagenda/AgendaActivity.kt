package com.leetuagenda

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pantalla completa con las entradas del día. Hay que tocar cada una para darla por leída.
 * - El botón "atrás" no la cierra.
 * - Inicio / recientes / llamadas / alarmas funcionan con normalidad (no es un bloqueo).
 * - Si sales sin terminar, el recordatorio vuelve a abrirla al cabo de unos minutos.
 */
class AgendaActivity : Activity() {

    companion object {
        /** Tiempo mínimo entre dos toques para evitar marcar todo "en piloto automático". */
        const val MIN_MS_BETWEEN_TAPS = 1200L
    }

    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var doneButton: Button
    private lateinit var root: LinearLayout

    private var items: List<AgendaItem> = emptyList()
    private var newKeys: Set<String> = emptySet()
    private var lastTap = 0L

    private val bg = Color.parseColor("#14161F")
    private val cardBg = Color.parseColor("#1F2230")
    private val cardDoneBg = Color.parseColor("#17311F")
    private val accent = Color.parseColor("#FFC857")
    private val green = Color.parseColor("#4CC38A")
    private val textMain = Color.parseColor("#F3F4F8")
    private val textSoft = Color.parseColor("#A6AABB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        showOverLockScreen()
        buildUi()
        blockBackButton()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    // ---------- Ventana ----------

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun blockBackButton() {
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, OnBackInvokedCallback { onBackBlocked() }
            )
        }
    }

    @Deprecated("Solo para Android 12 y anteriores")
    override fun onBackPressed() {
        onBackBlocked()
    }

    private fun onBackBlocked() {
        Toast.makeText(
            this,
            "Primero lee tu agenda 🙂  (Para llamar, usa el botón 📞 o el botón de inicio)",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ---------- Interfaz ----------

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun rounded(color: Int, radius: Int = 18, stroke: Int = 0, strokeColor: Int = 0) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke > 0) setStroke(dp(stroke), strokeColor)
        }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        // Cabecera
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(12))
        }
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "ES")))
        header.addView(TextView(this).apply {
            text = "HOY · " + date.uppercase(Locale("es", "ES"))
            setTextColor(accent)
            textSize = 13f
            letterSpacing = 0.08f
            typeface = Typeface.DEFAULT_BOLD
        })
        header.addView(TextView(this).apply {
            text = "Lee cada entrada y tócala"
            setTextColor(textMain)
            textSize = 26f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(4), 0, dp(10))
        })
        progressText = TextView(this).apply {
            setTextColor(textSoft)
            textSize = 14f
        }
        header.addView(progressText)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(green)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2C3042"))
        }
        header.addView(progressBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)).apply {
            topMargin = dp(8)
        })
        root.addView(header)

        // Lista
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(16))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(list)
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Barra inferior
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(10), dp(16), dp(14))
            gravity = Gravity.CENTER_VERTICAL
        }
        val callButton = Button(this).apply {
            text = "📞 Llamar"
            isAllCaps = false
            textSize = 16f
            setTextColor(textMain)
            background = rounded(Color.parseColor("#2C3042"), 26)
            setOnClickListener {
                // Abre el marcador sin cerrar la agenda (seguirá pendiente)
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
            }
        }
        bottom.addView(callButton, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(12) })
        doneButton = Button(this).apply {
            isAllCaps = false
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { finishReview() }
        }
        bottom.addView(doneButton, LinearLayout.LayoutParams(0, dp(56), 2f))
        root.addView(bottom)

        // Respetar barras del sistema (Android 15 dibuja de borde a borde)
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
            }
            insets
        }
        setContentView(root)
    }

    private fun reload() {
        // Se recarga cada vez que vuelves, por si la agenda ha cambiado
        items = CalendarRepo.today(this)
        newKeys = prefs.registerAndGetNew(items.map { it.eventId.toString() })
        renderList()
    }

    private fun renderList() {
        list.removeAllViews()
        val checked = prefs.checkedToday()

        if (!CalendarRepo.hasPermission(this)) {
            list.addView(message("Falta el permiso del calendario.\nAbre la app «Lee tu agenda» y concédelo."))
        } else if (items.isEmpty()) {
            list.addView(message("Hoy no hay nada en tu agenda. 🎉"))
        }

        val zone = ZoneId.systemDefault()
        val hm = DateTimeFormatter.ofPattern("HH:mm")
        items.forEachIndexed { index, item ->
            val isDone = item.key in checked
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = if (isDone) rounded(cardDoneBg, 18, 1, green)
                             else rounded(cardBg, 18, 1, Color.parseColor("#2C3042"))
                setPadding(0, 0, dp(16), 0)
                isClickable = true
                isFocusable = true
            }

            // Franja de color del calendario
            card.addView(View(this).apply {
                background = GradientDrawable().apply {
                    setColor(if (isDone) green else (item.color or 0xFF000000.toInt()))
                    cornerRadii = floatArrayOf(
                        dp(18).toFloat(), dp(18).toFloat(), 0f, 0f, 0f, 0f, dp(18).toFloat(), dp(18).toFloat()
                    )
                }
            }, LinearLayout.LayoutParams(dp(8), ViewGroup.LayoutParams.MATCH_PARENT))

            val body = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(14), dp(8), dp(14))
            }

            // Línea superior: hora + NUEVO
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val timeText = if (item.allDay) "Todo el día" else {
                val b = Instant.ofEpochMilli(item.begin).atZone(zone).format(hm)
                val e = Instant.ofEpochMilli(item.end).atZone(zone).format(hm)
                if (b == e) b else "$b – $e"
            }
            top.addView(TextView(this).apply {
                text = "${index + 1}.  $timeText"
                setTextColor(if (isDone) green else accent)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
            })
            if (item.eventId.toString() in newKeys) {
                top.addView(TextView(this).apply {
                    text = "NUEVO"
                    setTextColor(bg)
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    background = rounded(accent, 8)
                    setPadding(dp(8), dp(2), dp(8), dp(2))
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(10) })
            }
            body.addView(top)

            body.addView(TextView(this).apply {
                text = item.title
                setTextColor(if (isDone) textSoft else textMain)
                textSize = 21f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(0, dp(6), 0, 0)
            })
            if (item.location.isNotEmpty()) {
                body.addView(TextView(this).apply {
                    text = "📍 " + item.location
                    setTextColor(textSoft)
                    textSize = 15f
                    setPadding(0, dp(6), 0, 0)
                })
            }
            if (item.description.isNotEmpty()) {
                body.addView(TextView(this).apply {
                    text = item.description
                    setTextColor(textSoft)
                    textSize = 15f
                    maxLines = if (isDone) 2 else 8
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(6), 0, 0)
                })
            }
            body.addView(TextView(this).apply {
                text = if (isDone) "✓ Leída  ·  mantén pulsado para desmarcar" else "Toca cuando la hayas leído"
                setTextColor(if (isDone) green else Color.parseColor("#6E7389"))
                textSize = 12f
                setPadding(0, dp(10), 0, 0)
            })
            card.addView(body, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            // Círculo de verificación
            card.addView(TextView(this).apply {
                text = if (isDone) "✓" else ""
                gravity = Gravity.CENTER
                setTextColor(bg)
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (isDone) setColor(green) else {
                        setColor(Color.TRANSPARENT); setStroke(dp(2), textSoft)
                    }
                }
            }, LinearLayout.LayoutParams(dp(32), dp(32)).apply { gravity = Gravity.CENTER_VERTICAL })

            card.setOnClickListener { v -> onCardTap(v, item, isDone) }
            card.setOnLongClickListener {
                if (isDone) {
                    prefs.setChecked(item.key, false)
                    renderList()
                }
                true
            }

            list.addView(card, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) })
        }

        // Progreso y botón final
        val total = items.size
        val read = items.count { it.key in checked }
        val allRead = read == total
        progressText.text = if (total == 0) "Nada pendiente" else "$read de $total leídas"
        progressBar.progress = if (total == 0) 100 else read * 100 / total
        doneButton.isEnabled = allRead
        doneButton.text = if (allRead) "✓ Hecho" else "Faltan ${total - read}"
        doneButton.setTextColor(if (allRead) bg else textSoft)
        doneButton.background = rounded(if (allRead) green else Color.parseColor("#262938"), 26)
    }

    private fun message(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(textMain)
        textSize = 20f
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(60), dp(16), dp(60))
    }

    private fun onCardTap(v: View, item: AgendaItem, isDone: Boolean) {
        if (isDone) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastTap < MIN_MS_BETWEEN_TAPS) {
            // Demasiado rápido: probablemente no la has leído
            ObjectAnimator.ofFloat(v, "translationX", 0f, 18f, -18f, 12f, -12f, 0f).setDuration(350).start()
            Toast.makeText(this, "Despacio: léela de verdad 👀", Toast.LENGTH_SHORT).show()
            return
        }
        lastTap = now
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        prefs.setChecked(item.key, true)
        renderList()
    }

    private fun finishReview() {
        prefs.markDoneToday()
        Scheduler.cancelNag(this)
        Notifier.clear(this)
        Toast.makeText(this, "¡Agenda leída! 💪", Toast.LENGTH_SHORT).show()
        finishAndRemoveTask()
    }
}
