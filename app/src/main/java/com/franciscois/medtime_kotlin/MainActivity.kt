package com.franciscois.medtime_kotlin

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.franciscois.medtime_kotlin.adapters.MedicationAdapter
import com.franciscois.medtime_kotlin.models.Medicamento
import com.franciscois.medtime_kotlin.storage.MedicationStorage
import com.franciscois.medtime_kotlin.utils.AlarmHelper
import com.franciscois.medtime_kotlin.utils.NotificationHelper
import com.franciscois.medtime_kotlin.utils.ThemeManager
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MedicationAdapter
    private lateinit var emptyView: LinearLayout
    private lateinit var headerTitle: TextView
    private lateinit var headerSubtitle: TextView
    private lateinit var fab: FloatingActionButton

    private lateinit var storage: MedicationStorage
    private lateinit var alarmHelper: AlarmHelper
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var themeManager: ThemeManager

    private var medicamentos = mutableListOf<Medicamento>()
    private var medicamentosFiltrados = mutableListOf<Medicamento>()
    private var mostrarSoloActivos = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeHelpers()
        setupUserInterface()
        setupRecyclerView()
        cargarMedicamentos()
        manejarIntent(intent)
        actualizarUI()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        manejarIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        cargarMedicamentos()
        actualizarUI()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "Reprogramar alarmas")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 2, 0, if (mostrarSoloActivos) "Mostrar todos" else "Solo activos")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 3, 0, "Cambiar tema")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 4, 0, "Estadísticas")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 5, 0, "Configuración")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> reprogramarTodasLasAlarmas()
            2 -> toggleFiltroActivos()
            3 -> mostrarSelectorTemas()
            4 -> mostrarEstadisticas()
            5 -> abrirConfiguracion()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initializeHelpers() {
        storage = MedicationStorage.getInstance(this)
        alarmHelper = AlarmHelper.getInstance(this)
        notificationHelper = NotificationHelper.getInstance(this)
        themeManager = ThemeManager.getInstance(this)
    }

    private fun setupUserInterface() {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = themeManager.createBackgroundGradient()
        }

        val headerLayout = createPremiumHeader()
        mainLayout.addView(headerLayout)

        val contentFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            setPadding(0, 20, 0, 120)
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        contentFrame.addView(recyclerView)

        emptyView = createPremiumEmptyView()
        contentFrame.addView(emptyView)

        mainLayout.addView(contentFrame, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // FAB ahora en posición fija inferior
        val fabContainer = createPremiumFAB()
        mainLayout.addView(fabContainer)

        setContentView(mainLayout)
    }

    private fun mostrarMenuOpciones() {
        val opciones = arrayOf(
            "Cambiar tema",
            "Reprogramar alarmas",
            if (mostrarSoloActivos) "Mostrar todos" else "Solo activos",
            "Estadísticas",
            "Configuración"
        )

        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog)
            .setTitle("Opciones")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> mostrarSelectorTemas()
                    1 -> reprogramarTodasLasAlarmas()
                    2 -> toggleFiltroActivos()
                    3 -> mostrarEstadisticas()
                    4 -> abrirConfiguracion()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun createPremiumHeader(): LinearLayout {
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 80, 32, 40)
            background = createHeaderBackground()
            elevation = 8f
        }

        val titleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconContainer = createIconContainer()
        titleContainer.addView(iconContainer)

        headerTitle = TextView(this).apply {
            text = "MedTime"
            textSize = 34f
            setTextColor(themeManager.getOnBackgroundColor())
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            setShadowLayer(1f, 0f, 1f, Color.parseColor("#10000000"))
        }
        titleContainer.addView(headerTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // Botón de configuración (tuerca/settings)
        val settingsButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_manage) // Ícono de tuerca/settings
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(themeManager.getTextPrimaryColor())
            setPadding(12, 12, 12, 12)
            setOnClickListener { mostrarMenuOpciones() }
        }
        titleContainer.addView(settingsButton)

        headerLayout.addView(titleContainer)

        headerSubtitle = TextView(this).apply {
            text = "Cargando..."
            textSize = 17f
            setTextColor(themeManager.getTextSecondaryColor())
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setPadding(72, 12, 0, 0)
            alpha = 0.8f
        }
        headerLayout.addView(headerSubtitle)

        return headerLayout
    }

    private fun createHeaderBackground(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.LayerDrawable(arrayOf(
            themeManager.createBackgroundGradient(),
            android.graphics.drawable.GradientDrawable().apply {
                colors = intArrayOf(
                    Color.parseColor("#02${themeManager.getCurrentTheme().primary.substring(1)}"),
                    Color.parseColor("#00${themeManager.getCurrentTheme().primary.substring(1)}")
                )
                orientation = android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
            }
        ))
    }

    private fun createIconContainer(): FrameLayout {
        val iconContainer = FrameLayout(this).apply {
            setPadding(0, 0, 24, 0)
        }

        val iconBackground = View(this).apply {
            background = themeManager.createPrimaryGradient().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
            }
        }
        iconContainer.addView(iconBackground, FrameLayout.LayoutParams(48, 48))

        val iconText = TextView(this).apply {
            text = "💊"
            textSize = 20f
            gravity = Gravity.CENTER
        }
        iconContainer.addView(iconText, FrameLayout.LayoutParams(48, 48, Gravity.CENTER))

        return iconContainer
    }

    private fun createPremiumEmptyView(): LinearLayout {
        val emptyViewLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 120, 40, 120)
            visibility = View.GONE
        }

        val iconContainer = FrameLayout(this).apply {
            setPadding(0, 0, 0, 40)
        }

        val iconBackground = View(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                colors = intArrayOf(
                    themeManager.getDividerColor(),
                    Color.parseColor("#D1D1D6")
                )
                orientation = android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
            }
        }
        iconContainer.addView(iconBackground, FrameLayout.LayoutParams(120, 120, Gravity.CENTER))

        val emptyIcon = TextView(this).apply {
            text = "💊"
            textSize = 48f
            gravity = Gravity.CENTER
            alpha = 0.6f
        }
        iconContainer.addView(emptyIcon, FrameLayout.LayoutParams(120, 120, Gravity.CENTER))

        emptyViewLayout.addView(iconContainer)

        val emptyTitle = TextView(this).apply {
            text = "No hay recordatorios"
            textSize = 28f
            setTextColor(themeManager.getTextPrimaryColor())
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(0, 0, 0, 12)
        }
        emptyViewLayout.addView(emptyTitle)

        val emptySubtitle = TextView(this).apply {
            text = "Toca el botón + para crear tu primer recordatorio de medicamento"
            textSize = 17f
            setTextColor(themeManager.getTextSecondaryColor())
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        emptyViewLayout.addView(emptySubtitle)

        return emptyViewLayout
    }

    private fun createPremiumFAB(): FrameLayout {
        val fabFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(24, 16, 32, 120) // Más padding inferior para evitar botones de navegación
        }

        fab = FloatingActionButton(this).apply {
            setImageResource(android.R.drawable.ic_input_add)
            background = createFABBackground()
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            elevation = 16f
            setOnClickListener { abrirAgregarMedicamento() }
        }

        val fabLayoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.BOTTOM
        )
        fabFrame.addView(fab, fabLayoutParams)

        return fabFrame
    }

    private fun createFABBackground(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.LayerDrawable(arrayOf(
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#20000000"))
            },
            themeManager.createAccentButton()
        )).apply {
            setLayerInset(0, 4, 6, -4, -6)
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
        recyclerView.adapter = adapter
    }

    private fun cargarMedicamentos() {
        medicamentos.clear()
        medicamentos.addAll(storage.cargarMedicamentos())
        filtrarMedicamentos()
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

        headerTitle.text = "MedTime ($totalMedicamentos)"
        headerSubtitle.text = when {
            totalMedicamentos == 0 -> "No tienes medicamentos registrados"
            mostrarSoloActivos -> "$medicamentosActivos activos"
            else -> "$medicamentosActivos de $totalMedicamentos activos"
        }

        if (medicamentosFiltrados.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }

        invalidateOptionsMenu()
    }

    private fun manejarIntent(intent: Intent?) {
        intent?.let {
            val medicamentoId = it.getStringExtra("medicamento_id")
            val abrirAlarma = it.getBooleanExtra("abrir_alarma", false)

            if (abrirAlarma && medicamentoId != null) {
                val medicamento = medicamentos.find { med -> med.id == medicamentoId }
                medicamento?.let { med -> mostrarDialogoAlarma(med) }
            }
        }
    }

    private fun toggleMedicamento(medicamento: Medicamento) {
        medicamento.activo = !medicamento.activo

        if (medicamento.activo) {
            medicamento.proximaAlarma = medicamento.calcularProximaAlarma()
            alarmHelper.programarAlarma(medicamento)
        } else {
            alarmHelper.cancelarAlarma(medicamento.id)
        }

        storage.actualizarMedicamento(medicamento)
        cargarMedicamentos()
        actualizarUI()
    }

    private fun eliminarMedicamento(medicamento: Medicamento) {
        alarmHelper.cancelarAlarma(medicamento.id)
        storage.eliminarMedicamento(medicamento.id)
        cargarMedicamentos()
        actualizarUI()
        Toast.makeText(this, "${medicamento.nombre} eliminado", Toast.LENGTH_SHORT).show()
    }

    private fun editarMedicamento(medicamento: Medicamento) {
        Toast.makeText(this, "Editar ${medicamento.nombre} (próximamente)", Toast.LENGTH_SHORT).show()
    }

    private fun probarAlarma(medicamento: Medicamento) {
        alarmHelper.programarAlarmaPrueba(medicamento, 5)
    }

    private fun verDetalles(medicamento: Medicamento) {
        abrirPantallaDetalles(medicamento)
    }

    private fun abrirPantallaDetalles(medicamento: Medicamento) {
        val scrollView = ScrollView(this).apply {
            setPadding(24, 24, 24, 24)
            setBackgroundColor(themeManager.getBackgroundColor())
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val headerText = TextView(this).apply {
            text = medicamento.nombre
            textSize = 28f * resources.configuration.fontScale
            setTextColor(themeManager.getTextPrimaryColor())
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
        }
        mainLayout.addView(headerText)

        agregarSeccionDetalle(mainLayout, "Horario", medicamento.hora)
        agregarSeccionDetalle(mainLayout, "Días", medicamento.diasTexto)
        agregarSeccionDetalle(mainLayout, "Frecuencia", medicamento.frecuenciaTexto)

        val proximaAlarmaTexto = if (medicamento.activo && medicamento.proximaAlarma > 0) {
            val fecha = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(medicamento.proximaAlarma))
            val tiempoRestante = alarmHelper.formatearTiempoRestante(medicamento.proximaAlarma)
            "$fecha (en $tiempoRestante)"
        } else {
            if (medicamento.activo) "Por programar" else "Inactivo"
        }
        agregarSeccionDetalle(mainLayout, "Próxima alarma", proximaAlarmaTexto)

        val notasTexto = if (medicamento.notas.isNotEmpty()) medicamento.notas else "Sin notas adicionales"
        agregarSeccionDetalle(mainLayout, "Notas", notasTexto, true)

        val familiaresTexto = if (medicamento.familiares.isNotEmpty()) {
            medicamento.familiares.joinToString("\n")
        } else {
            "No se notificará a familiares"
        }
        agregarSeccionDetalle(mainLayout, "Notificar a", familiaresTexto, true)

        val estadoTexto = if (medicamento.activo) {
            "Activo - Las alarmas están programadas"
        } else {
            "Inactivo - Las alarmas están pausadas"
        }
        agregarSeccionDetalle(mainLayout, "Estado", estadoTexto)

        val botonesLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 0)
        }

        val btnProbar = Button(this).apply {
            text = "Probar alarma"
            setOnClickListener { probarAlarma(medicamento) }
        }
        botonesLayout.addView(btnProbar)

        val btnEditar = Button(this).apply {
            text = "Editar"
            setPadding(16, 0, 0, 0)
            setOnClickListener { editarMedicamento(medicamento) }
        }
        botonesLayout.addView(btnEditar)

        mainLayout.addView(botonesLayout)
        scrollView.addView(mainLayout)

        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog)
            .setView(scrollView)
            .setNegativeButton("Cerrar", null)
            .create()
            .show()
    }

    private fun agregarSeccionDetalle(parent: LinearLayout, titulo: String, contenido: String, expandible: Boolean = false) {
        val tituloText = TextView(this).apply {
            text = titulo
            textSize = 16f * resources.configuration.fontScale
            setTextColor(themeManager.getAccentColor())
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 16, 0, 8)
        }
        parent.addView(tituloText)

        val contenidoText = TextView(this).apply {
            text = contenido
            textSize = 14f * resources.configuration.fontScale
            setTextColor(themeManager.getTextPrimaryColor())
            setPadding(16, 8, 16, 16)
            background = themeManager.createCardBackground()

            if (expandible && contenido.length > 100) {
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
                setOnClickListener {
                    if (maxLines == 3) {
                        maxLines = Int.MAX_VALUE
                        ellipsize = null
                    } else {
                        maxLines = 3
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                }
            }
        }
        parent.addView(contenidoText)
    }

    private fun mostrarDialogoAlarma(medicamento: Medicamento) {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog)
            .setTitle(medicamento.nombre)
            .setMessage("Es hora de tomar tu medicamento\n\n${medicamento.notas}")
            .setPositiveButton("Tomado") { _, _ ->
                Toast.makeText(this, "${medicamento.nombre} marcado como tomado", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Posponer 10min") { _, _ ->
                alarmHelper.programarAlarmaPrueba(medicamento, 600)
            }
            .setNegativeButton("Cancelar", null)
            .setCancelable(false)
            .show()
    }

    private fun mostrarSelectorTemas() {
        val temas = themeManager.getAvailableThemes()
        val nombresThemes = temas.map { it.name }.toTypedArray()
        val temaActual = themeManager.getCurrentTheme()
        val indiceActual = temas.indexOfFirst { it.name == temaActual.name }

        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog)
            .setTitle("Seleccionar tema")
            .setSingleChoiceItems(nombresThemes, indiceActual) { dialog, which ->
                themeManager.setTheme(temas[which])
                recreate()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun reprogramarTodasLasAlarmas() {
        alarmHelper.reprogramarTodasLasAlarmas(medicamentos.filter { it.activo })
        Toast.makeText(this, "Alarmas reprogramadas", Toast.LENGTH_SHORT).show()
    }

    private fun toggleFiltroActivos() {
        mostrarSoloActivos = !mostrarSoloActivos
        filtrarMedicamentos()
        actualizarUI()
    }

    private fun mostrarEstadisticas() {
        val stats = storage.obtenerEstadisticas()
        val mensaje = """
            Estadísticas de medicamentos:
            
            Total: ${stats["total"]}
            Activos: ${stats["activos"]}
            Inactivos: ${stats["inactivos"]}
            Con notas: ${stats["conNotas"]}
            Con familiares: ${stats["conFamiliares"]}
            
            ${alarmHelper.obtenerInfoAlarmas()}
        """.trimIndent()

        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog)
            .setTitle("Estadísticas")
            .setMessage(mensaje)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun abrirConfiguracion() {
        val opciones = arrayOf(
            "Configurar notificaciones",
            "Exportar datos",
            "Importar datos",
            "Limpiar todos los datos"
        )

        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog)
            .setTitle("Configuración")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> abrirConfiguracionNotificaciones()
                    1 -> exportarDatos()
                    2 -> importarDatos()
                    3 -> confirmarLimpiarDatos()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirAgregarMedicamento() {
        Toast.makeText(this, "Agregar medicamento (próximamente)", Toast.LENGTH_SHORT).show()
    }

    private fun abrirConfiguracionNotificaciones() {
        val enabled = notificationHelper.notificacionesHabilitadas()
        val mensaje = if (enabled) {
            "Las notificaciones están habilitadas"
        } else {
            "Las notificaciones están deshabilitadas\n\nPor favor, habilítalas en la configuración del sistema"
        }

        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog)
            .setTitle("Estado de notificaciones")
            .setMessage(mensaje)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun exportarDatos() {
        val datos = storage.exportarDatos()
        if (datos != null) {
            AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog)
                .setTitle("Datos exportados")
                .setMessage("Datos listos para exportar\n(${datos.length} caracteres)")
                .setPositiveButton("OK", null)
                .show()
        } else {
            Toast.makeText(this, "Error exportando datos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importarDatos() {
        Toast.makeText(this, "Importar datos (próximamente)", Toast.LENGTH_SHORT).show()
    }

    private fun confirmarLimpiarDatos() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog)
            .setTitle("Limpiar todos los datos")
            .setMessage("¿Estás seguro? Esta acción eliminará todos los medicamentos y alarmas.")
            .setPositiveButton("Eliminar todo") { _, _ ->
                storage.limpiarTodos()
                cargarMedicamentos()
                actualizarUI()
                Toast.makeText(this, "Todos los datos eliminados", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }
}