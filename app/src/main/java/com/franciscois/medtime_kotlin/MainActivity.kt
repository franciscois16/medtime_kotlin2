package com.franciscois.medtime_kotlin

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
// import android.app.NotificationManager // No es necesario si usamos Compat
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat // Usar Compat
import androidx.core.content.ContextCompat
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

    // (Declaraciones no cambian)
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
                // (Lógica agregar medicamento no cambia)
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

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted) {
                // Si deniega, mostrar diálogo explicando por qué es necesario
                AlertDialog.Builder(this)
                    .setTitle("Permiso Denegado")
                    .setMessage("Sin el permiso de notificaciones, las alarmas no podrán sonar ni mostrarse. Por favor, considere activarlo desde los ajustes de la aplicación.")
                    .setPositiveButton("Ok", null)
                    .show()
            }
        }

    // Launcher para el permiso "Aparecer Encima"
    private val requestOverlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // No necesitamos hacer nada con el resultado aquí, solo llevar al usuario
            // Volveremos a verificar en onResume
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

        // Verificar todos los permisos al iniciar
        checkRequiredPermissions()
    }

    override fun onResume() {
        super.onResume()
        cargarYActualizarUI()
        // Volver a verificar permisos críticos por si el usuario los cambió
        checkRequiredPermissions()
    }

    // --- FUNCIÓN CENTRALIZADA PARA VERIFICAR PERMISOS ---
    private fun checkRequiredPermissions() {
        checkNotificationPermission()
        checkDrawOverlayPermission()
        checkFullScreenIntentPermission() // Esta verificará si las notificaciones están habilitadas primero
    }

    private fun checkDrawOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showPermissionDialog(
                "Permiso Necesario: Aparecer Encima",
                "Para mostrar la alarma sobre la pantalla de bloqueo, MedTime necesita este permiso.",
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION
            ) {
                // Lanzar la actividad para solicitar el permiso de superposición
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                requestOverlayPermissionLauncher.launch(intent)
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Usar diálogo antes de lanzar la petición formal
                AlertDialog.Builder(this)
                    .setTitle("Permiso Necesario: Notificaciones")
                    .setMessage("MedTime necesita enviar notificaciones para recordarte tus medicamentos.")
                    .setPositiveButton("Solicitar Permiso") { _, _ ->
                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    .setNegativeButton("Ahora no", null)
                    .show()
            }
        }
    }

    // --- FUNCIÓN MEJORADA ---
    private fun checkFullScreenIntentPermission() {
        val notificationManager = NotificationManagerCompat.from(this)
        // Solo relevante a partir de Android 12 (API 31)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Primero, verificar si las notificaciones están habilitadas en general
            if (!notificationManager.areNotificationsEnabled()) {
                // Si no están habilitadas, guiar al usuario a activarlas primero
                showPermissionDialog(
                    "Notificaciones Desactivadas",
                    "Las notificaciones para MedTime están desactivadas. Necesitas activarlas para recibir alarmas.",
                    Settings.ACTION_APP_NOTIFICATION_SETTINGS // Lleva a los ajustes generales de notificación de la app
                )
                return // No continuar si las notificaciones están desactivadas
            }

            // Ahora, verificar si puede usar FullScreenIntent
            // NOTA: canUseFullScreenIntent() a veces es engañoso en algunos OEMs.
            // Es mejor guiar al usuario a los ajustes de la CATEGORÍA si sospechamos problemas.

            // Mostraremos un diálogo informativo que guía al usuario a la categoría CUALQUIER CASO
            // si detectamos que es un Samsung, ya que ahí suele estar el problema oculto.
            // O podríamos mostrarlo siempre la primera vez para asegurar.
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val firstCheck = prefs.getBoolean("first_fullscreen_check", true)

            // Simplificado: Siempre mostrar la guía la primera vez o si detectamos problemas (aunque canUseFullScreenIntent no sea fiable)
//            if (firstCheck || !notificationManager.canUseFullScreenIntent()) { // Mostrar si es la primera vez O si el sistema *dice* que no puede
//                showPermissionDialog(
//                    "Ajuste Importante (Samsung)",
//                    "Para asegurar que la alarma aparezca en pantalla completa:\n\n" +
//                            "1. Toca 'Ir a Ajustes'.\n" +
//                            "2. Entra en 'Categorías de notificación'.\n" +
//                            "3. Selecciona 'Alarmas de Medicamentos'.\n" +
//                            "4. **ACTIVA 'Mostrar como ventana emergente'** y asegúrate que la importancia sea 'Urgente'.",
//                    Settings.ACTION_APP_NOTIFICATION_SETTINGS // Lleva a los ajustes generales, el usuario debe navegar
//                )
//                // Marcar que ya hicimos la primera verificación
//                prefs.edit().putBoolean("first_fullscreen_check", false).apply()
//            }
        }
    }

    // Función helper para mostrar diálogos de permisos/guía
    private fun showPermissionDialog(title: String, message: String, settingsAction: String, positiveAction: (() -> Unit)? = null) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Ir a Ajustes") { _, _ ->
                if (positiveAction != null) {
                    positiveAction.invoke() // Ejecutar acción específica si se proporcionó (para overlay)
                } else {
                    // Acción por defecto: abrir ajustes
                    val intent = Intent(settingsAction).apply {
                        // Para ACTION_APP_NOTIFICATION_SETTINGS, necesitamos el extra
                        if (settingsAction == Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        } else {
                            // Para otros, como OVERLAY, usamos data
                            data = Uri.parse("package:$packageName")
                        }
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "No se pudo abrir la configuración.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Ahora no", null)
            .setCancelable(false) // Evitar que se cierre tocando fuera
            .show()
    }

    // (El resto del archivo MainActivity.kt no cambia)
    // ... Código restante ...
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

    private fun eliminarMedicamento(medicamento: Medicamento) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar eliminación")
            .setMessage("¿Estás seguro de que quieres eliminar '${medicamento.nombre}'?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Eliminar") { _, _ ->
                alarmHelper.cancelarAlarma(medicamento.id)
                storage.eliminarMedicamento(medicamento.id)
                cargarYActualizarUI()
                Toast.makeText(this, "${medicamento.nombre} eliminado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

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