package com.franciscois.medtime_kotlin.adapters

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.franciscois.medtime_kotlin.models.Medicamento
import com.franciscois.medtime_kotlin.utils.AlarmHelper
import com.franciscois.medtime_kotlin.utils.ThemeManager
import java.text.SimpleDateFormat
import java.util.*

class MedicationAdapter(
    private var medicamentos: List<Medicamento>,
    private val onAction: (Medicamento, String) -> Unit
) : RecyclerView.Adapter<MedicationAdapter.MedicationViewHolder>() {

    companion object {
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_DELETE = "delete"
        const val ACTION_EDIT = "edit"
        const val ACTION_TEST_ALARM = "test_alarm"
        const val ACTION_VIEW_DETAILS = "view_details"
    }

    class MedicationViewHolder(val cardLayout: LinearLayout) : RecyclerView.ViewHolder(cardLayout) {
        val nombreText: TextView
        val horaText: TextView
        val frecuenciaText: TextView
        val proximaAlarmaText: TextView
        val notasText: TextView
        val switchActivo: Switch
        val testButton: ImageButton
        val statusIndicator: View

        init {
            val context = itemView.context
            val themeManager = ThemeManager.getInstance(context)

            cardLayout.apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 24, 20, 24)

                val params = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(20, 16, 20, 16)
                layoutParams = params

                background = themeManager.createCardBackground()
                elevation = 12f
            }

            statusIndicator = View(context).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 3f
                    setColor(themeManager.getSuccessColor())
                }
                val params = LinearLayout.LayoutParams(6, 50)
                params.gravity = Gravity.START
                params.setMargins(0, 0, 0, 16)
                layoutParams = params
            }
            cardLayout.addView(statusIndicator)

            val headerLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 20)
            }

            nombreText = TextView(context).apply {
                textSize = 22f
                setTextColor(themeManager.getTextPrimaryColor())
                setSingleLine(false)
            }
            headerLayout.addView(nombreText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            switchActivo = Switch(context).apply {
                scaleX = 1.2f
                scaleY = 1.2f
            }
            headerLayout.addView(switchActivo)
            cardLayout.addView(headerLayout)

            horaText = TextView(context).apply {
                textSize = 48f
                setTextColor(themeManager.getAccentColor())
                setPadding(0, 0, 0, 16)
            }
            cardLayout.addView(horaText)

            frecuenciaText = TextView(context).apply {
                textSize = 15f
                setTextColor(themeManager.getTextSecondaryColor())
                setPadding(12, 6, 12, 6)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(themeManager.getBackgroundSecondaryColor())
                    cornerRadius = 8f
                }
            }
            cardLayout.addView(frecuenciaText)

            proximaAlarmaText = TextView(context).apply {
                textSize = 13f
                setTextColor(android.graphics.Color.WHITE)
                setPadding(16, 8, 16, 8)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(themeManager.getWarningColor())
                    cornerRadius = 16f
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 8, 0, 0)
                layoutParams = params
            }
            cardLayout.addView(proximaAlarmaText)

            notasText = TextView(context).apply {
                textSize = 14f
                setTextColor(themeManager.getTextPrimaryColor())
                setSingleLine(false)
                setPadding(16, 12, 16, 12)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(themeManager.getBackgroundColor())
                    cornerRadius = 12f
                    setStroke(1, themeManager.getDividerColor())
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 16, 0, 0)
                layoutParams = params
            }
            cardLayout.addView(notasText)

            testButton = ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_media_play)
                background = themeManager.createAccentButton()
                setColorFilter(android.graphics.Color.WHITE)
                elevation = 8f
                val params = LinearLayout.LayoutParams(56, 56)
                params.gravity = Gravity.END
                params.setMargins(0, 16, 0, 0)
                layoutParams = params
            }
            cardLayout.addView(testButton)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicationViewHolder {
        return MedicationViewHolder(LinearLayout(parent.context))
    }

    override fun onBindViewHolder(holder: MedicationViewHolder, position: Int) {
        val medicamento = medicamentos[position]
        val themeManager = ThemeManager.getInstance(holder.itemView.context)

        holder.nombreText.text = medicamento.nombre

        if (medicamento.activo && medicamento.proximaAlarma > 0) {
            val formatoHora = SimpleDateFormat("HH:mm", Locale.getDefault())
            holder.horaText.text = formatoHora.format(Date(medicamento.proximaAlarma))
        } else {
            holder.horaText.text = "--:--"
        }

        holder.frecuenciaText.text = medicamento.frecuenciaTexto

        if (medicamento.activo && medicamento.proximaAlarma > 0) {
            val alarmHelper = AlarmHelper.getInstance(holder.itemView.context)
            val tiempoRestante = alarmHelper.formatearTiempoRestante(medicamento.proximaAlarma)
            holder.proximaAlarmaText.text = "Próxima en $tiempoRestante"
            holder.proximaAlarmaText.visibility = View.VISIBLE
        } else {
            holder.proximaAlarmaText.visibility = View.GONE
        }

        if (medicamento.notas.isNotEmpty()) {
            holder.notasText.text = medicamento.notas
            holder.notasText.visibility = View.VISIBLE
        } else {
            holder.notasText.visibility = View.GONE
        }

        holder.cardLayout.alpha = if (medicamento.activo) 1.0f else 0.6f
        holder.statusIndicator.setBackgroundColor(
            if (medicamento.activo) themeManager.getSuccessColor()
            else android.graphics.Color.parseColor("#757575")
        )

        holder.switchActivo.setOnCheckedChangeListener(null)
        holder.switchActivo.isChecked = medicamento.activo
        holder.switchActivo.setOnCheckedChangeListener { _, _ ->
            onAction(medicamento, ACTION_TOGGLE)
        }

        holder.testButton.setOnClickListener { onAction(medicamento, ACTION_TEST_ALARM) }
        holder.cardLayout.setOnClickListener { onAction(medicamento, ACTION_VIEW_DETAILS) }

        // --- INICIO DE LA MODIFICACIÓN ---
        holder.cardLayout.setOnLongClickListener { view ->
            val popup = PopupMenu(view.context, view, Gravity.END)
            popup.menu.add(ACTION_EDIT).title = "Editar"
            popup.menu.add(ACTION_DELETE).title = "Eliminar"
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.title) {
                    "Editar" -> onAction(medicamento, ACTION_EDIT)
                    "Eliminar" -> onAction(medicamento, ACTION_DELETE)
                }
                true
            }
            popup.show()
            true
        }
        // --- FIN DE LA MODIFICACIÓN ---
    }

    override fun getItemCount() = medicamentos.size

    fun actualizarMedicamentos(nuevaLista: List<Medicamento>) {
        medicamentos = nuevaLista
        notifyDataSetChanged()
    }
}