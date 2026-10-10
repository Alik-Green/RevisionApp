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
    fun retiredInkPaperPreferenceFallsBackToPlayfulAndSoundEffectsDefaultToOn() {
        assertEquals(ThemeStyle.PLAYFUL, ThemeStyle.fromStored("INK_PAPER"))
        assertEquals(ThemeStyle.PLAYFUL, ThemeStyle.fromStored(null))
        assertTrue(SettingsUi.initial().soundEffectsEnabled)
    }

    @Test
    fun darkThemeUsesTheRequestedAccentAndSurfaceRoles() {
        assertEquals(Color(0xFF58CC02), DarkAccentPalette.FeatherGreen)
        assertEquals(Color(0xFF89E219), DarkAccentPalette.MaskGreen)
        assertEquals(Color(0xFF1CB0F6), DarkAccentPalette.MacawBlue)
        assertEquals(Color(0xFF2B70C9), DarkAccentPalette.HumpbackBlue)
        assertEquals(Color(0xFFFF4B4B), DarkAccentPalette.CardinalRed)
        assertEquals(Color(0xFFFFC800), DarkAccentPalette.BeeYellow)
        assertEquals(Color(0xFFFF9600), DarkAccentPalette.FoxOrange)
        assertEquals(Color(0xFFCE82FF), DarkAccentPalette.BeetlePurple)
        assertEquals(DarkAccentPalette.FeatherGreen, PlayfulDark.primary)
        assertEquals(DarkAccentPalette.MacawBlue, PlayfulDark.secondary)
        assertEquals(DarkAccentPalette.HumpbackBlue, PlayfulDark.tertiary)
        assertEquals(DarkAccentPalette.CardinalRed, PlayfulDark.error)
        assertEquals(Color(0xFF131F24), PlayfulDark.background)
        assertEquals(Color(0xFF17272E), PlayfulDark.surfaceContainerLow)
        assertEquals(Color(0xFF202F36), PlayfulDark.surface)
        assertEquals(Color(0xFF263941), PlayfulDark.outlineVariant)
        assertEquals(Color(0xFF37464F), PlayfulDark.outline)
        assertEquals(Color(0xFFF1F7FB), PlayfulDark.onSurface)
    }

    @Test
    fun lightPaletteKeepsItsExistingVioletAndWarmBackground() {
        assertEquals(Color(0xFF4B35B5), PlayfulLight.primary)
        assertEquals(Color(0xFFFFF7EE), PlayfulLight.background)
        assertEquals(Color(0xFFA50F55), PlayfulLight.secondary)
    }

    @Test
    fun thePlayfulPaletteKeepsTextAndControlsHighContrastInLightAndDarkModes() {
        listOf(PlayfulLight, PlayfulDark).forEach { scheme ->
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
            // Outlines are intentionally lower-emphasis decorative borders, not text or icons.
        }
    }

    @Test
    fun correctAnswerFeedbackIsGreenAndReadableInBothAppearances() {
        listOf(PlayfulExtendedLight, PlayfulExtendedDark).forEach { colors ->
            assertTrue(colors.correct.green > colors.correct.red, "correct-answer accent should be green")
            assertAa(colors.onCorrect, colors.correct, "correct-answer badge text")
            assertAa(colors.correct, colors.correctContainer, "correct-answer accent")
            assertAa(colors.onCorrectContainer, colors.correctContainer, "correct-answer panel text")
        }
    }

    @Test
    fun darkPartialAndIncorrectFeedbackKeepDistinctReadableRoles() {
        assertEquals(DarkAccentPalette.BeeYellow, PlayfulExtendedDark.partial)
        assertEquals(DarkAccentPalette.CardinalRed, PlayfulExtendedDark.incorrect)
        assertAa(PlayfulExtendedDark.onPartial, PlayfulExtendedDark.partial, "partial-answer text")
        assertAa(PlayfulExtendedDark.onPartialContainer, PlayfulExtendedDark.partialContainer, "partial-answer panel text")
        assertAa(PlayfulExtendedDark.onIncorrect, PlayfulExtendedDark.incorrect, "incorrect-answer text")
        assertAa(PlayfulExtendedDark.onIncorrectContainer, PlayfulExtendedDark.incorrectContainer, "incorrect-answer panel text")
    }

    @Test
    fun rewardsStreaksAndPremiumAccentsKeepTheirOwnReadableRoles() {
        assertEquals(DarkAccentPalette.BeeYellow, PlayfulExtendedDark.reward)
        assertEquals(DarkAccentPalette.FoxOrange, PlayfulExtendedDark.streak)
        assertEquals(DarkAccentPalette.BeetlePurple, PlayfulExtendedDark.premium)
        assertAa(PlayfulExtendedDark.onReward, PlayfulExtendedDark.reward, "reward text")
        assertAa(PlayfulExtendedDark.onRewardContainer, PlayfulExtendedDark.rewardContainer, "reward panel text")
        assertAa(PlayfulExtendedDark.onStreak, PlayfulExtendedDark.streak, "streak text")
        assertAa(PlayfulExtendedDark.onStreakContainer, PlayfulExtendedDark.streakContainer, "streak panel text")
        assertAa(PlayfulExtendedDark.onPremium, PlayfulExtendedDark.premium, "premium text")
        assertAa(PlayfulExtendedDark.onPremiumContainer, PlayfulExtendedDark.premiumContainer, "premium panel text")
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
