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
        // 🌊 Océano Profundo (estilo navy intenso)
        AppTheme(
            name = "Océano Profundo",
            primary = "#0F4C75",
            primaryDark = "#072540",
            secondary = "#3282B8",
            background = "#0A2E4C",       // Azul marino oscuro
            backgroundSecondary = "#133B5C",
            surface = "#1B3A57",
            onPrimary = "#FFFFFF",
            onBackground = "#F5F5F5",
            onSurface = "#E0E0E0",
            accent = "#00A8E8",
            warning = "#FF6B6B",
            success = "#4ECDC4",
            textPrimary = "#FFFFFF",
            textSecondary = "#CCCCCC",
            textHint = "#999999",
            cardBackground = "#1C3F5C",
            divider = "#2E5D7B"
        ),

        // 🌲 Bosque Místico (oscuro, natural)
        AppTheme(
            name = "Bosque Místico",
            primary = "#2D4A22",
            primaryDark = "#182C13",
            secondary = "#4A7C59",
            background = "#1B2E1A",       // Verde bosque profundo
            backgroundSecondary = "#233D24",
            surface = "#2E4B32",
            onPrimary = "#FFFFFF",
            onBackground = "#F0F0F0",
            onSurface = "#E0E0E0",
            accent = "#6A994E",
            warning = "#F77F00",
            success = "#52B788",
            textPrimary = "#FFFFFF",
            textSecondary = "#CCCCCC",
            textHint = "#AAAAAA",
            cardBackground = "#324C3A",
            divider = "#405A46"
        ),

        // 🌌 Lavanda Nocturna (morado intenso)
        AppTheme(
            name = "Lavanda Nocturna",
            primary = "#6B5B95",
            primaryDark = "#3E2F5A",
            secondary = "#88689A",
            background = "#2E1B47",       // Violeta oscuro
            backgroundSecondary = "#3A2556",
            surface = "#463261",
            onPrimary = "#FFFFFF",
            onBackground = "#F5F5F5",
            onSurface = "#E0E0E0",
            accent = "#BB86FC",
            warning = "#E74C3C",
            success = "#27AE60",
            textPrimary = "#FFFFFF",
            textSecondary = "#CCCCCC",
            textHint = "#AAAAAA",
            cardBackground = "#3C2A55",
            divider = "#5A4377"
        ),

        // 🧊 Glaciar (azul frío oscuro)
        AppTheme(
            name = "Glaciar",
            primary = "#4A90A4",
            primaryDark = "#285566",
            secondary = "#5AA3B8",
            background = "#1B2C34",       // Azul glaciar oscuro
            backgroundSecondary = "#233C47",
            surface = "#2E4A56",
            onPrimary = "#FFFFFF",
            onBackground = "#F0F0F0",
            onSurface = "#DDDDDD",
            accent = "#7DCED0",
            warning = "#E67E22",
            success = "#16A085",
            textPrimary = "#FFFFFF",
            textSecondary = "#CCCCCC",
            textHint = "#AAAAAA",
            cardBackground = "#2F4C58",
            divider = "#3F6271"
        ),

        // 🌅 Crepúsculo (marrón y dorado intenso)
        AppTheme(
            name = "Crepúsculo",
            primary = "#8B7355",
            primaryDark = "#5E4631",
            secondary = "#A08B7A",
            background = "#3B1F1A",       // Marrón oscuro
            backgroundSecondary = "#4E2C25",
            surface = "#5C3A2E",
            onPrimary = "#FFFFFF",
            onBackground = "#FAFAFA",
            onSurface = "#E0E0E0",
            accent = "#EBA83A",
            warning = "#D35400",
            success = "#229954",
            textPrimary = "#FFFFFF",
            textSecondary = "#DDDDDD",
            textHint = "#AAAAAA",
            cardBackground = "#533025",
            divider = "#6E4B3F"
        ),

        // ⚫ Minimalista (gris carbón)
        AppTheme(
            name = "Minimalista",
            primary = "#2C3E50",
            primaryDark = "#1B2631",
            secondary = "#34495E",
            background = "#121212",       // Gris carbón
            backgroundSecondary = "#1E1E1E",
            surface = "#2C2C2C",
            onPrimary = "#FFFFFF",
            onBackground = "#EAEAEA",
            onSurface = "#D0D0D0",
            accent = "#5D6D7E",
            warning = "#E74C3C",
            success = "#27AE60",
            textPrimary = "#FFFFFF",
            textSecondary = "#BBBBBB",
            textHint = "#888888",
            cardBackground = "#242424",
            divider = "#3C3C3C"
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