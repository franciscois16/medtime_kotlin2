package com.franciscois.medtime_kotlin.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.franciscois.medtime_kotlin.models.Medicamento
import com.franciscois.medtime_kotlin.storage.MedicationStorage
import com.franciscois.medtime_kotlin.utils.AlarmHelper
import com.franciscois.medtime_kotlin.utils.NotificationHelper

class MedicationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        // Obtener datos del intent
        val medicamentoId = intent.getStringExtra("medicamento_id") ?: return
        val nombreMedicamento = intent.getStringExtra("medicamento_nombre") ?: "Medicamento"
        val notas = intent.getStringExtra("medicamento_notas") ?: ""
        val familiares = intent.getStringArrayExtra("medicamento_familiares")?.toList() ?: emptyList()
        val esPrueba = intent.getBooleanExtra("es_prueba", false)

        println("🔔 MedicationAlarmReceiver: Alarma recibida para $nombreMedicamento")

        try {
            // Mostrar notificación con sonido
            val notificationHelper = NotificationHelper.getInstance(context)
            notificationHelper.mostrarNotificacionAlarma(
                nombreMedicamento,
                notas,
                medicamentoId,
                esPrueba
            )

            // Si no es prueba, actualizar el medicamento y programar siguiente alarma
            if (!esPrueba) {
                procesarAlarmaReal(context, medicamentoId, familiares)
            }

            println("✅ MedicationAlarmReceiver: Alarma procesada exitosamente")

        } catch (e: Exception) {
            println("❌ MedicationAlarmReceiver: Error procesando alarma - ${e.message}")
            e.printStackTrace()
        }
    }

    private fun procesarAlarmaReal(context: Context, medicamentoId: String, familiares: List<String>) {
        val storage = MedicationStorage.getInstance(context)
        val alarmHelper = AlarmHelper.getInstance(context)

        // Buscar el medicamento
        val medicamento = storage.buscarMedicamento(medicamentoId)
        if (medicamento == null) {
            println("⚠️ MedicationAlarmReceiver: Medicamento no encontrado: $medicamentoId")
            return
        }

        // Actualizar próxima alarma del medicamento
        val medicamentoActualizado = medicamento.copy(
            proximaAlarma = medicamento.calcularProximaAlarma()
        )

        // Guardar cambios
        storage.actualizarMedicamento(medicamentoActualizado)

        // Programar siguiente alarma
        Handler(Looper.getMainLooper()).post {
            alarmHelper.programarAlarma(medicamentoActualizado)
        }

        // Notificar a familiares (simulado por ahora)
        if (familiares.isNotEmpty()) {
            notificarFamiliares(context, medicamento, familiares)
        }

        // Log para debugging
        println("📅 Próxima alarma programada para ${medicamento.nombre} a las ${medicamentoActualizado.proximaAlarma}")
    }

    private fun notificarFamiliares(context: Context, medicamento: Medicamento, familiares: List<String>) {
        // Por ahora solo log - aquí se implementaría SMS, email, etc.
        println("👨‍👩‍👧‍👦 Notificando a familiares sobre ${medicamento.nombre}:")
        familiares.forEach { familiar ->
            println("   📧 Enviando a: $familiar")
            // TODO: Implementar envío real
            when {
                familiar.contains("@") -> {
                    // Enviar email
                    enviarEmail(context, familiar, medicamento)
                }
                familiar.startsWith("+") || familiar.all { it.isDigit() } -> {
                    // Enviar SMS
                    enviarSMS(context, familiar, medicamento)
                }
                else -> {
                    println("   ⚠️ Formato de contacto no reconocido: $familiar")
                }
            }
        }
    }

    private fun enviarEmail(context: Context, email: String, medicamento: Medicamento) {
        // Implementación futura para envío de email
        println("   📧 Email a $email: ${medicamento.nombre} tomado a las ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")

        // Por ahora crear intent para app de email (opcional)
        try {
            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, "✅ Medicamento tomado: ${medicamento.nombre}")
                putExtra(Intent.EXTRA_TEXT,
                    "Hola,\n\n" +
                            "Te informo que se ha tomado el medicamento ${medicamento.nombre} " +
                            "a las ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}.\n\n" +
                            if (medicamento.notas.isNotEmpty()) "Notas: ${medicamento.notas}\n\n" else "" +
                                    "Próxima toma programada: ${medicamento.hora}\n\n" +
                                    "Enviado automáticamente por MedTime"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            // No iniciar automáticamente, solo preparar para uso futuro
            // context.startActivity(Intent.createChooser(emailIntent, "Enviar notificación"))

        } catch (e: Exception) {
            println("   ❌ Error preparando email: ${e.message}")
        }
    }

    private fun enviarSMS(context: Context, telefono: String, medicamento: Medicamento) {
        // Implementación futura para envío de SMS
        println("   📱 SMS a $telefono: ${medicamento.nombre} tomado")

        // Por ahora crear intent para app de SMS (opcional)
        try {
            val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("smsto:$telefono")
                putExtra("sms_body",
                    "✅ ${medicamento.nombre} tomado a las " +
                            "${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}. " +
                            "Próxima: ${medicamento.hora}. [MedTime]"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            // No enviar automáticamente, solo preparar para uso futuro
            // context.startActivity(smsIntent)

        } catch (e: Exception) {
            println("   ❌ Error preparando SMS: ${e.message}")
        }
    }
}

// Receptor para reinicio del dispositivo - reprogramar alarmas
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return

        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {

            println("🔄 BootReceiver: Reprogramando alarmas después del reinicio")

            try {
                val storage = MedicationStorage.getInstance(context)
                val alarmHelper = AlarmHelper.getInstance(context)
                val medicamentos = storage.obtenerMedicamentosActivos()

                // Reprogramar todas las alarmas activas
                alarmHelper.reprogramarTodasLasAlarmas(medicamentos)

                println("✅ BootReceiver: ${medicamentos.size} alarmas reprogramadas")

            } catch (e: Exception) {
                println("❌ BootReceiver: Error reprogramando alarmas - ${e.message}")
                e.printStackTrace()
            }
        }
    }
}