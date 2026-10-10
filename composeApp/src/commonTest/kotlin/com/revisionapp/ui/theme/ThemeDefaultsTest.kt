package com.revisionapp.ui.theme

import androidx.compose.ui.graphics.Color
import com.revisionapp.ui.SettingsUi
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeDefaultsTest {
    @Test
    fun newAndUnconfiguredProfilesFollowTheSystemAppearance() {
        assertEquals(ThemeMode.SYSTEM, SettingsUi.initial().themeMode)
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
    }

    @Test
    fun anExplicitlyStoredAppearanceChoiceIsPreserved() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("SYSTEM"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStored("DARK"))
    }

    @Test
    fun bothColourStylesKeepTextAndControlsHighContrastInLightAndDarkModes() {
        listOf(InkPaperLight, InkPaperDark, PlayfulLight, PlayfulDark).forEach { scheme ->
            assertAa(scheme.onBackground, scheme.background, "background text")
            assertAa(scheme.onSurface, scheme.surface, "surface text")
            assertAa(scheme.onSurfaceVariant, scheme.surfaceVariant, "secondary surface text")
            assertAa(scheme.onPrimary, scheme.primary, "primary action text")
            assertAa(scheme.onPrimaryContainer, scheme.primaryContainer, "primary panel text")
            assertAa(scheme.onSecondary, scheme.secondary, "secondary action text")
            assertAa(scheme.onSecondaryContainer, scheme.secondaryContainer, "secondary panel text")
            assertAa(scheme.onTertiary, scheme.tertiary, "tertiary action text")
            assertAa(scheme.onTertiaryContainer, scheme.tertiaryContainer, "tertiary panel text")
            assertAa(scheme.onError, scheme.error, "error action text")
            assertAa(scheme.onErrorContainer, scheme.errorContainer, "error panel text")
            assertTrue(
                contrastRatio(scheme.outlineVariant, scheme.surface) >= 3.0,
                "control outline should be distinguishable",
            )
        }
    }

    @Test
    fun correctAnswerFeedbackIsGreenAndReadableInBothAppearances() {
        listOf(InkPaperExtendedLight, InkPaperExtendedDark).forEach { colors ->
            assertTrue(colors.correct.green > colors.correct.red, "correct-answer accent should be green")
            assertAa(colors.onCorrect, colors.correct, "correct-answer badge text")
            assertAa(colors.correct, colors.correctContainer, "correct-answer accent")
            assertAa(colors.onCorrectContainer, colors.correctContainer, "correct-answer panel text")
        }
    }
}

private fun assertAa(foreground: Color, background: Color, label: String) {
    assertTrue(
        contrastRatio(foreground, background) >= 4.5,
        "$label contrast should meet WCAG AA; got ${contrastRatio(foreground, background)}",
    )
}

private fun contrastRatio(foreground: Color, background: Color): Double {
    val first = relativeLuminance(foreground)
    val second = relativeLuminance(background)
    val lighter = maxOf(first, second)
    val darker = minOf(first, second)
    return (lighter + 0.05) / (darker + 0.05)
}

private fun relativeLuminance(color: Color): Double {
    fun linearize(channel: Float): Double {
        val value = channel.toDouble()
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linearize(color.red) + 0.7152 * linearize(color.green) + 0.0722 * linearize(color.blue)
}
