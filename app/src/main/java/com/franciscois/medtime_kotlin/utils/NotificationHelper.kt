package com.franciscois.medtime_kotlin.utils

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.franciscois.medtime_kotlin.MainActivity
import com.franciscois.medtime_kotlin.storage.MedicationStorage

class NotificationHelper(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)
    private var mediaPlayer: MediaPlayer? = null

    companion object {
        private const val CHANNEL_ID_ALARMS = "medication_alarms"
        private const val CHANNEL_ID_REMINDERS = "medication_reminders"
        private const val CHANNEL_ID_INFO = "medication_info"

        private const val NOTIFICATION_ID_ALARM = 1001
        private const val NOTIFICATION_ID_REMINDER = 1002
        private const val NOTIFICATION_ID_INFO = 1003

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

    private fun crearCanalesNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canales = listOf(
                // Canal para alarmas importantes (sonido alto, vibración)
                NotificationChannel(
                    CHANNEL_ID_ALARMS,
                    "Alarmas de Medicamentos",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alarmas sonoras para recordar tomar medicamentos"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                    setShowBadge(true)
                    lightColor = Color.BLUE
                    enableLights(true)
                },

                // Canal para recordatorios suaves
                NotificationChannel(
                    CHANNEL_ID_REMINDERS,
                    "Recordatorios",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Recordatorios suaves sobre medicamentos"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 200, 300)
                    setShowBadge(true)
                },

                // Canal para información general
                NotificationChannel(
                    CHANNEL_ID_INFO,
                    "Información",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Información general sobre la aplicación"
                    enableVibration(false)
                    setShowBadge(false)
                }
            )

            val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            canales.forEach { canal ->
                systemNotificationManager.createNotificationChannel(canal)
            }
        }
    }

    // Mostrar notificación de alarma con sonido
    fun mostrarNotificacionAlarma(nombreMedicamento: String, notas: String, medicamentoId: String, esPrueba: Boolean = false) {
        val titulo = if (esPrueba) "🧪 PRUEBA: $nombreMedicamento" else "💊 Hora de tomar: $nombreMedicamento"
        val mensaje = if (notas.isNotEmpty()) "📝 $notas" else "Es hora de tu medicamento"

        // Intent para abrir la app al tocar la notificación
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("medicamento_id", medicamentoId)
            putExtra("abrir_alarma", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            medicamentoId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent para marcar como tomado
        val intentTomado = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "MARCAR_TOMADO"
            putExtra("medicamento_id", medicamentoId)
            putExtra("medicamento_nombre", nombreMedicamento)
        }

        val pendingIntentTomado = PendingIntent.getBroadcast(
            context,
            "tomado_$medicamentoId".hashCode(),
            intentTomado,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent para posponer
        val intentPosponer = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "POSPONER"
            putExtra("medicamento_id", medicamentoId)
            putExtra("medicamento_nombre", nombreMedicamento)
        }

        val pendingIntentPosponer = PendingIntent.getBroadcast(
            context,
            "posponer_$medicamentoId".hashCode(),
            intentPosponer,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Sonido de alarma
        val sonidoAlarma = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

        val notificacion = NotificationCompat.Builder(context, CHANNEL_ID_ALARMS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensaje))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true) // Pantalla completa en algunos dispositivos
            .setSound(sonidoAlarma)
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .setLights(Color.BLUE, 1000, 500)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Posponer 10min",
                pendingIntentPosponer
            )
            .addAction(
                android.R.drawable.checkbox_on_background,
                "✅ Tomado",
                pendingIntentTomado
            )
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_ALARM + medicamentoId.hashCode(), notificacion)

            // Reproducir sonido adicional con MediaPlayer
            reproducirSonidoAlarma(sonidoAlarma, esPrueba)

            println("🔔 NotificationHelper: Notificación de alarma mostrada para $nombreMedicamento")

        } catch (e: SecurityException) {
            println("❌ NotificationHelper: Error de permisos mostrando notificación: ${e.message}")
        } catch (e: Exception) {
            println("❌ NotificationHelper: Error mostrando notificación: ${e.message}")
            e.printStackTrace()
        }
    }

    // Reproducir sonido adicional con MediaPlayer
    private fun reproducirSonidoAlarma(sonidoUri: android.net.Uri, esPrueba: Boolean) {
        try {
            // Detener sonido anterior si existe
            detenerSonido()

            mediaPlayer = MediaPlayer.create(context, sonidoUri)?.apply {
                isLooping = !esPrueba // Solo loop si no es prueba
                setOnErrorListener { _, _, _ ->
                    println("❌ MediaPlayer: Error reproduciendo sonido")
                    false
                }

                setOnCompletionListener {
                    if (!esPrueba && !isLooping) {
                        // Si no es prueba y no está en loop, reproducir una vez más
                        start()
                    }
                }

                start()
                println("🔊 MediaPlayer: Reproduciendo sonido de alarma")
            }

            // Detener automáticamente después de un tiempo
            val tiempoMaximo = if (esPrueba) 5000L else 30000L // 5s para prueba, 30s para real
            Handler(Looper.getMainLooper()).postDelayed({
                detenerSonido()
            }, tiempoMaximo)

        } catch (e: Exception) {
            println("❌ MediaPlayer: Error configurando sonido: ${e.message}")
        }
    }

    // Detener sonido de alarma
    fun detenerSonido() {
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) {
                    mp.stop()
                    println("⏹️ MediaPlayer: Sonido detenido")
                }
                mp.release()
            } catch (e: Exception) {
                println("❌ MediaPlayer: Error deteniendo sonido: ${e.message}")
            } finally {
                mediaPlayer = null
            }
        }
    }

    // Mostrar recordatorio suave
    fun mostrarRecordatorio(titulo: String, mensaje: String, medicamentoId: String? = null) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            medicamentoId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificacion = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_REMINDER + (medicamentoId?.hashCode() ?: 0), notificacion)
        } catch (e: SecurityException) {
            println("❌ NotificationHelper: Error de permisos mostrando recordatorio")
        }
    }

    // Mostrar información general
    fun mostrarInfo(titulo: String, mensaje: String) {
        val notificacion = NotificationCompat.Builder(context, CHANNEL_ID_INFO)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_INFO, notificacion)
        } catch (e: SecurityException) {
            println("❌ NotificationHelper: Error de permisos mostrando info")
        }
    }

    // Cancelar notificación específica
    fun cancelarNotificacion(medicamentoId: String) {
        notificationManager.cancel(NOTIFICATION_ID_ALARM + medicamentoId.hashCode())
    }

    // Cancelar todas las notificaciones
    fun cancelarTodasLasNotificaciones() {
        notificationManager.cancelAll()
        detenerSonido()
    }

    // Verificar si las notificaciones están habilitadas
    fun notificacionesHabilitadas(): Boolean {
        return notificationManager.areNotificationsEnabled()
    }
}

// Receiver para acciones de notificación (Tomado/Posponer)
class NotificationActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val medicamentoId = intent.getStringExtra("medicamento_id") ?: return
        val nombreMedicamento = intent.getStringExtra("medicamento_nombre") ?: "Medicamento"
        val action = intent.action

        val notificationHelper = NotificationHelper.getInstance(context)

        when (action) {
            "MARCAR_TOMADO" -> {
                // Cancelar notificación
                notificationHelper.cancelarNotificacion(medicamentoId)
                notificationHelper.detenerSonido()

                // Mostrar confirmación
                notificationHelper.mostrarInfo(
                    "✅ Medicamento tomado",
                    "$nombreMedicamento marcado como tomado"
                )

                println("✅ NotificationAction: $nombreMedicamento marcado como tomado")
            }

            "POSPONER" -> {
                // Cancelar notificación actual
                notificationHelper.cancelarNotificacion(medicamentoId)
                notificationHelper.detenerSonido()

                // Programar nueva alarma en 10 minutos
                val alarmHelper = AlarmHelper.getInstance(context)
                val storage = MedicationStorage.getInstance(context)

                storage.buscarMedicamento(medicamentoId)?.let { medicamento ->
                    // Crear medicamento temporal para alarma en 10 minutos
                    val medicamentoTemporal = medicamento.copy(
                        proximaAlarma = System.currentTimeMillis() + (10 * 60 * 1000)
                    )
                    alarmHelper.programarAlarmaPrueba(medicamentoTemporal, 600) // 10 minutos = 600 segundos
                }

                // Mostrar confirmación
                notificationHelper.mostrarInfo(
                    "⏰ Medicamento pospuesto",
                    "$nombreMedicamento pospuesto 10 minutos"
                )

                println("⏰ NotificationAction: $nombreMedicamento pospuesto 10 minutos")
            }
        }
    }
}