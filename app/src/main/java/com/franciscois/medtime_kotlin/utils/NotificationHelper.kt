package com.franciscois.medtime_kotlin.utils

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.franciscois.medtime_kotlin.AlarmActivity // Necesario para PendingIntent
import com.franciscois.medtime_kotlin.MainActivity
import com.franciscois.medtime_kotlin.R // Asegúrate de importar R
import com.franciscois.medtime_kotlin.storage.MedicationStorage
// import com.franciscois.medtime_kotlin.receivers.BootReceiver // Si lo tienes

class NotificationHelper(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)
    private var mediaPlayer: MediaPlayer? = null

    companion object {
        const val CHANNEL_NAME_ALARMS = "Alarmas de Medicamentos"
        const val CHANNEL_ID_ALARMS = "medication_alarms_channel"

        @Volatile
        private var INSTANCE: NotificationHelper? = null
        fun getInstance(context: Context): NotificationHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        crearCanalesNotificacion()
    }

    // Muestra solo la notificación (llamada desde el Receiver)
    fun mostrarAlarmaComoNotificacion(intent: Intent) {
        val medicamentoId = intent.getStringExtra("medicamento_id") ?: return
        val nombreMedicamento = intent.getStringExtra("medicamento_nombre") ?: "Medicamento"
        val notas = intent.getStringExtra("medicamento_notas") ?: ""
        val duracionSonido = intent.getIntExtra("medicamento_duracion_sonido", 1)
        val esPrueba = intent.getBooleanExtra("es_prueba", false)

        // Intent al tocar la notificación
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val reqCodeContent = medicamentoId.hashCode() + 4
        val contentPendingIntent = PendingIntent.getActivity(context, reqCodeContent, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Acciones
        val intentTomado = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "MARCAR_TOMADO"
            putExtra("medicamento_id", medicamentoId)
            putExtra("medicamento_nombre", nombreMedicamento)
        }
        val reqCodeTomado = medicamentoId.hashCode() + 2
        val pendingIntentTomado = PendingIntent.getBroadcast(context, reqCodeTomado, intentTomado, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val intentPosponer = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "POSPONER"
            putExtra("medicamento_id", medicamentoId)
            putExtra("medicamento_nombre", nombreMedicamento)
        }
        val reqCodePosponer = medicamentoId.hashCode() + 3
        val pendingIntentPosponer = PendingIntent.getBroadcast(context, reqCodePosponer, intentPosponer, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Construir notificación
        val titulo = if (esPrueba) "🧪 PRUEBA: $nombreMedicamento" else "💊 Hora de tomar: $nombreMedicamento"
        val mensaje = if (notas.isNotEmpty()) "📝 $notas" else "Es hora de tu medicamento"
        val iconTomadoStandard = android.R.drawable.checkbox_on_background

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALARMS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)
            // NO .setFullScreenIntent()
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Posponer", pendingIntentPosponer)
            .addAction(iconTomadoStandard, "✅ Tomado", pendingIntentTomado)
            .setAutoCancel(true)
            .build()

        // Mostrar notificación (con chequeo de permiso)
        val notificationId = 1001 + medicamentoId.hashCode()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(notificationId, notification)
                println("Notificación mostrada (Android 13+)")
            } else {
                println("Error: Permiso POST_NOTIFICATIONS denegado.")
            }
        } else {
            notificationManager.notify(notificationId, notification)
            println("Notificación mostrada (Pre-Android 13)")
        }

        // Reprogramar (si no es prueba)
        // El sonido lo maneja AlarmActivity
        if (!esPrueba) {
            reprogramarSiguiente(context, medicamentoId)
        } else {
            println("Reproduciendo sonido de PRUEBA desde NotificationHelper")
            reproducirSonidoAlarma(duracionSonido, true)
        }
    }

    // Reprogramación
    fun reprogramarSiguiente(context: Context, medicamentoId: String) {
        Handler(Looper.getMainLooper()).post {
            val storage = MedicationStorage.getInstance(context)
            val alarmHelper = AlarmHelper.getInstance(context)
            storage.buscarMedicamento(medicamentoId)?.let { medicamento ->
                if (medicamento.activo) {
                    val proximaAlarmaCalculada = medicamento.calcularProximaAlarma()
                    if (proximaAlarmaCalculada > System.currentTimeMillis() - (1 * 60 * 1000L)) {
                        val medicamentoActualizado = medicamento.copy(proximaAlarma = proximaAlarmaCalculada)
                        if(storage.actualizarMedicamento(medicamentoActualizado)) {
                            alarmHelper.programarAlarma(medicamentoActualizado)
                            println("Reprogramada siguiente alarma para $medicamentoId a las $proximaAlarmaCalculada")
                        } else {
                            println("Error al actualizar medicamento $medicamentoId para reprogramar")
                        }
                    } else {
                        println("Se evitó reprogramar alarma en el pasado para $medicamentoId (Próxima calculada: $proximaAlarmaCalculada)")
                    }
                } else {
                    println("Medicamento $medicamentoId inactivo, no se reprograma.")
                }
            } ?: println("No se encontró medicamento $medicamentoId para reprogramar")
        }
    }

    // Reproducción de sonido
    fun reproducirSonidoAlarma(duracionSonidoMinutos: Int, esPrueba: Boolean) {
        val sonidoUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
        if (sonidoUri == null) {
            println("❌ MediaPlayer: No se encontró sonido de alarma predeterminado.")
            return
        }
        try {
            detenerSonido()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, sonidoUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = !esPrueba
                prepareAsync()
                setOnPreparedListener { mp ->
                    try {
                        mp.start()
                        println("🔊 MediaPlayer: Reproduciendo sonido de alarma (Looping: ${!esPrueba})")
                        val duracionMs = (duracionSonidoMinutos * 60 * 1000L).coerceIn(1000L, 10 * 60 * 1000L)
                        val tiempoMaximoMs = if (esPrueba) 5000L else duracionMs
                        println("MediaPlayer: Sonido programado para detenerse en ${tiempoMaximoMs / 1000} segundos.")
                        Handler(Looper.getMainLooper()).postDelayed({
                            println("MediaPlayer: Tiempo máximo alcanzado, deteniendo sonido.")
                            detenerSonido()
                        }, tiempoMaximoMs)
                    } catch (e: Exception) {
                        println("❌ MediaPlayer: Error al iniciar después de preparar: ${e.message}")
                        detenerSonido()
                    }
                }
                setOnErrorListener { mp, what, extra ->
                    println("❌ MediaPlayer: Error durante reproducción (what:$what, extra:$extra)")
                    detenerSonido()
                    true
                }
                setOnCompletionListener {
                    if (!isLooping) {
                        println("MediaPlayer: Sonido completado (no looping).")
                        detenerSonido()
                    }
                }
            }
        } catch (e: Exception) {
            println("❌ MediaPlayer: Error crítico al configurar MediaPlayer: ${e.message}")
            detenerSonido()
        }
    }

    // Detener sonido
    fun detenerSonido() {
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) {
                    mp.stop()
                    println("⏹️ MediaPlayer: Sonido detenido.")
                }
                mp.reset()
                mp.release()
                println("MediaPlayer: Recursos liberados.")
            } catch (e: Exception) {
                println("⚠️ MediaPlayer: Error al detener/liberar: ${e.message}")
            } finally {
                mediaPlayer = null
            }
        }
    }

    // Crear canal
    private fun crearCanalesNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sonidoUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val canal = NotificationChannel(
                CHANNEL_ID_ALARMS,
                CHANNEL_NAME_ALARMS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarmas sonoras para recordar tomar medicamentos"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(sonidoUri, audioAttributes)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID_ALARMS) == null) {
                manager.createNotificationChannel(canal)
                println("✅ Canal de notificación '${CHANNEL_NAME_ALARMS}' creado.")
            } else {
                println("ⓘ Canal de notificación '${CHANNEL_NAME_ALARMS}' ya existe.")
            }
        }
    }

    // Cancelar notificación
    fun cancelarNotificacion(medicamentoId: String) {
        try {
            val notificationId = 1001 + medicamentoId.hashCode()
            notificationManager.cancel(notificationId)
            println("ⓘ Notificación cancelada (ID: $notificationId) para $medicamentoId")
        } catch (e: Exception) {
            println("❌ Error al cancelar notificación para $medicamentoId: ${e.message}")
        }
    }
}

// --- NotificationActionReceiver ---
class NotificationActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val medicamentoId = intent.getStringExtra("medicamento_id") ?: return
        val nombreMedicamento = intent.getStringExtra("medicamento_nombre") ?: "Medicamento"
        val notificationHelper = NotificationHelper.getInstance(context)

        println("🔔 NotificationActionReceiver: Acción recibida '${intent.action}' para $nombreMedicamento")

        notificationHelper.detenerSonido()
        notificationHelper.cancelarNotificacion(medicamentoId)

        when (intent.action) {
            "MARCAR_TOMADO" -> {
                Toast.makeText(context, "$nombreMedicamento marcado como tomado", Toast.LENGTH_SHORT).show()
                notificationHelper.reprogramarSiguiente(context, medicamentoId)
                println("✅ Acción 'Tomado' procesada para $nombreMedicamento")
            }
            "POSPONER" -> {
                val alarmHelper = AlarmHelper.getInstance(context)
                val storage = MedicationStorage.getInstance(context)
                storage.buscarMedicamento(medicamentoId)?.let { medicamento ->
                    alarmHelper.programarAlarmaPrueba(medicamento, 300) // 5 minutos
                }
                Toast.makeText(context, "$nombreMedicamento pospuesto 5 minutos", Toast.LENGTH_SHORT).show()
                println("⏰ Acción 'Posponer' procesada para $nombreMedicamento")
            }
            else -> {
                println("⚠️ Acción desconocida recibida: ${intent.action}")
            }
        }
        // --- INICIO DE LA MODIFICACIÓN ---
        // Eliminar el intento de cerrar la barra de notificaciones
        /*
         try {
             val it = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
             context.sendBroadcast(it)
         } catch (e: Exception) {
             println("⚠️ Error al intentar cerrar diálogos del sistema: ${e.message}")
         }
        */
        // --- FIN DE LA MODIFICACIÓN ---
    }
}