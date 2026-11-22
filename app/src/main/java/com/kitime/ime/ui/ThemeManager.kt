package com.kitime.ime.ui

/**
 * Supported themes for Kit-IME.
 */
enum class KitTheme(val id: String, val title: String) {
    KAWAII_PURPLE("kawaii_purple", "🌸 Kawaiian Plumpitude (Default)"),
    DARK_SLATE("dark_slate", "Crisp Dark Slate 🌙"),
    LIGHT_LAVENDER("light_lavender", "Soft Light Lavender ☀️"),
    HIGH_CONTRAST("high_contrast", "High Contrast Amber ⚡")
}

/**
 * Color palette definition for theme styling.
 */
data class ThemePalette(
    val rootBackground: Int,
    val keyBackground: Int,
    val actionBackground: Int,
    val keyTextColor: Int,
    val actionTextColor: Int,
    val ribbonBackground: Int,
    val ribbonTextColor: Int,
    val accentColor: Int
)

/**
 * Theme manager providing persistent palettes across the IME and popup views.
 * Uses 0xFFxxxxxx hex literals rather than android.graphics.Color.parseColor so tests run on pure JVM.
 */
object ThemeManager {

    fun getPalette(theme: KitTheme): ThemePalette {
        return when (theme) {
            KitTheme.KAWAII_PURPLE -> ThemePalette(
                rootBackground = 0xFF2E1A47.toInt(),       // Deep Indigo
                keyBackground = 0xFF3D2660.toInt(),        // Medium Purple
                actionBackground = 0xFFE6A8D7.toInt(),     // Kawaii Pink
                keyTextColor = 0xFFE6E6FA.toInt(),         // Soft Lavender
                actionTextColor = 0xFF1E1E2E.toInt(),      // Dark Slate
                ribbonBackground = 0xFF1E1430.toInt(),     // Darker Indigo
                ribbonTextColor = 0xFFFFFBEB.toInt(),      // Amber Gold
                accentColor = 0xFFE6A8D7.toInt()
            )
            KitTheme.DARK_SLATE -> ThemePalette(
                rootBackground = 0xFF181825.toInt(),       // Catppuccin Crust
                keyBackground = 0xFF313244.toInt(),        // Catppuccin Surface0
                actionBackground = 0xFF89B4FA.toInt(),     // Soft Blue
                keyTextColor = 0xFFCDD6F4.toInt(),         // Light Text
                actionTextColor = 0xFF11111B.toInt(),
                ribbonBackground = 0xFF1E1E2E.toInt(),
                ribbonTextColor = 0xFFCDD6F4.toInt(),
                accentColor = 0xFF89B4FA.toInt()
            )
            KitTheme.LIGHT_LAVENDER -> ThemePalette(
                rootBackground = 0xFFF5F3FF.toInt(),       // Very Light Lavender
                keyBackground = 0xFFFFFFFF.toInt(),        // White Card
                actionBackground = 0xFFDDD6FE.toInt(),     // Lavender accent
                keyTextColor = 0xFF1E1B4B.toInt(),         // Dark Indigo text
                actionTextColor = 0xFF1E1B4B.toInt(),
                ribbonBackground = 0xFFEDE9FE.toInt(),
                ribbonTextColor = 0xFF1E1B4B.toInt(),
                accentColor = 0xFF7C3AED.toInt()
            )
            KitTheme.HIGH_CONTRAST -> ThemePalette(
                rootBackground = 0xFF000000.toInt(),       // Pure Black
                keyBackground = 0xFF1C1C1C.toInt(),        // Dark Grey
                actionBackground = 0xFFFFD700.toInt(),     // Vivid Gold
                keyTextColor = 0xFFFFFFFF.toInt(),         // Pure White
                actionTextColor = 0xFF000000.toInt(),
                ribbonBackground = 0xFF121212.toInt(),
                ribbonTextColor = 0xFFFFD700.toInt(),
                accentColor = 0xFFFFD700.toInt()
            )
        }
    }

    fun fromId(id: String): KitTheme {
        return KitTheme.values().find { it.id.equals(id, ignoreCase = true) } ?: KitTheme.KAWAII_PURPLE
    }
}
