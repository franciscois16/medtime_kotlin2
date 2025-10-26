package com.franciscois.medtime_kotlin.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.franciscois.medtime_kotlin.AlarmActivity // Importar AlarmActivity
import com.franciscois.medtime_kotlin.utils.NotificationHelper

class MedicationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        // 1. Adquirir WakeLock para asegurar que el dispositivo esté despierto
        val wakeLock = acquireWakeLock(context)

        try {
            // Obtener datos del Intent
            val medicamentoId = intent.getStringExtra("medicamento_id") ?: return // Salir si no hay ID
            val nombreMedicamento = intent.getStringExtra("medicamento_nombre") ?: "Medicamento"
            val notas = intent.getStringExtra("medicamento_notas") ?: ""
            val duracionSonido = intent.getIntExtra("medicamento_duracion_sonido", 1)
            val esPrueba = intent.getBooleanExtra("es_prueba", false)

            println("🔔 MedicationAlarmReceiver: Alarma recibida para $nombreMedicamento")

            // --- INICIO DE LA MODIFICACIÓN ---
            // 2. Crear y lanzar DIRECTAMENTE la AlarmActivity
            val alarmActivityIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Iniciar como nueva tarea
                // Pasar los mismos extras que necesita AlarmActivity
                putExtra("medicamento_id", medicamentoId)
                putExtra("medicamento_nombre", nombreMedicamento)
                putExtra("medicamento_notas", notas) // Aunque no se use en el layout simple, pasarlo por si acaso
                putExtra("medicamento_duracion_sonido", duracionSonido)
            }
            context.startActivity(alarmActivityIntent)
            println("🚀 Lanzando AlarmActivity directamente para $nombreMedicamento")
            // --- FIN DE LA MODIFICACIÓN ---

            // 3. Mostrar la notificación (esto actuará como respaldo o si el usuario cierra la activity)
            // Ya no dependemos de su fullScreenIntent para abrir la actividad.
            val notificationHelper = NotificationHelper.getInstance(context)
            notificationHelper.mostrarAlarmaComoNotificacion(intent) // Usar una función específica para solo notificación

            // La reprogramación se maneja ahora desde NotificationHelper y AlarmActivity

        } catch (e: Exception) {
            println("❌ MedicationAlarmReceiver: Error al procesar la alarma - ${e.message}")
            e.printStackTrace()
        } finally {
            // 4. Liberar el WakeLock
            wakeLock?.let {
                if (it.isHeld) {
                    try {
                        it.release()
                        println("WakeLock liberado en Receiver")
                    } catch (e: Exception) {
                        println("⚠️ Error liberando WakeLock en Receiver: ${e.message}")
                    }
                }
            }
        }
    }

    private fun acquireWakeLock(context: Context): PowerManager.WakeLock? {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            // Usar PARTIAL_WAKE_LOCK es suficiente si solo queremos que la CPU corra,
            // pero ACQUIRE_CAUSES_WAKEUP es importante para asegurar que despierte.
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "MedTime::AlarmReceiverWakeLockTag" // Tag único
            ).apply {
                // Adquirir por un tiempo corto, solo para procesar el onReceive
                acquire(10 * 1000L) // 10 segundos
                println("WakeLock adquirido en Receiver")
            }
            wakeLock
        } catch (e: Exception) {
            println("❌ Error adquiriendo WakeLock en Receiver: ${e.message}")
            null
        }
    }
}