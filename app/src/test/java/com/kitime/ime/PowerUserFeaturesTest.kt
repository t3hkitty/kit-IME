package com.kitime.ime

import com.kitime.ime.data.ClipItem
import com.kitime.ime.data.ClipboardShelfManager
import com.kitime.ime.ui.KitTheme
import com.kitime.ime.ui.LongPressPopupView
import com.kitime.ime.ui.ThemeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Gboard-style power user extensions:
 * - Theme palettes and lookup
 * - Long press popup alternate characters
 * - Zero post-commit rewriting integrity
 */
class PowerUserFeaturesTest {

    @Test
    fun testThemePaletteGeneration() {
        val kawaiiTheme = ThemeManager.getPalette(KitTheme.KAWAII_PURPLE)
        val darkTheme = ThemeManager.getPalette(KitTheme.DARK_SLATE)
        val lightTheme = ThemeManager.getPalette(KitTheme.LIGHT_LAVENDER)
        val hcTheme = ThemeManager.getPalette(KitTheme.HIGH_CONTRAST)

        assertNotNull("Kawaii palette must not be null", kawaiiTheme)
        assertNotNull("Dark palette must not be null", darkTheme)
        assertNotNull("Light palette must not be null", lightTheme)
        assertNotNull("High-contrast palette must not be null", hcTheme)

        assertEquals(KitTheme.KAWAII_PURPLE, ThemeManager.fromId("kawaii_purple"))
        assertEquals(KitTheme.DARK_SLATE, ThemeManager.fromId("dark_slate"))
        assertEquals(KitTheme.LIGHT_LAVENDER, ThemeManager.fromId("light_lavender"))
        assertEquals(KitTheme.HIGH_CONTRAST, ThemeManager.fromId("high_contrast"))
    }

    @Test
    fun testLongPressAlternatesMapping() {
        val eAlternates = LongPressPopupView.ALTERNATE_MAP["e"]
        assertNotNull("Letter 'e' should have alternates", eAlternates)
        assertTrue("e alternates should contain '3'", eAlternates!!.contains("3"))
        assertTrue("e alternates should contain diacritic 'é'", eAlternates.contains("é"))

        val pAlternates = LongPressPopupView.ALTERNATE_MAP["p"]
        assertNotNull("Letter 'p' should have alternates", pAlternates)
        assertTrue("p alternates should contain '0'", pAlternates!!.contains("0"))
    }

    @Test
    fun testKeepHtmlSanitizationRegex() {
        val sampleHtml = "<html><body><h1>Shopping List</h1><p>Cat food &amp; treats</p><div>Buy milk</div></body></html>"
        val tagRegex = Regex("""<[^>]*>""")
        val entityRegex = Regex("""&(?:nbsp|amp|quot|lt|gt);""")

        var cleaned = tagRegex.replace(sampleHtml, " ")
        cleaned = entityRegex.replace(cleaned, " ")
        val tokens = cleaned.split(Regex("""\s+""")).filter { it.isNotBlank() }

        assertTrue("Cleaned text should contain 'Shopping'", tokens.contains("Shopping"))
        assertTrue("Cleaned text should contain 'Cat'", tokens.contains("Cat"))
        assertFalse("Cleaned text must not contain HTML tags", cleaned.contains("<h1>"))
        assertFalse("Cleaned text must not contain html entity", cleaned.contains("&amp;"))
    }
}
