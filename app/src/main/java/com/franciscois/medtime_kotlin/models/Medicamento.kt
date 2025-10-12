package com.franciscois.medtime_kotlin.models

import java.util.*

data class Medicamento(
    val id: String = UUID.randomUUID().toString(),
    var nombre: String,
    var fechaHoraPrimeraToma: Long,
    var frecuenciaHoras: Int,
    var duracionSonidoMinutos: Int = 1, // Nuevo campo con valor por defecto de 1 minuto
    var notas: String = "",
    var familiares: List<String> = listOf(),
    var activo: Boolean = true,
    var proximaAlarma: Long = 0L,
    var fechaCreacion: Long = System.currentTimeMillis()
) {

    val frecuenciaTexto: String
        get() = when {
            frecuenciaHoras < 24 -> "Cada $frecuenciaHoras horas"
            frecuenciaHoras == 24 -> "Cada día"
            else -> "Cada ${frecuenciaHoras / 24} días"
        }

    fun calcularProximaAlarma(): Long {
        if (!activo || frecuenciaHoras <= 0) return 0L
        val ahora = System.currentTimeMillis()
        if (fechaHoraPrimeraToma > ahora) {
            return fechaHoraPrimeraToma
        }
        val tiempoTranscurrido = ahora - fechaHoraPrimeraToma
        val frecuenciaEnMillis = frecuenciaHoras * 60 * 60 * 1000L
        val intervalosPasados = tiempoTranscurrido / frecuenciaEnMillis
        return fechaHoraPrimeraToma + (intervalosPasados + 1) * frecuenciaEnMillis
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "nombre" to nombre,
            "fechaHoraPrimeraToma" to fechaHoraPrimeraToma,
            "frecuenciaHoras" to frecuenciaHoras,
            "duracionSonidoMinutos" to duracionSonidoMinutos, // Añadido para guardado
            "notas" to notas,
            "familiares" to familiares,
            "activo" to activo,
            "proximaAlarma" to proximaAlarma,
            "fechaCreacion" to fechaCreacion
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): Medicamento {
            return Medicamento(
                id = map["id"] as? String ?: UUID.randomUUID().toString(),
                nombre = map["nombre"] as? String ?: "",
                fechaHoraPrimeraToma = (map["fechaHoraPrimeraToma"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                frecuenciaHoras = (map["frecuenciaHoras"] as? Number)?.toInt() ?: 24,
                duracionSonidoMinutos = (map["duracionSonidoMinutos"] as? Number)?.toInt() ?: 1, // Añadido para carga
                notas = map["notas"] as? String ?: "",
                familiares = (map["familiares"] as? List<*>)?.map { it.toString() } ?: listOf(),
                activo = map["activo"] as? Boolean ?: true,
                proximaAlarma = (map["proximaAlarma"] as? Number)?.toLong() ?: 0L,
                fechaCreacion = (map["fechaCreacion"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }

        fun crearEjemplos(): List<Medicamento> {
            val inicioIbuprofeno = Calendar.getInstance().apply { add(Calendar.HOUR, -2) }.timeInMillis
            val ibuprofeno = Medicamento(
                nombre = "Ibuprofeno 400mg",
                fechaHoraPrimeraToma = inicioIbuprofeno,
                frecuenciaHoras = 8,
                duracionSonidoMinutos = 2, // Ejemplo con 2 minutos
                notas = "Tomar con comida para evitar malestar estomacal.",
                familiares = listOf("mama@email.com")
            )

            val inicioVitamina = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
            }.timeInMillis
            val vitaminaD = Medicamento(
                nombre = "Vitamina D",
                fechaHoraPrimeraToma = inicioVitamina,
                frecuenciaHoras = 24,
                duracionSonidoMinutos = 1,
                notas = "Tomar con el desayuno."
            )

            return listOf(ibuprofeno, vitaminaD).map {
                it.proximaAlarma = it.calcularProximaAlarma()
                it
            }
        }
    }
}