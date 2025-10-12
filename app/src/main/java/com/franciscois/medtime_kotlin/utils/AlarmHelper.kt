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
            putExtra("medicamento_duracion_sonido", medicamento.duracionSonidoMinutos)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicamento.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, proximaAlarma, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, proximaAlarma, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, proximaAlarma, pendingIntent)
            }
            val fechaFormateada = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(proximaAlarma))
            Toast.makeText(context, "⏰ ${medicamento.nombre} programado para $fechaFormateada", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "❌ Error programando alarma: ${medicamento.nombre}", Toast.LENGTH_SHORT).show()
            false
        }
    }

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

    fun reprogramarTodasLasAlmas(medicamentos: List<Medicamento>) {
        medicamentos.forEach { medicamento ->
            if (medicamento.activo) {
                programarAlarma(medicamento)
            } else {
                cancelarAlarma(medicamento.id)
            }
        }
    }

    fun programarAlarmaPrueba(medicamento: Medicamento, segundos: Int = 10) {
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra("medicamento_id", medicamento.id)
            putExtra("medicamento_nombre", medicamento.nombre)
            putExtra("medicamento_notas", "Alarma de prueba - ${medicamento.notas}")
            putExtra("es_prueba", true)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            "prueba_${medicamento.id}".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerTime = System.currentTimeMillis() + (segundos * 1000)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        Toast.makeText(context, "🧪 Alarma de prueba en $segundos segundos: ${medicamento.nombre}", Toast.LENGTH_SHORT).show()
    }

    fun obtenerInfoAlarmas(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "Alarmas exactas permitidas: ${alarmManager.canScheduleExactAlarms()}\n" +
                    "Version Android: ${Build.VERSION.SDK_INT}"
        } else {
            "Versión Android compatible con alarmas: ${Build.VERSION.SDK_INT}"
        }
    }

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