package com.franciscois.medtime_kotlin

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.franciscois.medtime_kotlin.adapters.MedicationAdapter
import com.franciscois.medtime_kotlin.models.Medicamento
import com.franciscois.medtime_kotlin.storage.MedicationStorage
import com.franciscois.medtime_kotlin.utils.AlarmHelper
import com.franciscois.medtime_kotlin.utils.NotificationHelper
import com.franciscois.medtime_kotlin.utils.ThemeManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: LinearLayout
    private lateinit var headerTitle: TextView
    private lateinit var headerSubtitle: TextView
    private lateinit var fab: FloatingActionButton
    private lateinit var settingsButton: ImageButton
    private lateinit var storage: MedicationStorage
    private lateinit var alarmHelper: AlarmHelper
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var themeManager: ThemeManager
    private lateinit var adapter: MedicationAdapter
    private var medicamentos = mutableListOf<Medicamento>()
    private var medicamentosFiltrados = mutableListOf<Medicamento>()
    private var mostrarSoloActivos = false

    private val addMedicationResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let {
                val nombre = it.getStringExtra("nombre") ?: ""
                val fechaHoraPrimeraToma = it.getLongExtra("fechaHoraPrimeraToma", System.currentTimeMillis())
                val frecuenciaHoras = it.getIntExtra("frecuenciaHoras", 24)
                val duracionSonidoMinutos = it.getIntExtra("duracionSonidoMinutos", 1)
                val notas = it.getStringExtra("notas") ?: ""
                val familiares = it.getStringArrayListExtra("familiares") ?: arrayListOf()

                val nuevoMedicamento = Medicamento(
                    nombre = nombre,
                    fechaHoraPrimeraToma = fechaHoraPrimeraToma,
                    frecuenciaHoras = frecuenciaHoras,
                    duracionSonidoMinutos = duracionSonidoMinutos,
                    notas = notas,
                    familiares = familiares,
                    activo = true
                )

                nuevoMedicamento.proximaAlarma = nuevoMedicamento.calcularProximaAlarma()
                storage.agregarMedicamento(nuevoMedicamento)
                alarmHelper.programarAlarma(nuevoMedicamento)

                cargarYActualizarUI()
                Toast.makeText(this, "'$nombre' guardado correctamente", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeHelpers()
        findViews()
        setupWindowInsets()
        setupRecyclerView()
        setupClickListeners()
        cargarYActualizarUI()
        manejarIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        cargarYActualizarUI()
    }

    private fun initializeHelpers() {
        storage = MedicationStorage.getInstance(this)
        alarmHelper = AlarmHelper.getInstance(this)
        notificationHelper = NotificationHelper.getInstance(this)
        themeManager = ThemeManager.getInstance(this)
    }

    private fun findViews() {
        recyclerView = findViewById(R.id.recycler_view_medicamentos)
        emptyView = findViewById(R.id.empty_view)
        headerTitle = findViewById(R.id.header_title)
        headerSubtitle = findViewById(R.id.header_subtitle)
        fab = findViewById(R.id.fab_add)
        settingsButton = findViewById(R.id.settings_button)
    }

    private fun setupWindowInsets() {
        val originalFabMargin = 24
        ViewCompat.setOnApplyWindowInsetsListener(fab) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val marginInPixels = (originalFabMargin * resources.displayMetrics.density).toInt()
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.bottom + marginInPixels
            }
            windowInsets
        }
    }

    private fun setupRecyclerView() {
        adapter = MedicationAdapter(medicamentosFiltrados) { medicamento, accion ->
            when (accion) {
                MedicationAdapter.ACTION_TOGGLE -> toggleMedicamento(medicamento)
                MedicationAdapter.ACTION_DELETE -> eliminarMedicamento(medicamento)
                MedicationAdapter.ACTION_EDIT -> editarMedicamento(medicamento)
                MedicationAdapter.ACTION_TEST_ALARM -> probarAlarma(medicamento)
                MedicationAdapter.ACTION_VIEW_DETAILS -> verDetalles(medicamento)
            }
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupClickListeners() {
        fab.setOnClickListener {
            val intent = Intent(this, AddEditActivity::class.java)
            addMedicationResultLauncher.launch(intent)
        }
        settingsButton.setOnClickListener {
            mostrarMenuOpciones()
        }
    }

    private fun cargarYActualizarUI() {
        medicamentos.clear()
        medicamentos.addAll(storage.cargarMedicamentos())
        filtrarMedicamentos()
        actualizarUI()
    }

    private fun filtrarMedicamentos() {
        medicamentosFiltrados.clear()
        medicamentosFiltrados.addAll(
            if (mostrarSoloActivos) medicamentos.filter { it.activo } else medicamentos
        )
        adapter.actualizarMedicamentos(medicamentosFiltrados)
    }

    private fun actualizarUI() {
        val totalMedicamentos = medicamentos.size
        val medicamentosActivos = medicamentos.count { it.activo }
        headerTitle.text = "MedTime"
        headerSubtitle.text = when {
            totalMedicamentos == 0 -> "No tienes recordatorios"
            else -> "$medicamentosActivos de $totalMedicamentos activos"
        }
        if (medicamentosFiltrados.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }

    private fun toggleMedicamento(medicamento: Medicamento) {
        medicamento.activo = !medicamento.activo
        medicamento.proximaAlarma = if (medicamento.activo) medicamento.calcularProximaAlarma() else 0L
        if (medicamento.activo) {
            alarmHelper.programarAlarma(medicamento)
        } else {
            alarmHelper.cancelarAlarma(medicamento.id)
        }
        storage.actualizarMedicamento(medicamento)
        cargarYActualizarUI()
    }

    // --- INICIO DE LA MODIFICACIÓN ---
    private fun eliminarMedicamento(medicamento: Medicamento) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar eliminación")
            .setMessage("¿Estás seguro de que quieres eliminar '${medicamento.nombre}'?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Eliminar") { _, _ ->
                // Lógica de borrado real
                alarmHelper.cancelarAlarma(medicamento.id)
                storage.eliminarMedicamento(medicamento.id)
                cargarYActualizarUI()
                Toast.makeText(this, "${medicamento.nombre} eliminado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    // --- FIN DE LA MODIFICACIÓN ---

    private fun editarMedicamento(medicamento: Medicamento) {
        Toast.makeText(this, "Editar ${medicamento.nombre} (próximamente)", Toast.LENGTH_SHORT).show()
    }

    private fun probarAlarma(medicamento: Medicamento) {
        alarmHelper.programarAlarmaPrueba(medicamento, 5)
    }

    private fun verDetalles(medicamento: Medicamento) {
        val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val fechaInicioTexto = formatoFecha.format(Date(medicamento.fechaHoraPrimeraToma))
        val proximaAlarmaTexto = if (medicamento.activo && medicamento.proximaAlarma > 0) {
            val fecha = formatoFecha.format(Date(medicamento.proximaAlarma))
            val tiempoRestante = alarmHelper.formatearTiempoRestante(medicamento.proximaAlarma)
            "$fecha (en $tiempoRestante)"
        } else {
            if (medicamento.activo) "Por programar" else "Inactivo"
        }
        val detalles = """
            Primera Dosis: $fechaInicioTexto
            Frecuencia: ${medicamento.frecuenciaTexto}
            Próxima Alarma: $proximaAlarmaTexto
            Notas: ${if (medicamento.notas.isNotEmpty()) medicamento.notas else "N/A"}
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle(medicamento.nombre)
            .setMessage(detalles)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun mostrarMenuOpciones() {
        val opciones = arrayOf(
            "Cambiar tema",
            if (mostrarSoloActivos) "Mostrar todos" else "Mostrar solo activos"
        )
        AlertDialog.Builder(this)
            .setTitle("Opciones")
            .setItems(opciones) { dialog, which ->
                when (which) {
                    0 -> Toast.makeText(this, "Cambiar tema (próximamente)", Toast.LENGTH_SHORT).show()
                    1 -> {
                        mostrarSoloActivos = !mostrarSoloActivos
                        cargarYActualizarUI()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun manejarIntent(intent: Intent?) {
        intent?.getStringExtra("medicamento_id")?.let { id ->
            storage.buscarMedicamento(id)?.let { med ->
                verDetalles(med)
            }
        }
    }
}