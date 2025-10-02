package com.franciscois.medtime_kotlin.models

import java.util.*

data class Medicamento(
    val id: String = UUID.randomUUID().toString(),
    var nombre: String,
    var hora: String, // HH:mm formato
    var dias: List<String>, // ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"]
    var frecuenciaHoras: Int = 24, // Cada cuántas horas
    var notas: String = "",
    var familiares: List<String> = listOf(), // Lista de contactos
    var activo: Boolean = true,
    var proximaAlarma: Long = 0L,
    var fechaCreacion: Long = System.currentTimeMillis()
) {

    // Propiedades computadas para compatibilidad con el código actual
    val diasTexto: String
        get() = when {
            dias.size == 7 -> "Todos los días"
            dias.isEmpty() -> "Ningún día"
            else -> dias.joinToString(", ")
        }

    val frecuenciaTexto: String
        get() = when {
            frecuenciaHoras == 24 -> "Una vez al día"
            frecuenciaHoras < 24 -> "Cada $frecuenciaHoras horas"
            else -> "Cada ${frecuenciaHoras / 24} días"
        }

    // Calcular próxima alarma basada en hora y días
    fun calcularProximaAlarma(): Long {
        if (!activo || dias.isEmpty()) return 0L

        val calendar = Calendar.getInstance()
        val horaPartes = hora.split(":")
        val horaDia = horaPartes[0].toInt()
        val minutos = horaPartes[1].toInt()

        // Establecer la hora de hoy
        calendar.set(Calendar.HOUR_OF_DAY, horaDia)
        calendar.set(Calendar.MINUTE, minutos)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Si ya pasó la hora de hoy, empezar mañana
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        // Encontrar el siguiente día válido
        val diasSemana = mapOf(
            "Dom" to Calendar.SUNDAY,
            "Lun" to Calendar.MONDAY,
            "Mar" to Calendar.TUESDAY,
            "Mié" to Calendar.WEDNESDAY,
            "Jue" to Calendar.THURSDAY,
            "Vie" to Calendar.FRIDAY,
            "Sáb" to Calendar.SATURDAY
        )

        // Si todos los días, retornar la próxima ocurrencia
        if (dias.size == 7) {
            return calendar.timeInMillis
        }

        // Buscar el próximo día válido (máximo 7 días)
        repeat(7) {
            val diaSemana = calendar.get(Calendar.DAY_OF_WEEK)
            val nombreDia = diasSemana.entries.find { it.value == diaSemana }?.key

            if (nombreDia != null && nombreDia in dias) {
                return calendar.timeInMillis
            }

            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        return 0L // No debería llegar aquí
    }

    // Convertir a JSON para almacenamiento
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "nombre" to nombre,
            "hora" to hora,
            "dias" to dias,
            "frecuenciaHoras" to frecuenciaHoras,
            "notas" to notas,
            "familiares" to familiares,
            "activo" to activo,
            "proximaAlarma" to proximaAlarma,
            "fechaCreacion" to fechaCreacion
        )
    }

    companion object {
        // Crear desde Map para carga de almacenamiento
        fun fromMap(map: Map<String, Any>): Medicamento {
            return Medicamento(
                id = map["id"] as? String ?: UUID.randomUUID().toString(),
                nombre = map["nombre"] as? String ?: "",
                hora = map["hora"] as? String ?: "08:00",
                dias = (map["dias"] as? List<*>)?.map { it.toString() } ?: listOf(),
                frecuenciaHoras = (map["frecuenciaHoras"] as? Number)?.toInt() ?: 24,
                notas = map["notas"] as? String ?: "",
                familiares = (map["familiares"] as? List<*>)?.map { it.toString() } ?: listOf(),
                activo = map["activo"] as? Boolean ?: true,
                proximaAlarma = (map["proximaAlarma"] as? Number)?.toLong() ?: 0L,
                fechaCreacion = (map["fechaCreacion"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }

        // Crear ejemplos para testing
        fun crearEjemplos(): List<Medicamento> {
            return listOf(
                Medicamento(
                    nombre = "Paracetamol",
                    hora = "08:00",
                    dias = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"),
                    frecuenciaHoras = 8,
                    notas = "Tomar con agua después de comer. Máximo 3 veces al día.",
                    familiares = listOf("+1234567890", "mama@email.com")
                ),
                Medicamento(
                    nombre = "Vitamina D",
                    hora = "09:00",
                    dias = listOf("Lun", "Mié", "Vie"),
                    frecuenciaHoras = 48, // Cada 2 días (pero solo ciertos días)
                    notas = "Una cápsula con el desayuno. Ayuda con la absorción del calcio.",
                    familiares = listOf("doctor@clinica.com"),
                    activo = false
                ),
                Medicamento(
                    nombre = "Omega 3",
                    hora = "20:00",
                    dias = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"),
                    frecuenciaHoras = 24,
                    notas = "Tomar con la cena para mejor absorción.",
                    familiares = listOf()
                )
            )
        }
    }
}