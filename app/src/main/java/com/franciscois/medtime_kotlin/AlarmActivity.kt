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
import android.widget.Button // Sigue siendo necesario para Posponer
import android.widget.ImageButton // Ahora usamos ImageButton para el slider
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

    // Vistas del nuevo layout
    private lateinit var timeTextView: TextView
    private lateinit var dateTextView: TextView
    private lateinit var nameTextView: TextView
    private lateinit var noteTextView: TextView // Para la nota
    private lateinit var buttonPosponer: Button
    private lateinit var sliderThumbButton: ImageButton // El botón que se desliza
    private lateinit var sliderTakenText: TextView // El texto "Desliza para Marcar..."
    private lateinit var sliderTakenContainer: View // El contenedor gris del slider

    // Variables para el slider
    private var initialX: Float = 0f
    private var sliderWidth: Int = 0
    private var thumbWidth: Int = 0
    private var isSliding = false
    // Variables de datos
    private var medId: String = ""
    private var medName: String = ""
    private var medNote: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        configurarPantallaBloqueo()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm)

        adquirirWakeLock()

        notificationHelper = NotificationHelper.getInstance(this)
        alarmHelper = AlarmHelper.getInstance(this)

        processIntent(intent)
        findViews()
        setupUI(medName, medNote)

        val duracionSonido = intent.getIntExtra("medicamento_duracion_sonido", 1)
        println("Reproduciendo sonido desde AlarmActivity onCreate")
        notificationHelper.reproducirSonidoAlarma(duracionSonido, false)

        // Configurar listeners
        if (::sliderThumbButton.isInitialized) {
            println("✅ Configurando listener del slider")
            sliderThumbButton.setOnTouchListener { view, event ->
                println("👆 Touch event recibido: ${event.action}")
                handleSliderTouch(view, event)
            }
        } else {
            println("❌ Error: sliderThumbButton NO inicializado")
        }

        if (::buttonPosponer.isInitialized) {
            buttonPosponer.setOnClickListener {
                posponerAlarma(medId, medName)
            }
        } else {
            println("❌ Error: buttonPosponer NO inicializado")
        }

        println("🎬 onCreate completado")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        println("AlarmActivity onNewIntent recibido")
        setIntent(intent)
        processIntent(intent) // Procesar nuevos datos
        setupUI(medName, medNote) // Actualizar UI
        notificationHelper.detenerSonido() // Detener sonido anterior
        val duracionSonido = intent.getIntExtra("medicamento_duracion_sonido", 1)
        notificationHelper.reproducirSonidoAlarma(duracionSonido, false) // Iniciar nuevo sonido
        if (::sliderThumbButton.isInitialized) {
            resetSlider(sliderThumbButton) // Reiniciar slider
        }
    }

    // Procesa los datos del Intent
    private fun processIntent(intent: Intent?) {
        medId = intent?.getStringExtra("medicamento_id") ?: medId
        medName = intent?.getStringExtra("medicamento_nombre") ?: medName
        medNote = intent?.getStringExtra("medicamento_notas") ?: medNote // Obtener nota
        println("Intent procesado: ID=$medId, Nombre=$medName")
    }

    // Enlaza las vistas del layout
    // Enlaza las vistas del layout
    private fun findViews() {
        try {
            timeTextView = findViewById(R.id.text_view_current_time)
            dateTextView = findViewById(R.id.text_view_current_date)
            nameTextView = findViewById(R.id.text_view_med_name)
            noteTextView = findViewById(R.id.text_view_med_note) // ← DESCOMENTADO
            buttonPosponer = findViewById(R.id.button_posponer)

            // Inicializar el slider
            sliderThumbButton = findViewById(R.id.slider_thumb_button) // ← DESCOMENTADO
            sliderTakenText = findViewById(R.id.slider_taken_text) // ← DESCOMENTADO
            sliderTakenContainer = findViewById(R.id.slider_taken_container) // ← DESCOMENTADO

            println("✅ Todas las vistas encontradas correctamente")

            // Debugging: Verificar inicialización después de encontrar las vistas
            println("🔍 Verificando vistas inicializadas:")
            println("  - sliderTakenContainer: ${::sliderTakenContainer.isInitialized}")
            println("  - sliderThumbButton: ${::sliderThumbButton.isInitialized}")
            println("  - sliderTakenText: ${::sliderTakenText.isInitialized}")

            // Esperar a que el layout se dibuje para obtener dimensiones
            sliderTakenContainer.post {
                sliderWidth = sliderTakenContainer.width
                thumbWidth = sliderThumbButton.width
                println("📏 Dimensiones del slider: container=$sliderWidth, thumb=$thumbWidth")
                if (sliderWidth <= thumbWidth) {
                    println("⚠️ ADVERTENCIA: El contenedor ($sliderWidth) es más pequeño o igual que el thumb ($thumbWidth)!")
                } else {
                    println("✅ Dimensiones del slider correctas. Se puede deslizar ${sliderWidth - thumbWidth}px")
                }
            }

        } catch (e: Exception) {
            println("❌ Error encontrando vistas: ${e.message}")
            e.printStackTrace()
            finish()
        }
    }

    // Configura los textos y visibilidad
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
        // Mostrar u ocultar la nota
        if (::noteTextView.isInitialized) {
            if (medNote.isNotEmpty()) {
                noteTextView.text = medNote
                noteTextView.visibility = View.VISIBLE
            } else {
                noteTextView.visibility = View.GONE
            }
        }
    }

    // --- LÓGICA DEL SLIDER ---
    // En handleSliderTouch()
    private fun handleSliderTouch(view: View, event: MotionEvent): Boolean {
        if (!::sliderTakenContainer.isInitialized || !::sliderThumbButton.isInitialized) {
            println("❌ Slider views no inicializadas")
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Esperar a que las vistas tengan dimensiones
                view.post {
                    sliderWidth = sliderTakenContainer.width
                    thumbWidth = view.width
                    println("📏 Slider width: $sliderWidth, Thumb width: $thumbWidth")

                    if (sliderWidth > thumbWidth && sliderWidth > 0) {
                        initialX = event.rawX - view.x
                        isSliding = true
                        println("✅ Slider iniciado en x: ${view.x}")
                        if (::sliderTakenText.isInitialized) {
                            sliderTakenText.animate().alpha(0f).setDuration(100).start()
                        }
                    } else {
                        println("⚠️ Dimensiones inválidas para slider")
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isSliding && sliderWidth > thumbWidth) {
                    var newX = event.rawX - initialX
                    val maxX = (sliderWidth - thumbWidth).toFloat()
                    newX = newX.coerceIn(0f, maxX)
                    view.x = newX
                    println("📍 Moviendo slider a x: $newX / $maxX")
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isSliding) {
                    val finalX = view.x
                    val maxX = (sliderWidth - thumbWidth).toFloat()
                    val threshold = maxX * 0.7f
                    println("🏁 Slider soltado en x: $finalX, threshold: $threshold")

                    if (finalX >= threshold) {
                        println("✅ Slider completado - Confirmando")
                        animateSliderToTaken(view)
                    } else {
                        println("↩️ Slider no completado - Regresando")
                        resetSlider(view)
                    }
                    isSliding = false
                }
                return true
            }
        }
        return false
    }

    private fun animateSliderToTaken(view: View) {
        if (sliderWidth <= thumbWidth) return
        val targetX = (sliderWidth - thumbWidth).toFloat()
        view.isEnabled = false // Deshabilitar
        if (::buttonPosponer.isInitialized) buttonPosponer.isEnabled = false

        ObjectAnimator.ofFloat(view, "x", targetX).apply {
            duration = 150 // Animación rápida
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Cambiar ícono a check al final
                    sliderThumbButton.setImageResource(R.drawable.ic_check_white_24dp)
                    // Esperar un instante antes de cerrar
                    Handler(Looper.getMainLooper()).postDelayed({
                        marcarComoTomado(medId, medName)
                    }, 200) // 200ms de espera
                }
            })
            start()
        }
    }

    private fun resetSlider(view: View) {
        // Volver ícono a check (o el inicial que prefieras)
        sliderThumbButton.setImageResource(R.drawable.ic_check_white_24dp) // O podrías usar otro ícono aquí
        ObjectAnimator.ofFloat(view, "x", 0f).apply {
            duration = 150
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (::sliderTakenText.isInitialized) sliderTakenText.animate().alpha(1f).setDuration(100).start()
                    view.isEnabled = true // Habilitar de nuevo
                    if (::buttonPosponer.isInitialized) buttonPosponer.isEnabled = true
                }
            })
            start()
        }
    }
    // --- FIN LÓGICA DEL SLIDER ---


    // Configura flags de ventana (SIN dismissKeyguard)
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

    // Adquiere WakeLock
    private fun adquirirWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "MedTime::AlarmWakeLockTag"
            ).apply {
                acquire(1 * 60 * 1000L) // 1 minuto
                println("WakeLock adquirido en AlarmActivity")
            }
        } catch (e: Exception) {
            println("❌ Error adquiriendo WakeLock en AlarmActivity: ${e.message}")
            wakeLock = null
        }
    }

    // Marca como tomado
    private fun marcarComoTomado(medicamentoId: String, nombreMedicamento: String) {
        println("Slider completado -> Marcando como Tomado")
        notificationHelper.detenerSonido()
        notificationHelper.cancelarNotificacion(medicamentoId)
        notificationHelper.reprogramarSiguiente(this, medicamentoId)
        Toast.makeText(this, "'$nombreMedicamento' tomado", Toast.LENGTH_SHORT).show()
        if (!isFinishing && !isDestroyed) {
            try { finishAndRemoveTask() } catch (e: Exception) { finish() }
        }
    }

    // Posponer alarma
    private fun posponerAlarma(medicamentoId: String, nombreMedicamento: String) {
        println("Botón 'Posponer' presionado")
        notificationHelper.detenerSonido()
        notificationHelper.cancelarNotificacion(medicamentoId)
        val storage = MedicationStorage.getInstance(this)
        storage.buscarMedicamento(medicamentoId)?.let { medicamento ->
            alarmHelper.programarAlarmaPrueba(medicamento, 300) // 5 minutos
            Toast.makeText(this, "'$nombreMedicamento' pospuesto 5 minutos", Toast.LENGTH_SHORT).show()
        }
        if (!isFinishing && !isDestroyed) {
            try { finishAndRemoveTask() } catch (e: Exception) { finish() }
        }
    }

    // Liberar recursos al destruir
    override fun onDestroy() {
        super.onDestroy()
        println("AlarmActivity onDestroy")
        notificationHelper.detenerSonido()
        wakeLock?.let {
            if (it.isHeld) {
                try {
                    it.release()
                    println("WakeLock liberado en AlarmActivity")
                } catch (e: Exception) {
                    println("⚠️ Error liberando WakeLock en AlarmActivity: ${e.message}")
                }
            }
        }
        wakeLock = null
    }
}