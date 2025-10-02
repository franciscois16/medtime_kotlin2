package com.franciscois.medtime_kotlin.utils

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

data class AppTheme(
    val name: String,
    val primary: String,
    val primaryDark: String,
    val secondary: String,
    val background: String,
    val backgroundSecondary: String,
    val surface: String,
    val onPrimary: String,
    val onBackground: String,
    val onSurface: String,
    val accent: String,
    val warning: String,
    val success: String,
    val textPrimary: String,
    val textSecondary: String,
    val textHint: String,
    val cardBackground: String,
    val divider: String
)

class ThemeManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE)
    private val currentThemeKey = "selected_theme"

    companion object {
        @Volatile
        private var INSTANCE: ThemeManager? = null

        fun getInstance(context: Context): ThemeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemeManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // Temas disponibles inspirados en paletas frías
    private val availableThemes = listOf(
        // 🌊 Océano Profundo (azules con más contraste)
        AppTheme(
            name = "Océano Profundo",
            primary = "#0F4C75",
            primaryDark = "#062238",
            secondary = "#3FA9F5",
            background = "#081C2C",         // Más oscuro que antes
            backgroundSecondary = "#133B5C",
            surface = "#1A3857",
            onPrimary = "#FFFFFF",
            onBackground = "#F5F5F5",
            onSurface = "#E0E0E0",
            accent = "#00CFFF",             // Más brillante
            warning = "#FF5A5A",
            success = "#36E0C2",
            textPrimary = "#FFFFFF",
            textSecondary = "#D9D9D9",
            textHint = "#AAAAAA",
            cardBackground = "#102F4C",
            divider = "#357099"
        ),

        // 🌲 Bosque Místico (verdes más notorios)
        AppTheme(
            name = "Bosque Místico",
            primary = "#2D4A22",
            primaryDark = "#111F0E",
            secondary = "#5FA36F",
            background = "#121E12",         // Más profundo
            backgroundSecondary = "#1F3221",
            surface = "#29442D",
            onPrimary = "#FFFFFF",
            onBackground = "#F0F0F0",
            onSurface = "#E0E0E0",
            accent = "#88CC66",             // Verde más vivo
            warning = "#FF8C1A",
            success = "#4CD889",
            textPrimary = "#FFFFFF",
            textSecondary = "#DDDDDD",
            textHint = "#AAAAAA",
            cardBackground = "#243926",
            divider = "#3D5C41"
        ),

        // 🌌 Lavanda Nocturna (morado con mayor saturación)
        AppTheme(
            name = "Lavanda Nocturna",
            primary = "#7B68EE",
            primaryDark = "#2A1C4F",
            secondary = "#9B72CF",
            background = "#1C1033",         // Más oscuro
            backgroundSecondary = "#2E1B47",
            surface = "#3B275C",
            onPrimary = "#FFFFFF",
            onBackground = "#F5F5F5",
            onSurface = "#E0E0E0",
            accent = "#CBA6FF",             // Más brillante
            warning = "#FF3B3B",
            success = "#2ECC71",
            textPrimary = "#FFFFFF",
            textSecondary = "#D6CBE3",
            textHint = "#AAAAAA",
            cardBackground = "#2A1C44",
            divider = "#5C3F77"
        ),

        // 🧊 Glaciar (azules fríos más claros)
        AppTheme(
            name = "Glaciar",
            primary = "#5AB6D0",
            primaryDark = "#123844",
            secondary = "#73C2E1",
            background = "#0E1D23",         // Más profundo
            backgroundSecondary = "#1E3A45",
            surface = "#2D4954",
            onPrimary = "#FFFFFF",
            onBackground = "#F0F0F0",
            onSurface = "#E0E0E0",
            accent = "#96E8EB",             // Celeste brillante
            warning = "#FF8C42",
            success = "#1ABC9C",
            textPrimary = "#FFFFFF",
            textSecondary = "#DDEDED",
            textHint = "#AAAAAA",
            cardBackground = "#183943",
            divider = "#3C6271"
        ),

        // 🌅 Crepúsculo (marrón-dorado más intenso)
        AppTheme(
            name = "Crepúsculo",
            primary = "#B8860B",
            primaryDark = "#402810",
            secondary = "#C4976A",
            background = "#2A120A",         // Mucho más oscuro
            backgroundSecondary = "#3E1E15",
            surface = "#543626",
            onPrimary = "#FFFFFF",
            onBackground = "#FAFAFA",
            onSurface = "#E0E0E0",
            accent = "#FFB84D",             // Dorado vivo
            warning = "#FF6B00",
            success = "#2ECC71",
            textPrimary = "#FFFFFF",
            textSecondary = "#E8D5C1",
            textHint = "#AAAAAA",
            cardBackground = "#361D12",
            divider = "#6E4B3F"
        ),

        // ⚫ Minimalista (gris carbón con contraste marcado)
        AppTheme(
            name = "Minimalista",
            primary = "#2C3E50",
            primaryDark = "#0D1117",
            secondary = "#4B637A",
            background = "#0A0A0A",         // Más oscuro que antes
            backgroundSecondary = "#1C1C1C",
            surface = "#2C2C2C",
            onPrimary = "#FFFFFF",
            onBackground = "#F5F5F5",
            onSurface = "#D0D0D0",
            accent = "#85929E",             // Gris azulado claro
            warning = "#FF4D4D",
            success = "#33CC66",
            textPrimary = "#FFFFFF",
            textSecondary = "#CCCCCC",
            textHint = "#888888",
            cardBackground = "#1A1A1A",
            divider = "#3D3D3D"
        )
    )



    fun getAvailableThemes(): List<AppTheme> = availableThemes

    fun getCurrentTheme(): AppTheme {
        val savedThemeName = prefs.getString(currentThemeKey, availableThemes[0].name)
        return availableThemes.find { it.name == savedThemeName } ?: availableThemes[0]
    }

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString(currentThemeKey, theme.name).apply()
    }

    fun setThemeByName(themeName: String) {
        val theme = availableThemes.find { it.name == themeName }
        theme?.let { setTheme(it) }
    }

    // Métodos de conveniencia para obtener colores como Int
    fun getPrimaryColor(): Int = Color.parseColor(getCurrentTheme().primary)
    fun getPrimaryDarkColor(): Int = Color.parseColor(getCurrentTheme().primaryDark)
    fun getSecondaryColor(): Int = Color.parseColor(getCurrentTheme().secondary)
    fun getBackgroundColor(): Int = Color.parseColor(getCurrentTheme().background)
    fun getBackgroundSecondaryColor(): Int = Color.parseColor(getCurrentTheme().backgroundSecondary)
    fun getSurfaceColor(): Int = Color.parseColor(getCurrentTheme().surface)
    fun getOnPrimaryColor(): Int = Color.parseColor(getCurrentTheme().onPrimary)
    fun getOnBackgroundColor(): Int = Color.parseColor(getCurrentTheme().onBackground)
    fun getOnSurfaceColor(): Int = Color.parseColor(getCurrentTheme().onSurface)
    fun getAccentColor(): Int = Color.parseColor(getCurrentTheme().accent)
    fun getWarningColor(): Int = Color.parseColor(getCurrentTheme().warning)
    fun getSuccessColor(): Int = Color.parseColor(getCurrentTheme().success)
    fun getTextPrimaryColor(): Int = Color.parseColor(getCurrentTheme().textPrimary)
    fun getTextSecondaryColor(): Int = Color.parseColor(getCurrentTheme().textSecondary)
    fun getTextHintColor(): Int = Color.parseColor(getCurrentTheme().textHint)
    fun getCardBackgroundColor(): Int = Color.parseColor(getCurrentTheme().cardBackground)
    fun getDividerColor(): Int = Color.parseColor(getCurrentTheme().divider)

    // Crear gradientes comunes
    fun createPrimaryGradient(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            colors = intArrayOf(getPrimaryColor(), getPrimaryDarkColor())
            orientation = android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
        }
    }

    fun createBackgroundGradient(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            colors = intArrayOf(getBackgroundColor(), getBackgroundSecondaryColor(), getSurfaceColor())
            orientation = android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
        }
    }

    fun createCardBackground(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(getCardBackgroundColor())
            cornerRadius = 18f
            setStroke(1, getDividerColor())
        }
    }

    fun createAccentButton(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            colors = intArrayOf(getAccentColor(), getPrimaryDarkColor())
            orientation = android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
        }
    }
}