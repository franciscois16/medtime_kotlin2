package com.franciscois.medtime_kotlin.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.franciscois.medtime_kotlin.storage.MedicationStorage
import com.franciscois.medtime_kotlin.utils.AlarmHelper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return

        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON") {

            println("🔄 BootReceiver: Reprogramando alarmas después del reinicio")

            try {
                val storage = MedicationStorage.getInstance(context)
                val alarmHelper = AlarmHelper.getInstance(context)
                val medicamentos = storage.obtenerMedicamentosActivos()

                // Reprogramar todas las alarmas activas
                medicamentos.forEach {
                    alarmHelper.programarAlarma(it)
                }

                println("✅ BootReceiver: ${medicamentos.size} alarmas reprogramadas")

            } catch (e: Exception) {
                println("❌ BootReceiver: Error reprogramando alarmas - ${e.message}")
                e.printStackTrace()
            }
        }
    }
}