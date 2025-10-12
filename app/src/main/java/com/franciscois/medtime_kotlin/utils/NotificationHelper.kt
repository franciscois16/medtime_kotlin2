package com.franciscois.medtime_kotlin.utils

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.franciscois.medtime_kotlin.MainActivity
import com.franciscois.medtime_kotlin.storage.MedicationStorage

class NotificationHelper(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)
    private var mediaPlayer: MediaPlayer? = null

    companion object {
        private const val CHANNEL_ID_ALARMS = "medication_alarms"

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

    // Para la pantalla desbloqueada
    fun mostrarNotificacionAlarma(nombreMedicamento: String, notas: String, medicamentoId: String, duracionSonidoMinutos: Int, esPrueba: Boolean = false) {
        val titulo = if (esPrueba) "🧪 PRUEBA: $nombreMedicamento" else "💊 Hora de tomar: $nombreMedicamento"
        val mensaje = if (notas.isNotEmpty()) "📝 $notas" else "Es hora de tu medicamento"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("medicamento_id", medicamentoId)
        }
        val pendingIntent = PendingIntent.getActivity(context, medicamentoId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val intentTomado = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "MARCAR_TOMADO"
            putExtra("medicamento_id", medicamentoId)
            putExtra("medicamento_nombre", nombreMedicamento)
        }
        val pendingIntentTomado = PendingIntent.getBroadcast(context, "tomado_$medicamentoId".hashCode(), intentTomado, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val intentPosponer = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "POSPONER"
            putExtra("medicamento_id", medicamentoId)
            putExtra("medicamento_nombre", nombreMedicamento)
        }
        val pendingIntentPosponer = PendingIntent.getBroadcast(context, "posponer_$medicamentoId".hashCode(), intentPosponer, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notificacion = NotificationCompat.Builder(context, CHANNEL_ID_ALARMS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true) // Importante para que sea "Heads-Up"
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Posponer 10min", pendingIntentPosponer)
            .addAction(android.R.drawable.checkbox_on_background, "✅ Tomado", pendingIntentTomado)
            .build()

        notificationManager.notify(1001 + medicamentoId.hashCode(), notificacion)

        reproducirSonidoAlarma(duracionSonidoMinutos, esPrueba)
    }

    // Ahora es PÚBLICA y no necesita la URI
    fun reproducirSonidoAlarma(duracionSonidoMinutos: Int, esPrueba: Boolean) {
        val sonidoUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM) ?: return
        try {
            detenerSonido()
            mediaPlayer = MediaPlayer.create(context, sonidoUri)?.apply {
                isLooping = !esPrueba
                start()
            }

            val tiempoMaximo = if (esPrueba) 5000L else (duracionSonidoMinutos * 60 * 1000L)
            Handler(Looper.getMainLooper()).postDelayed({
                detenerSonido()
            }, tiempoMaximo)

        } catch (e: Exception) {
            println("❌ MediaPlayer: Error configurando sonido: ${e.message}")
        }
    }

    fun detenerSonido() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.stop()
            }
            mp.release()
        }
        mediaPlayer = null
    }

    private fun crearCanalesNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CHANNEL_ID_ALARMS,
                "Alarmas de Medicamentos",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarmas sonoras para recordar tomar medicamentos"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(canal)
        }
    }

    fun cancelarNotificacion(medicamentoId: String) {
        notificationManager.cancel(1001 + medicamentoId.hashCode())
    }
}

class NotificationActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val medicamentoId = intent.getStringExtra("medicamento_id") ?: return
        val nombreMedicamento = intent.getStringExtra("medicamento_nombre") ?: "Medicamento"
        val notificationHelper = NotificationHelper.getInstance(context)

        when (intent.action) {
            "MARCAR_TOMADO" -> {
                notificationHelper.cancelarNotificacion(medicamentoId)
                notificationHelper.detenerSonido()
                Toast.makeText(context, "$nombreMedicamento marcado como tomado", Toast.LENGTH_SHORT).show()
            }
            "POSPONER" -> {
                notificationHelper.cancelarNotificacion(medicamentoId)
                notificationHelper.detenerSonido()

                val alarmHelper = AlarmHelper.getInstance(context)
                val storage = MedicationStorage.getInstance(context)
                storage.buscarMedicamento(medicamentoId)?.let { medicamento ->
                    alarmHelper.programarAlarmaPrueba(medicamento, 600) // 10 minutos
                }
                Toast.makeText(context, "$nombreMedicamento pospuesto 10 minutos", Toast.LENGTH_SHORT).show()
            }
        }
    }
}