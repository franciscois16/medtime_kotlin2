package com.franciscois.medtime_kotlin.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
// Asegúrate de tener este import explícito
import android.app.Service
import androidx.core.app.NotificationCompat
import com.franciscois.medtime_kotlin.AlarmActivity
import com.franciscois.medtime_kotlin.MainActivity
import com.franciscois.medtime_kotlin.R

class AlarmService : Service() {

    companion object {
        const val ACTION_START = "com.franciscois.medtime_kotlin.ACTION_START"
        const val EXTRA_MED_ID = "medicamento_id"
        const val EXTRA_MED_NAME = "medicamento_nombre"
        const val EXTRA_MED_NOTES = "medicamento_notas"
        const val EXTRA_MED_DURATION = "medicamento_duracion_sonido"
        const val SERVICE_NOTIFICATION_ID = 99
        const val SERVICE_CHANNEL_ID = "alarm_service_channel"
        const val SERVICE_CHANNEL_NAME = "Servicio de Alarma MedTime"
    }

    private var serviceWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        println("AlarmService: onCreate")
        createServiceNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        println("AlarmService: onStartCommand, Action: ${intent?.action}")

        if (intent?.action == ACTION_START) {
            acquireWakeLock()

            val medId = intent.getStringExtra(EXTRA_MED_ID) ?: ""
            val medName = intent.getStringExtra(EXTRA_MED_NAME) ?: "Medicamento"
            val medNotes = intent.getStringExtra(EXTRA_MED_NOTES) ?: ""
            val medDuration = intent.getIntExtra(EXTRA_MED_DURATION, 1)

            if (medId.isEmpty()) {
                println("❌ AlarmService: Error - No se recibió medicamento_id. Deteniendo servicio.")
                releaseWakeLock()
                stopSelf()
                // --- CORRECCIÓN AQUÍ ---
                return Service.START_NOT_STICKY // Usar Service.
            }

            println("AlarmService: Procesando alarma para $medName (ID: $medId)")

            try {
                startForeground(SERVICE_NOTIFICATION_ID, createServiceNotification())
                println("AlarmService: Iniciado en primer plano.")
            } catch (e: Exception) {
                println("❌ AlarmService: Error al iniciar en primer plano: ${e.message}")
            }

            val alarmActivityIntent = Intent(this, AlarmActivity::class.java).apply {
                // Las flags son correctas, el error 'val cannot be reassigned' no debería aplicar aquí.
                this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
                putExtra("medicamento_id", medId)
                putExtra("medicamento_nombre", medName)
                putExtra("medicamento_notas", medNotes)
                putExtra("medicamento_duracion_sonido", medDuration)
            }
            try {
                Handler(Looper.getMainLooper()).post {
                    try {
                        startActivity(alarmActivityIntent)
                        println("AlarmService: AlarmActivity lanzada para $medName")
                        Handler(Looper.getMainLooper()).postDelayed({ stopSelfIfAppropriate(startId) }, 5000)
                    } catch (e: SecurityException) {
                        println("❌ AlarmService: SecurityException al lanzar AlarmActivity: ${e.message}")
                        stopSelfIfAppropriate(startId)
                    } catch (e: Exception) {
                        println("❌ AlarmService: Error general al lanzar AlarmActivity: ${e.message}")
                        stopSelfIfAppropriate(startId)
                    }
                }
            } catch (e: Exception) {
                println("❌ AlarmService: Error al postear el lanzamiento de AlarmActivity: ${e.message}")
                stopSelfIfAppropriate(startId)
            }

            releaseWakeLock()

        } else {
            println("AlarmService: Acción desconocida o nula. Deteniendo servicio.")
            releaseWakeLock()
            stopSelf()
        }

        // --- CORRECCIÓN AQUÍ ---
        // Devolver constante válida de Service.
        return Service.START_NOT_STICKY
    }

    private fun stopSelfIfAppropriate(startId: Int) {
        println("AlarmService: Intentando detenerse (startId: $startId)")
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        println("AlarmService: onDestroy")
        releaseWakeLock()
    }

    private fun createServiceNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle("MedTime")
            .setContentText("Gestionando recordatorio...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    private fun createServiceNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                SERVICE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación persistente mientras MedTime maneja una alarma."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel) ?: println("Error: NotificationManager no disponible")
        }
    }

    private fun acquireWakeLock() {
        try {
            if (serviceWakeLock == null || serviceWakeLock?.isHeld == false) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                serviceWakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "MedTime::AlarmServiceWakeLockTag"
                ).apply {
                    acquire(30 * 1000L)
                    println("WakeLock adquirido en AlarmService")
                }
            }
        } catch (e: Exception) {
            println("❌ Error adquiriendo WakeLock en AlarmService: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        serviceWakeLock?.let {
            if (it.isHeld) {
                try {
                    it.release()
                    println("WakeLock liberado en AlarmService")
                } catch (e: Exception) {
                    println("⚠️ Error liberando WakeLock en AlarmService: ${e.message}")
                }
            }
        }
        serviceWakeLock = null
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}