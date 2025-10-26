package com.franciscois.medtime_kotlin

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.franciscois.medtime_kotlin.storage.MedicationStorage
import com.franciscois.medtime_kotlin.utils.AlarmHelper
import com.franciscois.medtime_kotlin.utils.NotificationHelper
import java.text.SimpleDateFormat
import java.util.*

class AlarmActivity : AppCompatActivity() {

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var alarmHelper: AlarmHelper
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Configurar flags ANTES de llamar a super.onCreate puede ser necesario en algunos casos
        configurarPantallaBloqueo()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm)

        // Adquirir WakeLock
        adquirirWakeLock()

        notificationHelper = NotificationHelper.getInstance(this)
        alarmHelper = AlarmHelper.getInstance(this)

        val medId = intent.getStringExtra("medicamento_id") ?: ""
        val medName = intent.getStringExtra("medicamento_nombre") ?: "Medicamento"
        val duracionSonido = intent.getIntExtra("medicamento_duracion_sonido", 1)

        val timeTextView: TextView = findViewById(R.id.text_view_current_time)
        val dateTextView: TextView = findViewById(R.id.text_view_current_date)
        val nameTextView: TextView = findViewById(R.id.text_view_med_name)
        val buttonTomado: Button = findViewById(R.id.button_tomado)
        val buttonPosponer: Button = findViewById(R.id.button_posponer)

        val formatoHora = SimpleDateFormat("HH:mm", Locale.getDefault())
        timeTextView.text = formatoHora.format(Date())
        val formatoFecha = SimpleDateFormat("EEE, MMMM dd", Locale("es", "ES"))
        dateTextView.text = formatoFecha.format(Date())
        nameTextView.text = medName

        // Iniciar el sonido desde AlarmActivity SIEMPRE que se muestre
        println("Reproduciendo sonido desde AlarmActivity")
        notificationHelper.reproducirSonidoAlarma(duracionSonido, false) // 'false' porque no es prueba

        buttonTomado.setOnClickListener {
            marcarComoTomado(medId, medName)
        }
        buttonPosponer.setOnClickListener {
            posponerAlarma(medId, medName)
        }
    }

    private fun configurarPantallaBloqueo() {
        // Intentar mostrar sobre la pantalla de bloqueo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true) // Encender pantalla
            // Intentar descartar el Keyguard si es posible
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                keyguardManager.requestDismissKeyguard(this, null)
            }
        } else {
            // Flags deprecadas para versiones anteriores
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or // <-- CORREGIDO AQUÍ
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        // Asegurar que la pantalla se mantenga encendida mientras la actividad esté visible
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }


    private fun adquirirWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            // Usar SCREEN_BRIGHT_WAKE_LOCK para encender pantalla + ACQUIRE_CAUSES_WAKEUP para forzar despertar
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "MedTime::AlarmWakeLockTag" // Usar un Tag único
            ).apply {
                // Adquirir con timeout por seguridad (ej. 1 minuto)
                acquire(1 * 60 * 1000L)
                println("WakeLock adquirido")
            }
        } catch (e: Exception) {
            println("❌ Error adquiriendo WakeLock: ${e.message}")
            wakeLock = null // Asegurar que sea null si falla
        }
    }


    private fun marcarComoTomado(medicamentoId: String, nombreMedicamento: String) {
        println("Botón 'Tomado' presionado")
        notificationHelper.detenerSonido()
        // Reprogramar la siguiente alarma
        notificationHelper.reprogramarSiguiente(this, medicamentoId) // Usar helper para consistencia
        Toast.makeText(this, "'$nombreMedicamento' tomado", Toast.LENGTH_SHORT).show()
        // Cerrar la actividad de forma segura
        if (!isFinishing && !isDestroyed) {
            finishAndRemoveTask()
        }
    }

    private fun posponerAlarma(medicamentoId: String, nombreMedicamento: String) {
        println("Botón 'Posponer' presionado")
        notificationHelper.detenerSonido()
        val storage = MedicationStorage.getInstance(this)
        storage.buscarMedicamento(medicamentoId)?.let { medicamento ->
            // Usar AlarmHelper para posponer consistentemente
            alarmHelper.programarAlarmaPrueba(medicamento, 300) // 5 minutos
            Toast.makeText(this, "'$nombreMedicamento' pospuesto 5 minutos", Toast.LENGTH_SHORT).show()
        }
        // Cerrar la actividad de forma segura
        if (!isFinishing && !isDestroyed) {
            finishAndRemoveTask()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        println("AlarmActivity onDestroy")
        // Es CRUCIAL detener el sonido y liberar el WakeLock aquí
        notificationHelper.detenerSonido()
        wakeLock?.let {
            if (it.isHeld) {
                try {
                    it.release()
                    println("WakeLock liberado")
                } catch (e: Exception) {
                    println("⚠️ Error liberando WakeLock: ${e.message}")
                }
            }
        }
        wakeLock = null // Poner a null después de liberar
    }

}