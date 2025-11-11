package com.franciscois.medtime_kotlin

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.franciscois.medtime_kotlin.storage.MedicationStorage
import com.franciscois.medtime_kotlin.utils.AlarmHelper
import com.franciscois.medtime_kotlin.utils.NotificationHelper
import java.text.SimpleDateFormat
import java.util.*

class AlarmActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var alarmHelper: AlarmHelper
    private var wakeLock: PowerManager.WakeLock? = null

    // Vistas
    private lateinit var timeTextView: TextView
    private lateinit var dateTextView: TextView
    private lateinit var nameTextView: TextView
    private lateinit var noteTextView: TextView
    private lateinit var buttonPosponer: Button
    private lateinit var sliderThumbButton: ImageButton
    private lateinit var sliderTakenText: TextView
    private lateinit var sliderTakenContainer: View

    // Slider
    private var initialX: Float = 0f
    private var sliderWidth: Int = 0
    private var thumbWidth: Int = 0
    private var isSliding = false
    // Datos
    private var medId: String = ""
    private var medName: String = ""
    private var medNote: String = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        configurarPantallaBloqueo()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm) // Asegúrate que este sea el layout del slider

        db = Firebase.firestore
        adquirirWakeLock()
        notificationHelper = NotificationHelper.getInstance(this)
        alarmHelper = AlarmHelper.getInstance(this)
        processIntent(intent)
        findViews()
        setupUI(medName, medNote)

        val duracionSonido = intent.getIntExtra("medicamento_duracion_sonido", 1)
        notificationHelper.reproducirSonidoAlarma(duracionSonido, false)

        if (::sliderThumbButton.isInitialized) {
            sliderThumbButton.setOnTouchListener { view, event ->
                handleSliderTouch(view, event)
            }
        }
        if (::buttonPosponer.isInitialized) {
            buttonPosponer.setOnClickListener {
                // "Posponer" envía la alerta de OMITIDO
                enviarAlertaACuidadores(medId, medName, medNote, "OMITIDO")
            }
        }
    }

    // Envía la alerta a Firestore
    private fun enviarAlertaACuidadores(medicamentoId: String, nombreMedicamento: String, notasMedicamento: String, tipo: String) {
        println("Creando alerta en Firestore (tipo: $tipo)...")

        val prefs = getSharedPreferences("MedTimePrefs", Context.MODE_PRIVATE)
        val pacienteUid = prefs.getString("USER_UID", null)
        val pacienteName = prefs.getString("USER_NAME", "El paciente") ?: "El paciente"

        if (pacienteUid == null) {
            Toast.makeText(this, "Error: No se pudo identificar al paciente.", Toast.LENGTH_SHORT).show()
            // Continuar con la acción local aunque falle
            if (tipo == "OMITIDO") posponerAlarma(medicamentoId, nombreMedicamento)
            else cerrarActividad(medicamentoId, nombreMedicamento, true)
            return
        }

        Toast.makeText(this, "Enviando estado a cuidadores...", Toast.LENGTH_SHORT).show()

        val formatoHora = SimpleDateFormat("HH:mm", Locale.getDefault())
        val horaActual = formatoHora.format(Date())

        val alerta = hashMapOf(
            "pacienteUid" to pacienteUid,
            "pacienteName" to pacienteName,
            "medicamentoName" to nombreMedicamento,
            "medicamentoNotas" to notasMedicamento,
            "horaEvento" to horaActual,
            "tipo" to tipo, // "OMITIDO" o "TOMADO"
            "createdAt" to System.currentTimeMillis(),
            "vistaPorCuidador" to false
        )

        db.collection("alertas")
            .add(alerta)
            .addOnSuccessListener {
                println("✅ Alerta ($tipo) creada exitosamente en Firestore.")
                // Continuar con la acción local DESPUÉS de enviar
                if (tipo == "OMITIDO") {
                    posponerAlarma(medicamentoId, nombreMedicamento)
                } else {
                    cerrarActividad(medicamentoId, nombreMedicamento, true)
                }
            }
            .addOnFailureListener { e ->
                println("❌ Error al crear alerta en Firestore: $e")
                // Continuar con la acción local AUNQUE falle
                if (tipo == "OMITIDO") {
                    posponerAlarma(medicamentoId, nombreMedicamento)
                } else {
                    cerrarActividad(medicamentoId, nombreMedicamento, true)
                }
            }
    }

    // Se activa con el slider
    private fun marcarComoTomado(medicamentoId: String, nombreMedicamento: String) {
        println("Slider completado -> Marcando como Tomado")
        // Enviar alerta de "TOMADO"
        enviarAlertaACuidadores(medicamentoId, nombreMedicamento, medNote, "TOMADO")
    }

    // Cierra la actividad y (opcionalmente) reprograma
    private fun cerrarActividad(medicamentoId: String, nombreMedicamento: String, fueTomado: Boolean) {
        notificationHelper.detenerSonido()
        notificationHelper.cancelarNotificacion(medicamentoId)

        if (fueTomado) {
            notificationHelper.reprogramarSiguiente(this, medicamentoId)
            Toast.makeText(this, "'$nombreMedicamento' tomado", Toast.LENGTH_SHORT).show()
        }

        if (!isFinishing && !isDestroyed) {
            try { finishAndRemoveTask() } catch (e: Exception) { finish() }
        }
    }

    // Solo pospone localmente
    private fun posponerAlarma(medicamentoId: String, nombreMedicamento: String) {
        println("Lógica de 'Posponer' ejecutándose...")
        val storage = MedicationStorage.getInstance(this)
        storage.buscarMedicamento(medicamentoId)?.let { medicamento ->
            alarmHelper.programarAlarmaPrueba(medicamento, 300) // 5 minutos
            Toast.makeText(this, "'$nombreMedicamento' pospuesto 5 minutos", Toast.LENGTH_SHORT).show()
        }
        // Llamar a cerrar (sin marcar como tomado)
        cerrarActividad(medicamentoId, nombreMedicamento, false)
    }

    // (El resto de AlarmActivity.kt no cambia)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        println("AlarmActivity onNewIntent recibido")
        setIntent(intent)
        processIntent(intent)
        setupUI(medName, medNote)
        notificationHelper.detenerSonido()
        val duracionSonido = intent.getIntExtra("medicamento_duracion_sonido", 1)
        notificationHelper.reproducirSonidoAlarma(duracionSonido, false)
        if (::sliderThumbButton.isInitialized) {
            resetSlider(sliderThumbButton)
        }
    }

    private fun processIntent(intent: Intent?) {
        medId = intent?.getStringExtra("medicamento_id") ?: medId
        medName = intent?.getStringExtra("medicamento_nombre") ?: medName
        medNote = intent?.getStringExtra("medicamento_notas") ?: medNote
        println("Intent procesado: ID=$medId, Nombre=$medName")
    }

    private fun findViews() {
        try {
            timeTextView = findViewById(R.id.text_view_current_time)
            dateTextView = findViewById(R.id.text_view_current_date)
            nameTextView = findViewById(R.id.text_view_med_name)
            noteTextView = findViewById(R.id.text_view_med_note)
            buttonPosponer = findViewById(R.id.button_posponer)
            sliderThumbButton = findViewById(R.id.slider_thumb_button)
            sliderTakenText = findViewById(R.id.slider_taken_text)
            sliderTakenContainer = findViewById(R.id.slider_taken_container)
        } catch (e: Exception) {
            println("Error encontrando vistas en AlarmActivity: ${e.message}.")
            finish()
        }
    }

    private fun setupUI(medName: String, medNote: String) {
        if (::timeTextView.isInitialized) {
            val formatoHora = SimpleDateFormat("HH:mm", Locale.getDefault())
            timeTextView.text = formatoHora.format(Date())
        }
        if (::dateTextView.isInitialized) {
            val formatoFecha = SimpleDateFormat("EEE, MMMM dd", Locale("es", "ES"))
            dateTextView.text = formatoFecha.format(Date())
        }
        if (::nameTextView.isInitialized) {
            nameTextView.text = medName
        }
        if (::noteTextView.isInitialized) {
            if (medNote.isNotEmpty()) {
                noteTextView.text = medNote
                noteTextView.visibility = View.VISIBLE
            } else {
                noteTextView.visibility = View.GONE
            }
        }
    }

    private fun handleSliderTouch(view: View, event: MotionEvent): Boolean {
        if (!::sliderTakenContainer.isInitialized || !::sliderThumbButton.isInitialized) return false
        if (sliderWidth == 0) {
            sliderWidth = sliderTakenContainer.width
            thumbWidth = view.width
            if (sliderWidth <= 0 || thumbWidth <= 0) {
                println("Slider dimensions not ready.")
                return false
            }
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (sliderWidth > thumbWidth) {
                    initialX = event.rawX - view.x
                    isSliding = true
                    if (::sliderTakenText.isInitialized) sliderTakenText.animate().alpha(0f).setDuration(100).start()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isSliding) {
                    var newX = event.rawX - initialX
                    val maxX = (sliderWidth - thumbWidth).toFloat()
                    newX = newX.coerceIn(0f, maxX)
                    view.x = newX
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isSliding) {
                    val finalX = view.x
                    val threshold = (sliderWidth - thumbWidth) * 0.7f
                    if (finalX >= threshold) {
                        animateSliderToTaken(view)
                    } else {
                        resetSlider(view)
                    }
                    isSliding = false
                }
            }
        }
        return true
    }

    private fun animateSliderToTaken(view: View) {
        if (sliderWidth <= thumbWidth) return
        val targetX = (sliderWidth - thumbWidth).toFloat()
        view.isEnabled = false
        if (::buttonPosponer.isInitialized) buttonPosponer.isEnabled = false
        ObjectAnimator.ofFloat(view, "x", targetX).apply {
            duration = 150
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    sliderThumbButton.setImageResource(R.drawable.ic_check_white_24dp)
                    Handler(Looper.getMainLooper()).postDelayed({
                        marcarComoTomado(medId, medName)
                    }, 200)
                }
            })
            start()
        }
    }

    private fun resetSlider(view: View) {
        sliderThumbButton.setImageResource(R.drawable.ic_check_white_24dp)
        ObjectAnimator.ofFloat(view, "x", 0f).apply {
            duration = 150
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (::sliderTakenText.isInitialized) sliderTakenText.animate().alpha(1f).setDuration(100).start()
                    view.isEnabled = true
                    if (::buttonPosponer.isInitialized) buttonPosponer.isEnabled = true
                }
            })
            start()
        }
    }

    private fun configurarPantallaBloqueo() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            println("Flags de ventana configuradas para pantalla de bloqueo (SIN dismiss keyguard).")
        } catch (e: Exception) {
            println("❌ Error configurando flags de ventana: ${e.message}")
        }
    }

    private fun adquirirWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "MedTime::AlarmWakeLockTag"
            ).apply {
                acquire(1 * 60 * 1000L)
                println("WakeLock adquirido en AlarmActivity")
            }
        } catch (e: Exception) {
            println("❌ Error adquiriendo WakeLock en AlarmActivity: ${e.message}")
            wakeLock = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        println("AlarmActivity onDestroy")
        notificationHelper.detenerSonido()
        wakeLock?.let {
            if (it.isHeld) {
                try { it.release() } catch (e: Exception) { /*... */ }
            }
        }
        wakeLock = null
    }
}