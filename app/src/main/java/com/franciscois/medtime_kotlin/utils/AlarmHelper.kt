package com.franciscois.medtime_kotlin.utils


import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.franciscois.medtime_kotlin.models.Medicamento
import com.franciscois.medtime_kotlin.receivers.MedicationAlarmReceiver
import java.text.SimpleDateFormat
import java.util.*

class AlarmHelper(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        @Volatile
        private var INSTANCE: AlarmHelper? = null

        fun getInstance(context: Context): AlarmHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AlarmHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // Programar alarma para un medicamento
    fun programarAlarma(medicamento: Medicamento): Boolean {
        if (!medicamento.activo) {
            cancelarAlarma(medicamento.id)
            return false
        }

        val proximaAlarma = medicamento.calcularProximaAlarma()
        if (proximaAlarma == 0L) {
            return false
        }

        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra("medicamento_id", medicamento.id)
            putExtra("medicamento_nombre", medicamento.nombre)
            putExtra("medicamento_notas", medicamento.notas)
            putExtra("medicamento_familiares", medicamento.familiares.toTypedArray())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicamento.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return try {
            // Usar alarma exacta si está disponible
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        proximaAlarma,
                        pendingIntent
                    )
                } else {
                    // Fallback para dispositivos que no permiten alarmas exactas
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        proximaAlarma,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    proximaAlarma,
                    pendingIntent
                )
            }

            // Mostrar confirmación
            val fechaFormateada = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(Date(proximaAlarma))

            Toast.makeText(
                context,
                "⏰ ${medicamento.nombre} programado para $fechaFormateada",
                Toast.LENGTH_SHORT
            ).show()

            true
        } catch (e: SecurityException) {
            // En Android 12+, necesita permiso especial para alarmas exactas
            Toast.makeText(
                context,
                "⚠️ Permisos de alarma requeridos para ${medicamento.nombre}",
                Toast.LENGTH_LONG
            ).show()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "❌ Error programando alarma: ${medicamento.nombre}",
                Toast.LENGTH_SHORT
            ).show()
            false
        }
    }

    // Cancelar alarma de un medicamento
    fun cancelarAlarma(medicamentoId: String) {
        val intent = Intent(context, MedicationAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicamentoId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
    }

    // Reprogramar todas las alarmas activas
    fun reprogramarTodasLasAlarmas(medicamentos: List<Medicamento>) {
        medicamentos.forEach { medicamento ->
            if (medicamento.activo) {
                programarAlarma(medicamento)
            } else {
                cancelarAlarma(medicamento.id)
            }
        }
    }

    // Programar alarma de prueba (para testing)
    fun programarAlarmaPrueba(medicamento: Medicamento, segundos: Int = 10): Boolean {
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra("medicamento_id", medicamento.id)
            putExtra("medicamento_nombre", medicamento.nombre)
            putExtra("medicamento_notas", "Alarma de prueba - ${medicamento.notas}")
            putExtra("medicamento_familiares", medicamento.familiares.toTypedArray())
            putExtra("es_prueba", true)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            "prueba_${medicamento.id}".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + (segundos * 1000)

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }

            Toast.makeText(
                context,
                "🧪 Alarma de prueba en $segundos segundos: ${medicamento.nombre}",
                Toast.LENGTH_SHORT
            ).show()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Verificar si puede programar alarmas exactas
    private fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    // Obtener información de alarmas programadas (para debug)
    fun obtenerInfoAlarmas(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "Alarmas exactas permitidas: ${canScheduleExactAlarms()}\n" +
                    "Version Android: ${Build.VERSION.SDK_INT}"
        } else {
            "Versión Android compatible con alarmas: ${Build.VERSION.SDK_INT}"
        }
    }

    // Calcular próximas alarmas para mostrar en UI
    fun calcularProximasAlarmas(medicamentos: List<Medicamento>): List<Pair<Medicamento, Long>> {
        return medicamentos
            .filter { it.activo }
            .map { medicamento ->
                Pair(medicamento, medicamento.calcularProximaAlarma())
            }
            .filter { it.second > 0 }
            .sortedBy { it.second }
    }

    // Formatear tiempo restante hasta próxima alarma
    fun formatearTiempoRestante(tiempoAlarma: Long): String {
        val ahora = System.currentTimeMillis()
        val diferencia = tiempoAlarma - ahora

        if (diferencia <= 0) return "Ahora"

        val segundos = diferencia / 1000
        val minutos = segundos / 60
        val horas = minutos / 60
        val dias = horas / 24

        return when {
            dias > 0 -> "${dias}d ${horas % 24}h"
            horas > 0 -> "${horas}h ${minutos % 60}m"
            minutos > 0 -> "${minutos}m"
            else -> "${segundos}s"
        }
    }
}