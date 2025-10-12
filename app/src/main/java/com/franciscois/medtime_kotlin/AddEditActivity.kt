package com.franciscois.medtime_kotlin

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.franciscois.medtime_kotlin.models.Medicamento
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*

class AddEditActivity : AppCompatActivity() {

    private lateinit var editTextNombre: TextInputEditText
    private lateinit var btnSeleccionarFechaHora: Button
    private lateinit var editTextFrecuencia: TextInputEditText
    private lateinit var editTextDuracionSonido: TextInputEditText
    private lateinit var editTextFamiliares: TextInputEditText
    private lateinit var editTextNotas: TextInputEditText
    private lateinit var btnGuardar: Button

    private val calendario = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit)

        editTextNombre = findViewById(R.id.edit_text_nombre)
        btnSeleccionarFechaHora = findViewById(R.id.btn_seleccionar_fecha_hora)
        editTextFrecuencia = findViewById(R.id.edit_text_frecuencia)
        editTextDuracionSonido = findViewById(R.id.edit_text_duracion_sonido)
        editTextFamiliares = findViewById(R.id.edit_text_familiares)
        editTextNotas = findViewById(R.id.edit_text_notas)
        btnGuardar = findViewById(R.id.btn_guardar)

        actualizarTextoBotonFechaHora()

        btnSeleccionarFechaHora.setOnClickListener { mostrarSelectorFecha() }
        btnGuardar.setOnClickListener { guardarMedicamento() }
    }

    private fun mostrarSelectorFecha() {
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            calendario.set(Calendar.YEAR, year)
            calendario.set(Calendar.MONTH, month)
            calendario.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            mostrarSelectorHora()
        }
        DatePickerDialog(this, dateSetListener, calendario.get(Calendar.YEAR), calendario.get(Calendar.MONTH), calendario.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun mostrarSelectorHora() {
        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            calendario.set(Calendar.HOUR_OF_DAY, hour)
            calendario.set(Calendar.MINUTE, minute)
            actualizarTextoBotonFechaHora()
        }
        TimePickerDialog(this, timeSetListener, calendario.get(Calendar.HOUR_OF_DAY), calendario.get(Calendar.MINUTE), true).show()
    }

    private fun actualizarTextoBotonFechaHora() {
        val formato = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        btnSeleccionarFechaHora.text = "Inicia: ${formato.format(calendario.time)}"
    }

    private fun guardarMedicamento() {
        val nombre = editTextNombre.text.toString().trim()
        if (nombre.isEmpty()) {
            Toast.makeText(this, "El nombre es obligatorio", Toast.LENGTH_SHORT).show()
            return
        }

        val frecuencia = editTextFrecuencia.text.toString().toIntOrNull() ?: 24
        val duracionSonido = editTextDuracionSonido.text.toString().toIntOrNull() ?: 1
        if (frecuencia <= 0 || duracionSonido <= 0) {
            Toast.makeText(this, "La frecuencia y duración deben ser mayores a 0", Toast.LENGTH_SHORT).show()
            return
        }

        val notas = editTextNotas.text.toString().trim()
        val familiaresStr = editTextFamiliares.text.toString().trim()
        val familiares = if (familiaresStr.isNotEmpty()) familiaresStr.split(",").map { it.trim() } else emptyList()

        val nuevoMedicamento = Medicamento(
            nombre = nombre,
            fechaHoraPrimeraToma = calendario.timeInMillis,
            frecuenciaHoras = frecuencia,
            duracionSonidoMinutos = duracionSonido,
            notas = notas,
            familiares = familiares
        )

        val resultIntent = Intent()
        resultIntent.putExtra("nombre", nuevoMedicamento.nombre)
        resultIntent.putExtra("fechaHoraPrimeraToma", nuevoMedicamento.fechaHoraPrimeraToma)
        resultIntent.putExtra("frecuenciaHoras", nuevoMedicamento.frecuenciaHoras)
        resultIntent.putExtra("duracionSonidoMinutos", nuevoMedicamento.duracionSonidoMinutos)
        resultIntent.putExtra("notas", nuevoMedicamento.notas)
        resultIntent.putStringArrayListExtra("familiares", ArrayList(nuevoMedicamento.familiares))

        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}