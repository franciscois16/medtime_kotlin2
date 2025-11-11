package com.franciscois.medtime_kotlin

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.Log // <-- ¡IMPORTANTE! Importar Log
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var alertasListener: ListenerRegistration? = null

    // Etiqueta para Logcat
    private val TAG = "ListenerAlertas"

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

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted) {
                AlertDialog.Builder(this)
                    .setTitle("Permiso Denegado")
                    .setMessage("Sin el permiso de notificaciones, las alarmas no podrán sonar ni mostrarse.")
                    .setPositiveButton("Ok", null)
                    .show()
            }
        }
    private val requestOverlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        db = Firebase.firestore

        signInAnonymously() // Esto obtiene el UID

        initializeHelpers()
        findViews()
        setupWindowInsets()
        setupRecyclerView()
        setupClickListeners()
        cargarYActualizarUI()
        manejarIntent(intent)

        checkRequiredPermissions()
    }

    override fun onResume() {
        super.onResume()
        cargarYActualizarUI()
        checkRequiredPermissions()
        iniciarListenerAlertas() // Iniciar el listener
    }

    override fun onPause() {
        super.onPause()
        detenerListenerAlertas() // Detener el listener
    }

    private fun signInAnonymously() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.i(TAG, "No hay usuario, iniciando sesión anónima...")
            auth.signInAnonymously()
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        val uid = user?.uid ?: "error_uid"
                        Log.i(TAG, "✅ Inicio de sesión anónimo exitoso. UID: $uid")
                        val prefs = getSharedPreferences("MedTimePrefs", Context.MODE_PRIVATE)
                        with(prefs.edit()) {
                            putString("USER_UID", uid)
                            apply()
                        }
                        checkFirstRunAndAskForName()
                        iniciarListenerAlertas()
                    } else {
                        Log.w(TAG, "❌ Falló el inicio de sesión anónimo.", task.exception)
                        Toast.makeText(baseContext, "Error de autenticación.", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            val uid = currentUser.uid
            Log.i(TAG, "✅ Usuario ya autenticado. UID: $uid")
            val prefs = getSharedPreferences("MedTimePrefs", Context.MODE_PRIVATE)
            with(prefs.edit()) {
                putString("USER_UID", uid)
                apply()
            }
            checkFirstRunAndAskForName()
            iniciarListenerAlertas()
        }
    }

    private fun iniciarListenerAlertas() {
        detenerListenerAlertas()

        val prefs = getSharedPreferences("MedTimePrefs", Context.MODE_PRIVATE)
        val myUid = prefs.getString("USER_UID", null)
        if (myUid == null) {
            Log.w(TAG, "Esperando UID... El listener no puede iniciar.")
            return
        }

        Log.i(TAG, "Configurando listener para cuidador UID: $myUid")

        db.collection("vinculos")
            .whereEqualTo("cuidadorUid", myUid)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Log.i(TAG, "Este usuario no es cuidador de nadie.")
                    return@addOnSuccessListener
                }

                val pacientesIds = snapshot.documents.mapNotNull { it.getString("pacienteUid") }
                if (pacientesIds.isEmpty()) {
                    Log.i(TAG, "No se encontraron IDs de pacientes en los vínculos.")
                    return@addOnSuccessListener
                }

                Log.i(TAG, "Siguiendo a pacientes: $pacientesIds")

                val unaHoraAtras = System.currentTimeMillis() - 3600 * 1000

                alertasListener = db.collection("alertas")
                    .whereIn("pacienteUid", pacientesIds)
                    .whereEqualTo("vistaPorCuidador", false)
                    .whereGreaterThan("createdAt", unaHoraAtras)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshots, e ->
                        if (e != null) {
                            Log.e(TAG, "Error en el listener de alertas:", e)
                            return@addSnapshotListener
                        }

                        if (snapshots == null) return@addSnapshotListener

                        Log.i(TAG, "¡Nuevos eventos recibidos! (${snapshots.documentChanges.size} cambios)")
                        for (doc in snapshots.documentChanges) {
                            if (doc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                                val alerta = doc.document
                                val pacienteName = alerta.getString("pacienteName") ?: "Alguien"
                                val medName = alerta.getString("medicamentoName") ?: "un medicamento"
                                val tipo = alerta.getString("tipo") ?: "OMITIDO"

                                val titulo = "Alerta de $pacienteName"
                                val mensaje = if (tipo == "TOMADO") {
                                    "✅ $pacienteName ha marcado '$medName' como tomado."
                                } else {
                                    "⚠️ $pacienteName NO ha marcado '$medName'."
                                }

                                // --- 1. MENSAJE EN LOGCAT ---
                                Log.i(TAG, "Mostrando notificación local: $mensaje")
                                notificationHelper.mostrarNotificacionLocal(titulo, mensaje, alerta.id.hashCode())

                                // --- 2. CONFIRMACIÓN A FIREBASE ---
                                db.collection("alertas").document(alerta.id)
                                    .update("vistaPorCuidador", true)
                                    .addOnSuccessListener { Log.i(TAG, "Alerta ${alerta.id} marcada como vista.") }
                                    .addOnFailureListener { Log.w(TAG, "Error al marcar alerta ${alerta.id} como vista.") }
                            }
                        }
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error buscando vínculos:", e)
            }
    }

    private fun detenerListenerAlertas() {
        alertasListener?.remove()
        alertasListener = null
        Log.i(TAG, "Listener detenido.")
    }


    private fun checkFirstRunAndAskForName() {
        val prefs = getSharedPreferences("MedTimePrefs", Context.MODE_PRIVATE)
        val hasUserSetName = prefs.getBoolean("HAS_USER_SET_NAME", false)
        val uid = prefs.getString("USER_UID", null)
        if (!hasUserSetName && uid != null) {
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                hint = "Ej: Francisco"
                setPadding(50, 50, 50, 50)
            }
            val dialog = AlertDialog.Builder(this)
                .setTitle("¡Bienvenido a MedTime!")
                .setMessage("Ingresa tu nombre para personalizar las notificaciones (puedes cambiarlo después).")
                .setView(input)
                .setPositiveButton("Guardar") { _, _ ->
                    val nombre = input.text.toString().trim().ifEmpty { "Usuario" }
                    val userProfile = hashMapOf("name" to nombre, "uid" to uid, "createdAt" to System.currentTimeMillis())
                    db.collection("users").document(uid).set(userProfile)
                        .addOnSuccessListener {
                            Log.i(TAG, "✅ Perfil de usuario '$nombre' guardado en Firestore.")
                            with(prefs.edit()) {
                                putString("USER_NAME", nombre)
                                putBoolean("HAS_USER_SET_NAME", true)
                                apply()
                            }
                            Toast.makeText(this, "¡Hola, $nombre!", Toast.LENGTH_SHORT).show()
                            actualizarUI()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "❌ Error al guardar perfil en Firestore:", e)
                            Toast.makeText(this, "Error al guardar perfil. Inténtalo de nuevo.", Toast.LENGTH_SHORT).show()
                        }
                }
                .setNegativeButton("Omitir") { _, _ ->
                    val nombre = "Usuario"
                    val userProfile = hashMapOf("name" to nombre, "uid" to uid, "createdAt" to System.currentTimeMillis())
                    db.collection("users").document(uid).set(userProfile)
                        .addOnCompleteListener {
                            with(prefs.edit()) {
                                putString("USER_NAME", nombre)
                                putBoolean("HAS_USER_SET_NAME", true)
                                apply()
                            }
                            actualizarUI()
                        }
                }
                .setCancelable(false)
                .create()
            dialog.show()
        }
    }

    private fun checkRequiredPermissions() {
        checkNotificationPermission()
        checkDrawOverlayPermission()
        checkFullScreenIntentPermission()
    }

    private fun checkDrawOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showPermissionDialog(
                "Permiso Necesario: Aparecer Encima",
                "Para mostrar la alarma sobre la pantalla de bloqueo, MedTime necesita este permiso.",
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION
            ) {
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

    private fun checkFullScreenIntentPermission() {
        val notificationManager = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!notificationManager.areNotificationsEnabled()) {
                showPermissionDialog(
                    "Notificaciones Desactivadas",
                    "Las notificaciones para MedTime están desactivadas. Necesitas activarlas para recibir alarmas.",
                    Settings.ACTION_APP_NOTIFICATION_SETTINGS
                )
                return
            }

            val prefs = getSharedPreferences("MedTimePrefs", Context.MODE_PRIVATE)
            val firstCheck = prefs.getBoolean("first_fullscreen_check", true)

            if (firstCheck || !notificationManager.canUseFullScreenIntent()) {
                showPermissionDialog(
                    "Ajuste Importante (Samsung)",
                    "Para asegurar que la alarma aparezca en pantalla completa:\n\n" +
                            "1. Toca 'Ir a Ajustes'.\n" +
                            "2. Entra en 'Categorías de notificación'.\n" +
                            "3. Selecciona 'Alarmas de Medicamentos'.\n" +
                            "4. **ACTIVA 'Mostrar como ventana emergente'** y asegúrate que la importancia sea 'Urgente'.",
                    Settings.ACTION_APP_NOTIFICATION_SETTINGS
                )
                prefs.edit().putBoolean("first_fullscreen_check", false).apply()
            }
        }
    }

    private fun showPermissionDialog(title: String, message: String, settingsAction: String, positiveAction: (() -> Unit)? = null) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Ir a Ajustes") { _, _ ->
                if (positiveAction != null) {
                    positiveAction.invoke()
                } else {
                    val intent = Intent(settingsAction).apply {
                        if (settingsAction == Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        } else {
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
            .setCancelable(false)
            .show()
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
        val prefs = getSharedPreferences("MedTimePrefs", Context.MODE_PRIVATE)
        val userName = prefs.getString("USER_NAME", null)
        headerTitle.text = if (userName != null && userName != "Usuario") userName else "MedTime"
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
        val prefs = getSharedPreferences("MedTimePrefs", Context.MODE_PRIVATE)
        val myUid = prefs.getString("USER_UID", null)

        val opciones = arrayOf(
            "Mi Código para Compartir",
            "Añadir Paciente (Soy Cuidador)",
            "Cambiar tema",
            if (mostrarSoloActivos) "Mostrar todos" else "Mostrar solo activos"
        )
        AlertDialog.Builder(this)
            .setTitle("Opciones")
            .setItems(opciones) { dialog, which ->
                when(which) {
                    0 -> mostrarMiCodigo(myUid)
                    1 -> anadirPaciente()
                    2 -> Toast.makeText(this, "Cambiar tema (próximamente)", Toast.LENGTH_SHORT).show()
                    3 -> {
                        mostrarSoloActivos = !mostrarSoloActivos
                        cargarYActualizarUI()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarMiCodigo(myUid: String?) {
        if (myUid == null) {
            Toast.makeText(this, "Error: No se pudo obtener tu ID. Reinicia la app.", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Tu Código para Compartir")
            .setMessage("Comparte este código con tu cuidador para que pueda recibir tus alertas:\n\n$myUid")
            .setPositiveButton("Copiar") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("UserID", myUid)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "¡Código copiado!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun anadirPaciente() {
        val prefs = getSharedPreferences("MedTimePrefs", Context.MODE_PRIVATE)
        val myUid = prefs.getString("USER_UID", null)

        if (myUid == null) {
            Toast.makeText(this, "Error: No se pudo obtener tu ID. Reinicia la app.", Toast.LENGTH_LONG).show()
            return
        }

        val input = EditText(this).apply {
            hint = "Pega el código del paciente aquí"
            setPadding(50, 50, 50, 50)
        }

        AlertDialog.Builder(this)
            .setTitle("Añadir Paciente")
            .setMessage("Pide al paciente su 'Código para Compartir' y pégalo aquí.")
            .setView(input)
            .setPositiveButton("Añadir") { _, _ ->
                val pacienteUid = input.text.toString().trim()
                if (pacienteUid.isNotEmpty() && pacienteUid != myUid) {
                    vincularCuidadorAPaciente(myUid, pacienteUid)
                } else if (pacienteUid == myUid) {
                    Toast.makeText(this, "No puedes añadirte a ti mismo.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Código no válido.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun vincularCuidadorAPaciente(cuidadorUid: String, pacienteUid: String) {
        val vinculoId = "${pacienteUid}_$cuidadorUid"
        val vinculo = hashMapOf(
            "pacienteUid" to pacienteUid,
            "cuidadorUid" to cuidadorUid,
            "estado" to "activo",
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("vinculos").document(vinculoId)
            .set(vinculo)
            .addOnSuccessListener {
                Log.i(TAG, "✅ Vínculo creado: Cuidador $cuidadorUid sigue a Paciente $pacienteUid")
                Toast.makeText(this, "¡Paciente añadido exitosamente!", Toast.LENGTH_SHORT).show()
                iniciarListenerAlertas() // Reiniciar el listener para incluir al nuevo paciente
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Error al crear vínculo:", e)
                Toast.makeText(this, "Error al añadir paciente.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun manejarIntent(intent: Intent?) {
        intent?.getStringExtra("medicamento_id")?.let { id ->
            storage.buscarMedicamento(id)?.let { med ->
                verDetalles(med)
            }
        }
    }
}