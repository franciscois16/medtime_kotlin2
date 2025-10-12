package com.franciscois.medtime_kotlin

import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.franciscois.medtime_kotlin.storage.MedicationStorage
import com.franciscois.medtime_kotlin.utils.AlarmHelper
import com.franciscois.medtime_kotlin.utils.NotificationHelper
import com.google.android.material.slider.Slider
import java.text.SimpleDateFormat
import java.util.*

class AlarmActivity : AppCompatActivity() {

    private lateinit var notificationHelper: NotificationHelper
    private var wakeLock: PowerManager.WakeLock? = null // Para mantener la pantalla encendida

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm)

        // --- INICIO DE CÓDIGO NUEVO ---
        // 1. Forzar que la pantalla se encienda y se mantenga encendida
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 2. Adquirir el WakeLock para despertar el dispositivo
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "MedTime::AlarmWakeLock"
        )
        wakeLock?.acquire(10*60*1000L /* 10 minutes timeout */)
        // --- FIN DE CÓDIGO NUEVO ---

        notificationHelper = NotificationHelper.getInstance(this)

        val medId = intent.getStringExtra("medicamento_id") ?: ""
        val medName = intent.getStringExtra("medicamento_nombre") ?: "Medicamento"
        val medNotes = intent.getStringExtra("medicamento_notas") ?: ""
        val duracionSonido = intent.getIntExtra("medicamento_duracion_sonido", 1)

        val timeTextView: TextView = findViewById(R.id.text_view_current_time)
        val nameTextView: TextView = findViewById(R.id.text_view_med_name)
        val notesTextView: TextView = findViewById(R.id.text_view_med_notes)
        val slider: Slider = findViewById(R.id.slider_tomado)

        val formatoHora = SimpleDateFormat("HH:mm", Locale.getDefault())
        timeTextView.text = formatoHora.format(Date())
        nameTextView.text = medName
        notesTextView.text = medNotes

        notificationHelper.reproducirSonidoAlarma(duracionSonido, false)

        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser && value >= 0.9f) {
                marcarComoTomado(medId, medName)
            }
        }
    }

    private fun marcarComoTomado(medicamentoId: String, nombreMedicamento: String) {
        notificationHelper.detenerSonido()

        // Reprogramamos la siguiente alarma
        val storage = MedicationStorage.getInstance(this)
        val alarmHelper = AlarmHelper.getInstance(this)
        storage.buscarMedicamento(medicamentoId)?.let { medicamento ->
            val proximaAlarmaCalculada = medicamento.calcularProximaAlarma()
            val medicamentoActualizado = medicamento.copy(proximaAlarma = proximaAlarmaCalculada)
            storage.actualizarMedicamento(medicamentoActualizado)
            alarmHelper.programarAlarma(medicamentoActualizado)
        }

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 3. Liberar el WakeLock y detener el sonido para no gastar batería
        notificationHelper.detenerSonido()
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }
}