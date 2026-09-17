package com.diariodehoras.app

import android.app.*
import android.content.*
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

data class Entry(val start:String, val end:String, val text:String)

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("diary", MODE_PRIVATE) }
    private val entries = mutableListOf<Entry>()
    private lateinit var list: LinearLayout
    private lateinit var note: EditText
    private lateinit var start: EditText
    private lateinit var end: EditText
    private lateinit var report: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()
        buildUi()
        requestNotificationPermission()
        scheduleHourlyReminder()
    }

    private fun buildUi() {
        val root = ScrollView(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 40)
            setBackgroundColor(0xFFF6F1E8.toInt())
        }
        root.addView(wrap)

        val title = TextView(this).apply {
            text = "DIÁRIO DE HORAS\n\nSeu dia, registrado hora a hora."
            textSize = 28f
            setTextColor(0xFF17324D.toInt())
        }
        wrap.addView(title)

        val date = TextView(this).apply {
            text = SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt","BR")).format(Date())
            textSize = 15f
            setPadding(0, 10, 0, 22)
        }
        wrap.addView(date)

        wrap.addView(label("HORÁRIO DO REGISTRO"))
        val times = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        start = timeField()
        end = timeField()
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        start.setText(String.format("%02d:00", (h + 23) % 24))
        end.setText(String.format("%02d:00", h))
        times.addView(start, LinearLayout.LayoutParams(0, -2, 1f))
        times.addView(space())
        times.addView(end, LinearLayout.LayoutParams(0, -2, 1f))
        wrap.addView(times)

        wrap.addView(label("O que você fez na última hora?"))
        note = EditText(this).apply {
            hint = "Escreva uma descrição simples do que aconteceu..."
            minLines = 5
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        wrap.addView(note)

        val save = Button(this).apply {
            text = "Salvar registro"
            setOnClickListener { saveEntry() }
        }
        wrap.addView(save)

        wrap.addView(label("REGISTROS DE HOJE"))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(list)
        refreshList()

        val close = Button(this).apply {
            text = "Fechar dia e gerar relatório"
            setOnClickListener { closeDay() }
        }
        wrap.addView(close)

        report = TextView(this).apply {
            textSize = 15f
            setPadding(0, 18, 0, 0)
        }
        wrap.addView(report)

        val alarm = Button(this).apply {
            text = "Ativar alarme de hora em hora"
            setOnClickListener { scheduleHourlyReminder(); Toast.makeText(this@MainActivity, "Alarme ativado", Toast.LENGTH_SHORT).show() }
        }
        wrap.addView(alarm)

        setContentView(root)
    }

    private fun label(s:String) = TextView(this).apply {
        text = s; textSize = 12f; setPadding(0, 20, 0, 8); setTextColor(0xFF66717D.toInt())
    }

    private fun timeField() = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_DATETIME
        hint = "00:00"
        setSingleLine()
    }
    private fun space() = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(16, 1)
    }

    private fun todayKey() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun load() {
        val raw = prefs.getString(todayKey(), "") ?: ""
        if (raw.isNotBlank()) raw.split("\n").forEach {
            val p = it.split("\t", limit = 3)
            if (p.size == 3) entries.add(Entry(p[0], p[1], p[2]))
        }
    }

    private fun persist() {
        prefs.edit().putString(todayKey(), entries.joinToString("\n") { "${it.start}\t${it.end}\t${it.text}" }).apply()
    }

    private fun saveEntry() {
        val text = note.text.toString().trim()
        if (text.isEmpty()) return
        entries.add(Entry(start.text.toString(), end.text.toString(), text))
        entries.sortBy { it.start }
        persist()
        note.setText("")
        refreshList()
        Toast.makeText(this, "Registro salvo", Toast.LENGTH_SHORT).show()
    }

    private fun refreshList() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        if (entries.isEmpty()) {
            list.addView(TextView(this).apply { text = "Nenhum registro feito hoje ainda."; setPadding(0, 8, 0, 8) })
        } else entries.forEach {
            list.addView(TextView(this).apply {
                text = "${it.start}–${it.end}  ${it.text}"
                textSize = 15f
                setPadding(0, 10, 0, 10)
            })
        }
    }

    private fun closeDay() {
        val sb = StringBuilder("RELATÓRIO DO DIA\n\n")
        if (entries.isEmpty()) sb.append("Nenhum registro foi feito neste dia.")
        else entries.forEach { sb.append("${it.start}–${it.end} — ${it.text}\n") }
        report.text = sb.toString()
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 10)
    }

    private fun scheduleHourlyReminder() {
        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(this, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val now = Calendar.getInstance()
        now.set(Calendar.MINUTE, 0); now.set(Calendar.SECOND, 0); now.set(Calendar.MILLISECOND, 0)
        now.add(Calendar.HOUR, 1)
        am.setRepeating(AlarmManager.RTC_WAKEUP, now.timeInMillis, 60*60*1000L, pi)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val channel = "hourly"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel(channel, "Diário de Horas", NotificationManager.IMPORTANCE_DEFAULT))
        val n = Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Diário de Horas")
            .setContentText("Hora de registrar: o que você fez na última hora?")
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis()/3600000).toInt(), n)
    }
}
