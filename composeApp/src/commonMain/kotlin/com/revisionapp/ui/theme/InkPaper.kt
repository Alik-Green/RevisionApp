package com.revisionapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revisionapp.domain.check.VerdictKind
import com.revisionapp.domain.srs.Rating
import com.revisionapp.generated.resources.Res
import com.revisionapp.generated.resources.nunito_variable
import org.jetbrains.compose.resources.Font

/** The warm-violet light palette and the role-based evergreen dark palette. */
val PlayfulLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF4B35B5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1D9FF),
    onPrimaryContainer = Color(0xFF201157),
    inversePrimary = Color(0xFFC9BCFF),
    secondary = Color(0xFFA50F55),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E8),
    onSecondaryContainer = Color(0xFF3C0020),
    tertiary = Color(0xFF00685E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC4F2E9),
    onTertiaryContainer = Color(0xFF002F2B),
    background = Color(0xFFFFF7EE),
    onBackground = Color(0xFF251D27),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF251D27),
    surfaceVariant = Color(0xFFF1E9F3),
    onSurfaceVariant = Color(0xFF49414D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0F7),
    surfaceContainer = Color(0xFFF7E8F2),
    surfaceContainerHigh = Color(0xFFEFDBE9),
    surfaceContainerHighest = Color(0xFFE6D0E0),
    surfaceDim = Color(0xFFE7D9E4),
    surfaceBright = Color(0xFFFFFBFD),
    inverseSurface = Color(0xFF342D37),
    inverseOnSurface = Color(0xFFF7EEF5),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410001),
    outline = Color(0xFF69596A),
    outlineVariant = Color(0xFF9B879A),
    scrim = Color(0xFF000000),
)

/** The user's colour roles, kept together so every dark screen uses the same tokens. */
object DarkAccentPalette {
    val FeatherGreen = Color(0xFF58CC02)
    val MaskGreen = Color(0xFF89E219)
    val MacawBlue = Color(0xFF1CB0F6)
    val HumpbackBlue = Color(0xFF2B70C9)
    val CardinalRed = Color(0xFFFF4B4B)
    val BeeYellow = Color(0xFFFFC800)
    val FoxOrange = Color(0xFFFF9600)
    val BeetlePurple = Color(0xFFCE82FF)

    val Background = Color(0xFF131F24)
    val SecondaryBackground = Color(0xFF17272E)
    val CardSurface = Color(0xFF202F36)
    val SubtleBorder = Color(0xFF263941)
    val StandardBorder = Color(0xFF37464F)
    val PrimaryText = Color(0xFFF1F7FB)
    val MutedText = Color(0xFFB7C7CF)
}

/** Dark mode follows the supplied evergreen/slate neutrals and Duolingo-inspired roles. */
val PlayfulDark: ColorScheme = darkColorScheme(
    primary = DarkAccentPalette.FeatherGreen,
    onPrimary = DarkAccentPalette.Background,
    primaryContainer = Color(0xFF263D20),
    onPrimaryContainer = Color(0xFFDDF7D2),
    inversePrimary = DarkAccentPalette.MaskGreen,
    secondary = DarkAccentPalette.MacawBlue,
    onSecondary = DarkAccentPalette.Background,
    secondaryContainer = Color(0xFF173747),
    onSecondaryContainer = Color(0xFFD4F2FF),
    tertiary = DarkAccentPalette.HumpbackBlue,
    onTertiary = DarkAccentPalette.PrimaryText,
    tertiaryContainer = Color(0xFF1D3550),
    onTertiaryContainer = Color(0xFFD9E9FF),
    background = DarkAccentPalette.Background,
    onBackground = DarkAccentPalette.PrimaryText,
    surface = DarkAccentPalette.CardSurface,
    onSurface = DarkAccentPalette.PrimaryText,
    surfaceVariant = DarkAccentPalette.SubtleBorder,
    onSurfaceVariant = DarkAccentPalette.MutedText,
    surfaceContainerLowest = Color(0xFF101B20),
    surfaceContainerLow = DarkAccentPalette.SecondaryBackground,
    surfaceContainer = Color(0xFF1B2B32),
    surfaceContainerHigh = DarkAccentPalette.CardSurface,
    surfaceContainerHighest = DarkAccentPalette.SubtleBorder,
    surfaceDim = DarkAccentPalette.Background,
    surfaceBright = DarkAccentPalette.StandardBorder,
    inverseSurface = DarkAccentPalette.PrimaryText,
    inverseOnSurface = DarkAccentPalette.Background,
    error = DarkAccentPalette.CardinalRed,
    onError = DarkAccentPalette.Background,
    errorContainer = Color(0xFF4A2730),
    onErrorContainer = Color(0xFFFFE0E3),
    outline = DarkAccentPalette.StandardBorder,
    outlineVariant = DarkAccentPalette.SubtleBorder,
    scrim = Color(0xFF000000),
)

/**
 * Semantic colours Material 3 has no dedicated slot for: answer verdicts,
 * rewards, streaks and premium/playful accents. Kept out of [ColorScheme] so
 * course information, correct answers and reward feedback remain distinct.
 */
data class ExtendedColors(
    val correct: Color,
    val onCorrect: Color,
    val correctContainer: Color,
    val onCorrectContainer: Color,
    val partial: Color,
    val onPartial: Color,
    val partialContainer: Color,
    val onPartialContainer: Color,
    val incorrect: Color,
    val onIncorrect: Color,
    val incorrectContainer: Color,
    val onIncorrectContainer: Color,
    val reward: Color,
    val onReward: Color,
    val rewardContainer: Color,
    val onRewardContainer: Color,
    val streak: Color,
    val onStreak: Color,
    val streakContainer: Color,
    val onStreakContainer: Color,
    val premium: Color,
    val onPremium: Color,
    val premiumContainer: Color,
    val onPremiumContainer: Color,
)

val PlayfulExtendedLight = ExtendedColors(
    correct = Color(0xFF1D7445),
    onCorrect = Color(0xFFFFFFFF),
    correctContainer = Color(0xFFD6F0E0),
    onCorrectContainer = Color(0xFF0B3D22),
    partial = Color(0xFFD9A100),
    onPartial = Color(0xFF3A2A00),
    partialContainer = Color(0xFFFBEDC6),
    onPartialContainer = Color(0xFF4A3400),
    incorrect = Color(0xFFB3263E),
    onIncorrect = Color(0xFFFFFFFF),
    incorrectContainer = Color(0xFFFFDAD9),
    onIncorrectContainer = Color(0xFF410006),
    reward = Color(0xFF00685E),
    onReward = Color(0xFFFFFFFF),
    rewardContainer = Color(0xFFFFD9E8),
    onRewardContainer = Color(0xFF3C0020),
    streak = Color(0xFF4B35B5),
    onStreak = Color(0xFFFFFFFF),
    streakContainer = Color(0xFFE1D9FF),
    onStreakContainer = Color(0xFF201157),
    premium = Color(0xFF8E5BD9),
    onPremium = Color(0xFFFFFFFF),
    premiumContainer = Color(0xFFF1E9F3),
    onPremiumContainer = Color(0xFF251D27),
)

val PlayfulExtendedDark = ExtendedColors(
    correct = DarkAccentPalette.FeatherGreen,
    onCorrect = DarkAccentPalette.Background,
    correctContainer = Color(0xFF263D20),
    onCorrectContainer = Color(0xFFDDF7D2),
    partial = DarkAccentPalette.BeeYellow,
    onPartial = DarkAccentPalette.Background,
    partialContainer = Color(0xFF3F3419),
    onPartialContainer = Color(0xFFFFEEB5),
    incorrect = DarkAccentPalette.CardinalRed,
    onIncorrect = DarkAccentPalette.Background,
    incorrectContainer = Color(0xFF4A2730),
    onIncorrectContainer = Color(0xFFFFE0E3),
    reward = DarkAccentPalette.BeeYellow,
    onReward = DarkAccentPalette.Background,
    rewardContainer = Color(0xFF3F3419),
    onRewardContainer = Color(0xFFFFEEB5),
    streak = DarkAccentPalette.FoxOrange,
    onStreak = DarkAccentPalette.Background,
    streakContainer = Color(0xFF40291B),
    onStreakContainer = Color(0xFFFFDFC5),
    premium = DarkAccentPalette.BeetlePurple,
    onPremium = DarkAccentPalette.Background,
    premiumContainer = Color(0xFF34263F),
    onPremiumContainer = Color(0xFFF0D9FF),
)

val LocalExtendedColors = staticCompositionLocalOf { PlayfulExtendedLight }

private val RevisionAppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp),
)

/** Keep each existing light-mode radius; unify larger dark-mode cards and controls. */
@Composable
@ReadOnlyComposable
fun appCornerShape(lightRadius: Dp): Shape {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val radius = when {
        !isDark -> lightRadius
        lightRadius > 14.dp -> 14.dp
        lightRadius > 12.dp -> 12.dp
        lightRadius > 6.dp && lightRadius < 10.dp -> 10.dp
        else -> lightRadius
    }
    return RoundedCornerShape(radius)
}

/** Slightly tighter dark-mode insets; all existing light-mode spacing is preserved. */
@Composable
@ReadOnlyComposable
fun appInset(lightInset: Dp): Dp {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark && lightInset >= 10.dp) (lightInset - 2.dp).coerceAtLeast(6.dp) else lightInset
}

/** Keep existing light weights, while making dark-mode headings more decisive. */
@Composable
@ReadOnlyComposable
fun appHeadingWeight(lightWeight: FontWeight = FontWeight.SemiBold): FontWeight =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) FontWeight.Bold else lightWeight

/** Applies the app colour palette and light/dark appearance across the whole app. */
@Composable
fun RevisionAppTheme(
    themeStyle: ThemeStyle = ThemeStyle.PLAYFUL,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = when (themeStyle) {
        ThemeStyle.PLAYFUL -> if (dark) PlayfulDark else PlayfulLight
    }
    val extendedColors = if (dark) PlayfulExtendedDark else PlayfulExtendedLight
    val friendlyFontFamily = FontFamily(
        Font(Res.font.nunito_variable, weight = FontWeight.Normal),
        Font(Res.font.nunito_variable, weight = FontWeight.Bold),
    )
    val friendlyTypography = remember(friendlyFontFamily, dark) { typographyWithFontFamily(friendlyFontFamily, dark) }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = friendlyTypography,
            shapes = if (dark) RevisionAppShapes else Shapes(),
            content = content,
        )
    }
}

private fun typographyWithFontFamily(fontFamily: FontFamily, darkMode: Boolean): Typography {
    val defaults = Typography()
    fun style(default: androidx.compose.ui.text.TextStyle, heavy: Boolean = false, small: Boolean = false) =
        default.copy(
            fontFamily = fontFamily,
            fontWeight = if (darkMode && heavy) FontWeight.Bold else default.fontWeight,
            fontSize = if (darkMode && small) (default.fontSize.value + 1f).sp else default.fontSize,
        )
    return Typography(
        displayLarge = style(defaults.displayLarge, heavy = true),
        displayMedium = style(defaults.displayMedium, heavy = true),
        displaySmall = style(defaults.displaySmall, heavy = true),
        headlineLarge = style(defaults.headlineLarge, heavy = true),
        headlineMedium = style(defaults.headlineMedium, heavy = true),
        headlineSmall = style(defaults.headlineSmall, heavy = true),
        titleLarge = style(defaults.titleLarge, heavy = true),
        titleMedium = style(defaults.titleMedium, heavy = true),
        titleSmall = style(defaults.titleSmall, heavy = true),
        bodyLarge = style(defaults.bodyLarge),
        bodyMedium = style(defaults.bodyMedium),
        bodySmall = style(defaults.bodySmall, small = true),
        labelLarge = style(defaults.labelLarge, heavy = true),
        labelMedium = style(defaults.labelMedium, small = true),
        labelSmall = style(defaults.labelSmall, small = true),
    )
}

/** Kept for stored-setting compatibility; the Playful palette is the only available style. */
enum class ThemeStyle {
    PLAYFUL,
    ;

    companion object {
        fun fromStored(value: String?): ThemeStyle =
            entries.firstOrNull { it.name == value } ?: PLAYFUL
    }
}

/** The appearance override the user picks in Settings; new installs follow the device. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromStored(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

object ExtendedTheme {
    val colors: ExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}

/**
 * Accent colours for top-level topic folders.
 *
 * The four built-in subjects have fixed colours so Physics is always the same
 * blue on every device; anything else — a user's own top-level topic — gets a
 * stable colour from its id, so it never changes between launches.
 */
object SubjectAccents {
    val Physics = Color(0xFF3D7BEA)
    val Maths = Color(0xFF8E5BD9)
    val ComputerScience = Color(0xFF14A38B)
    val Tmua = Color(0xFFE0662E)

    private val BuiltInByName: Map<String, Color> = mapOf(
        "physics" to Physics,
        "maths" to Maths,
        "math" to Maths,
        "mathematics" to Maths,
        "further maths" to Maths,
        "computer science" to ComputerScience,
        "tmua" to Tmua,
    )

    private val Palette: List<Color> = listOf(
        Physics, Maths, ComputerScience, Tmua,
        Color(0xFFC2436B), Color(0xFF2F7D4F), Color(0xFFB06A12), Color(0xFF4A6FA5),
    )
    private val DarkBuiltInByName: Map<String, Color> = mapOf(
        "physics" to DarkAccentPalette.HumpbackBlue,
        "maths" to DarkAccentPalette.FeatherGreen,
        "math" to DarkAccentPalette.FeatherGreen,
        "mathematics" to DarkAccentPalette.FeatherGreen,
        "further maths" to DarkAccentPalette.FeatherGreen,
        "computer science" to DarkAccentPalette.MacawBlue,
        "tmua" to DarkAccentPalette.BeetlePurple,
    )
    private val DarkPalette: List<Color> = listOf(
        DarkAccentPalette.MaskGreen,
        DarkAccentPalette.MacawBlue,
        DarkAccentPalette.HumpbackBlue,
        DarkAccentPalette.BeeYellow,
        DarkAccentPalette.FoxOrange,
        DarkAccentPalette.BeetlePurple,
    )

    fun forTopic(name: String, id: String, dark: Boolean = false): Color {
        val normalizedName = name.trim().lowercase()
        val fixed = if (dark) DarkBuiltInByName[normalizedName] else BuiltInByName[normalizedName]
        if (fixed != null) return fixed
        val palette = if (dark) DarkPalette else Palette
        val index = (id.hashCode() and Int.MAX_VALUE) % palette.size
        return palette[index]
    }
}

/** Again, Hard, Good and Easy in the semantic colours the brief specifies. */
@Composable
@ReadOnlyComposable
fun ratingColour(rating: Rating): Color = when (rating) {
    Rating.AGAIN -> ExtendedTheme.colors.incorrect
    Rating.HARD -> ExtendedTheme.colors.partial
    Rating.GOOD -> MaterialTheme.colorScheme.primary
    Rating.EASY -> ExtendedTheme.colors.correct
}

@Composable
@ReadOnlyComposable
fun ratingContainerColour(rating: Rating): Color = when (rating) {
    Rating.AGAIN -> ExtendedTheme.colors.incorrectContainer
    Rating.HARD -> ExtendedTheme.colors.partialContainer
    Rating.GOOD -> MaterialTheme.colorScheme.primaryContainer
    Rating.EASY -> ExtendedTheme.colors.correctContainer
}

@Composable
@ReadOnlyComposable
fun ratingOnContainerColour(rating: Rating): Color = when (rating) {
    Rating.AGAIN -> ExtendedTheme.colors.onIncorrectContainer
    Rating.HARD -> ExtendedTheme.colors.onPartialContainer
    Rating.GOOD -> MaterialTheme.colorScheme.onPrimaryContainer
    Rating.EASY -> ExtendedTheme.colors.onCorrectContainer
}

@Composable
@ReadOnlyComposable
fun verdictColour(kind: VerdictKind): Color = when (kind) {
    VerdictKind.CORRECT -> ExtendedTheme.colors.correct
    VerdictKind.PARTIAL -> ExtendedTheme.colors.partial
    VerdictKind.INCORRECT -> ExtendedTheme.colors.incorrect
}

@Composable
@ReadOnlyComposable
fun verdictContainerColour(kind: VerdictKind): Color = when (kind) {
    VerdictKind.CORRECT -> ExtendedTheme.colors.correctContainer
    VerdictKind.PARTIAL -> ExtendedTheme.colors.partialContainer
    VerdictKind.INCORRECT -> ExtendedTheme.colors.incorrectContainer
}

@Composable
@ReadOnlyComposable
fun verdictOnContainerColour(kind: VerdictKind): Color = when (kind) {
    VerdictKind.CORRECT -> ExtendedTheme.colors.onCorrectContainer
    VerdictKind.PARTIAL -> ExtendedTheme.colors.onPartialContainer
    VerdictKind.INCORRECT -> ExtendedTheme.colors.onIncorrectContainer
}
