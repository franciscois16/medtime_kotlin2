package com.franciscois.medtime_kotlin.receivers

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.franciscois.medtime_kotlin.AlarmActivity
import com.franciscois.medtime_kotlin.storage.MedicationStorage
import com.franciscois.medtime_kotlin.utils.AlarmHelper
import com.franciscois.medtime_kotlin.utils.NotificationHelper

class MedicationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val medicamentoId = intent.getStringExtra("medicamento_id") ?: return
        val nombreMedicamento = intent.getStringExtra("medicamento_nombre") ?: "Medicamento"
        val notas = intent.getStringExtra("medicamento_notas") ?: ""
        val duracionSonido = intent.getIntExtra("medicamento_duracion_sonido", 1)
        val esPrueba = intent.getBooleanExtra("es_prueba", false)

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        // --- LÓGICA PRINCIPAL: Decidir qué mostrar ---
        if (keyguardManager.isKeyguardLocked) {
            // PANTALLA BLOQUEADA: Lanzar nuestra AlarmActivity de pantalla completa
            val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
                putExtra("medicamento_id", medicamentoId)
                putExtra("medicamento_nombre", nombreMedicamento)
                putExtra("medicamento_notas", notas)
                putExtra("medicamento_duracion_sonido", duracionSonido)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fullScreenIntent)
        } else {
            // PANTALLA DESBLOQUEADA: Mostrar una notificación normal
            val notificationHelper = NotificationHelper.getInstance(context)
            notificationHelper.mostrarNotificacionAlarma(
                nombreMedicamento,
                notas,
                medicamentoId,
                duracionSonido,
                esPrueba
            )
        }

        // Reprogramar la siguiente alarma (si no es una prueba)
        if (!esPrueba) {
            reprogramarSiguiente(context, medicamentoId)
        }
    }

    private fun reprogramarSiguiente(context: Context, medicamentoId: String) {
        // Se ejecuta en segundo plano para no bloquear el hilo principal
        Handler(Looper.getMainLooper()).post {
            val storage = MedicationStorage.getInstance(context)
            val alarmHelper = AlarmHelper.getInstance(context)
            storage.buscarMedicamento(medicamentoId)?.let { medicamento ->
                val proximaAlarmaCalculada = medicamento.calcularProximaAlarma()
                val medicamentoActualizado = medicamento.copy(proximaAlarma = proximaAlarmaCalculada)
                storage.actualizarMedicamento(medicamentoActualizado)
                alarmHelper.programarAlarma(medicamentoActualizado)
            }
        }
    }
}