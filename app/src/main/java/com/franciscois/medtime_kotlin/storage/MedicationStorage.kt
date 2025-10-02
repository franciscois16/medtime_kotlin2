package com.franciscois.medtime_kotlin.storage

import android.content.Context
import android.content.SharedPreferences
import com.franciscois.medtime_kotlin.models.Medicamento
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MedicationStorage(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("medicamentos_db", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_MEDICAMENTOS = "lista_medicamentos"
        private const val KEY_VERSION = "db_version"
        private const val CURRENT_VERSION = 1

        @Volatile
        private var INSTANCE: MedicationStorage? = null

        fun getInstance(context: Context): MedicationStorage {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MedicationStorage(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        // Verificar versión y migrar si es necesario
        val currentVersion = sharedPreferences.getInt(KEY_VERSION, 0)
        if (currentVersion < CURRENT_VERSION) {
            migrarDatos(currentVersion)
            sharedPreferences.edit().putInt(KEY_VERSION, CURRENT_VERSION).apply()
        }
    }

    // Guardar lista completa de medicamentos
    fun guardarMedicamentos(medicamentos: List<Medicamento>): Boolean {
        return try {
            val medicamentosMap = medicamentos.map { it.toMap() }
            val json = gson.toJson(medicamentosMap)
            sharedPreferences.edit().putString(KEY_MEDICAMENTOS, json).apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Cargar lista de medicamentos
    fun cargarMedicamentos(): List<Medicamento> {
        return try {
            val json = sharedPreferences.getString(KEY_MEDICAMENTOS, null)
            if (json != null) {
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val medicamentosMap: List<Map<String, Any>> = gson.fromJson(json, type)
                medicamentosMap.map { Medicamento.fromMap(it) }
            } else {
                // Primera vez - crear datos de ejemplo
                val ejemplos = Medicamento.crearEjemplos()
                guardarMedicamentos(ejemplos)
                ejemplos
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // En caso de error, devolver lista vacía
            emptyList()
        }
    }

    // Agregar un medicamento
    fun agregarMedicamento(medicamento: Medicamento): Boolean {
        val medicamentos = cargarMedicamentos().toMutableList()
        medicamentos.add(medicamento)
        return guardarMedicamentos(medicamentos)
    }

    // Actualizar un medicamento existente
    fun actualizarMedicamento(medicamentoActualizado: Medicamento): Boolean {
        val medicamentos = cargarMedicamentos().toMutableList()
        val index = medicamentos.indexOfFirst { it.id == medicamentoActualizado.id }

        return if (index >= 0) {
            medicamentos[index] = medicamentoActualizado
            guardarMedicamentos(medicamentos)
        } else {
            false
        }
    }

    // Eliminar un medicamento
    fun eliminarMedicamento(medicamentoId: String): Boolean {
        val medicamentos = cargarMedicamentos().toMutableList()
        val eliminado = medicamentos.removeIf { it.id == medicamentoId }

        return if (eliminado) {
            guardarMedicamentos(medicamentos)
        } else {
            false
        }
    }

    // Buscar medicamento por ID
    fun buscarMedicamento(medicamentoId: String): Medicamento? {
        return cargarMedicamentos().find { it.id == medicamentoId }
    }

    // Obtener medicamentos activos
    fun obtenerMedicamentosActivos(): List<Medicamento> {
        return cargarMedicamentos().filter { it.activo }
    }

    // Obtener estadísticas
    fun obtenerEstadisticas(): Map<String, Int> {
        val medicamentos = cargarMedicamentos()
        return mapOf(
            "total" to medicamentos.size,
            "activos" to medicamentos.count { it.activo },
            "inactivos" to medicamentos.count { !it.activo },
            "conNotas" to medicamentos.count { it.notas.isNotEmpty() },
            "conFamiliares" to medicamentos.count { it.familiares.isNotEmpty() }
        )
    }

    // Limpiar todos los datos (para testing o reset)
    fun limpiarTodos(): Boolean {
        return try {
            sharedPreferences.edit().clear().apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Exportar datos como JSON (para backup)
    fun exportarDatos(): String? {
        return try {
            val medicamentos = cargarMedicamentos()
            val datos = mapOf(
                "version" to CURRENT_VERSION,
                "fecha_exportacion" to System.currentTimeMillis(),
                "medicamentos" to medicamentos.map { it.toMap() }
            )
            gson.toJson(datos)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Importar datos desde JSON
    fun importarDatos(jsonData: String): Boolean {
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val datos: Map<String, Any> = gson.fromJson(jsonData, type)

            @Suppress("UNCHECKED_CAST")
            val medicamentosMap = datos["medicamentos"] as? List<Map<String, Any>>

            if (medicamentosMap != null) {
                val medicamentos = medicamentosMap.map { Medicamento.fromMap(it) }
                guardarMedicamentos(medicamentos)
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Migrar datos de versiones anteriores
    private fun migrarDatos(versionAnterior: Int) {
        // Por ahora no hay migraciones, pero aquí se agregarían
        // cuando cambien los modelos de datos en el futuro
        when (versionAnterior) {
            0 -> {
                // Migración inicial - no hacer nada
            }
            // Agregar más migraciones aquí en el futuro
        }
    }
}