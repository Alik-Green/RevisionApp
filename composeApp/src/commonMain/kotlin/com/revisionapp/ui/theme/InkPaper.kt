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

/** A five-colour dark-mode accent set: leaf, aqua, sky, amber, and coral. */
object DarkAccentPalette {
    val Leaf = Color(0xFFC4F27A)
    val Aqua = Color(0xFF5AD9C4)
    val Sky = Color(0xFF85B7FF)
    val Amber = Color(0xFFF5C75E)
    val Coral = Color(0xFFFF917C)
}

/** Deep, neutral-green surfaces keep the five bright accents intentional and readable. */
val PlayfulDark: ColorScheme = darkColorScheme(
    primary = DarkAccentPalette.Leaf,
    onPrimary = Color(0xFF20300D),
    primaryContainer = Color(0xFF2A3B1B),
    onPrimaryContainer = Color(0xFFE5F6C9),
    inversePrimary = Color(0xFF536E2A),
    secondary = DarkAccentPalette.Aqua,
    onSecondary = Color(0xFF07352D),
    secondaryContainer = Color(0xFF173A33),
    onSecondaryContainer = Color(0xFFC2F5E9),
    tertiary = DarkAccentPalette.Sky,
    onTertiary = Color(0xFF0E2B4C),
    tertiaryContainer = Color(0xFF213753),
    onTertiaryContainer = Color(0xFFD4E7FF),
    background = Color(0xFF0A0D0B),
    onBackground = Color(0xFFF1F6F2),
    surface = Color(0xFF111613),
    onSurface = Color(0xFFF1F6F2),
    surfaceVariant = Color(0xFF242C27),
    onSurfaceVariant = Color(0xFFB8C5BC),
    surfaceContainerLowest = Color(0xFF080A08),
    surfaceContainerLow = Color(0xFF141A16),
    surfaceContainer = Color(0xFF1A221D),
    surfaceContainerHigh = Color(0xFF222B25),
    surfaceContainerHighest = Color(0xFF2B352E),
    surfaceDim = Color(0xFF0A0D0B),
    surfaceBright = Color(0xFF303B34),
    inverseSurface = Color(0xFFE6EEE7),
    inverseOnSurface = Color(0xFF1D241F),
    error = DarkAccentPalette.Coral,
    onError = Color(0xFF48150D),
    errorContainer = Color(0xFF4A2922),
    onErrorContainer = Color(0xFFFFDAD1),
    outline = Color(0xFF87968A),
    outlineVariant = Color(0xFF5B6A60),
    scrim = Color(0xFF000000),
)

/**
 * Semantic colours Material 3 has no slot for: the three verdicts and their
 * containers. Kept out of [ColorScheme] because stuffing them into tertiary and
 * secondary would make "due soon" and "partly right" the same colour.
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
)

val PlayfulExtendedDark = ExtendedColors(
    correct = DarkAccentPalette.Leaf,
    onCorrect = Color(0xFF20300D),
    correctContainer = Color(0xFF2A3B1B),
    onCorrectContainer = Color(0xFFE5F6C9),
    partial = DarkAccentPalette.Amber,
    onPartial = Color(0xFF30250A),
    partialContainer = Color(0xFF3C3217),
    onPartialContainer = Color(0xFFFFF0C1),
    incorrect = DarkAccentPalette.Coral,
    onIncorrect = Color(0xFF48150D),
    incorrectContainer = Color(0xFF4A2922),
    onIncorrectContainer = Color(0xFFFFDAD1),
)

val LocalExtendedColors = staticCompositionLocalOf { PlayfulExtendedLight }

private val RevisionAppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** Keep each existing light-mode radius; unify larger dark-mode cards and controls. */
@Composable
@ReadOnlyComposable
fun appCornerShape(lightRadius: Dp): Shape {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val radius = when {
        !isDark -> lightRadius
        lightRadius > 16.dp -> 16.dp
        lightRadius > 12.dp -> 12.dp
        lightRadius > 6.dp && lightRadius < 10.dp -> 10.dp
        else -> lightRadius
    }
    return RoundedCornerShape(radius)
}

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
    val friendlyTypography = remember(friendlyFontFamily) { typographyWithFontFamily(friendlyFontFamily) }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = friendlyTypography,
            shapes = if (dark) RevisionAppShapes else Shapes(),
            content = content,
        )
    }
}

private fun typographyWithFontFamily(fontFamily: FontFamily): Typography {
    val defaults = Typography()
    return Typography(
        displayLarge = defaults.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = defaults.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = defaults.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = defaults.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = defaults.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = defaults.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = defaults.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = defaults.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = defaults.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = defaults.labelSmall.copy(fontFamily = fontFamily),
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
        "physics" to DarkAccentPalette.Sky,
        "maths" to DarkAccentPalette.Leaf,
        "math" to DarkAccentPalette.Leaf,
        "mathematics" to DarkAccentPalette.Leaf,
        "further maths" to DarkAccentPalette.Leaf,
        "computer science" to DarkAccentPalette.Aqua,
        "tmua" to DarkAccentPalette.Coral,
    )
    private val DarkPalette: List<Color> = listOf(
        DarkAccentPalette.Sky,
        DarkAccentPalette.Leaf,
        DarkAccentPalette.Aqua,
        DarkAccentPalette.Amber,
        DarkAccentPalette.Coral,
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
