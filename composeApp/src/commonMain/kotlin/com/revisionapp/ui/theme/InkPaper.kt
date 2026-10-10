package com.revisionapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.revisionapp.domain.check.VerdictKind
import com.revisionapp.domain.srs.Rating

/**
 * "Ink & Paper": warm off-white paper, ink-dark text, an indigo primary, a teal
 * secondary and an amber tertiary reserved for *time pressure* — due counts and
 * streaks — so amber always means "this needs attention soon".
 *
 * Every colour pair below was chosen for WCAG AA text contrast: body text uses
 * onSurface on background (about 15:1 light, 13:1 dark), and coloured text always
 * uses an on*Container colour on its container rather than white on a mid-tone,
 * which is where amber and green usually fail.
 */
val InkPaperLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF3D4FD1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E3FF),
    onPrimaryContainer = Color(0xFF10196B),
    inversePrimary = Color(0xFFBFC2FF),
    secondary = Color(0xFF0F8B8D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDEFF0),
    onSecondaryContainer = Color(0xFF00373A),
    tertiary = Color(0xFFC77510),
    onTertiary = Color(0xFF2C1700),
    tertiaryContainer = Color(0xFFFFEFD8),
    onTertiaryContainer = Color(0xFF4A2800),
    background = Color(0xFFF8F7F4),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFECEAE4),
    onSurfaceVariant = Color(0xFF5C5A63),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFAF7),
    surfaceContainer = Color(0xFFF5F3EF),
    surfaceContainerHigh = Color(0xFFEFEDE8),
    surfaceContainerHighest = Color(0xFFEAE7E1),
    surfaceDim = Color(0xFFE4E1DB),
    surfaceBright = Color(0xFFFCFBF8),
    inverseSurface = Color(0xFF31303A),
    inverseOnSurface = Color(0xFFF3F0F0),
    error = Color(0xFFC62F3B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD9),
    onErrorContainer = Color(0xFF410006),
    outline = Color(0xFFC9C5BB),
    outlineVariant = Color(0xFFDAD6CC),
    scrim = Color(0xFF000000),
)

val InkPaperDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFAEB6FF),
    onPrimary = Color(0xFF1B2178),
    primaryContainer = Color(0xFF2B3396),
    onPrimaryContainer = Color(0xFFE0E3FF),
    inversePrimary = Color(0xFF3D4FD1),
    secondary = Color(0xFF5CD1D3),
    onSecondary = Color(0xFF00373A),
    secondaryContainer = Color(0xFF0B4F51),
    onSecondaryContainer = Color(0xFFCDEFF0),
    tertiary = Color(0xFFF5B65A),
    onTertiary = Color(0xFF462A00),
    tertiaryContainer = Color(0xFF6B4400),
    onTertiaryContainer = Color(0xFFFFDDB0),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE6E4EA),
    surface = Color(0xFF1A1B22),
    onSurface = Color(0xFFE6E4EA),
    surfaceVariant = Color(0xFF2A2B34),
    onSurfaceVariant = Color(0xFFB2B0BA),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF16171D),
    surfaceContainer = Color(0xFF1A1B22),
    surfaceContainerHigh = Color(0xFF24252D),
    surfaceContainerHighest = Color(0xFF2E2F38),
    surfaceDim = Color(0xFF121318),
    surfaceBright = Color(0xFF33343D),
    inverseSurface = Color(0xFFE6E4EA),
    inverseOnSurface = Color(0xFF31303A),
    error = Color(0xFFFF8A93),
    onError = Color(0xFF69000A),
    errorContainer = Color(0xFF4A1518),
    onErrorContainer = Color(0xFFFFDAD9),
    outline = Color(0xFF4A4B57),
    outlineVariant = Color(0xFF3A3B45),
    scrim = Color(0xFF000000),
)

/** Default, brighter palette: orchid, berry and mint on warm, paper-like surfaces. */
val PlayfulLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF5540C9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8E0FF),
    onPrimaryContainer = Color(0xFF21105E),
    inversePrimary = Color(0xFFC9BCFF),
    secondary = Color(0xFFAD155E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E8),
    onSecondaryContainer = Color(0xFF3C0020),
    tertiary = Color(0xFF00786A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC5F2E9),
    onTertiaryContainer = Color(0xFF002F2B),
    background = Color(0xFFFFF7EE),
    onBackground = Color(0xFF251D27),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF251D27),
    surfaceVariant = Color(0xFFF0E8F2),
    onSurfaceVariant = Color(0xFF514853),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0F7),
    surfaceContainer = Color(0xFFF8E8F2),
    surfaceContainerHigh = Color(0xFFF0DDEA),
    surfaceContainerHighest = Color(0xFFE8D3E3),
    surfaceDim = Color(0xFFE6D8E3),
    surfaceBright = Color(0xFFFFFAFC),
    inverseSurface = Color(0xFF342D37),
    inverseOnSurface = Color(0xFFF7EEF5),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410001),
    outline = Color(0xFF746573),
    outlineVariant = Color(0xFFC9B5C5),
    scrim = Color(0xFF000000),
)

val PlayfulDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFD0C4FF),
    onPrimary = Color(0xFF2B176D),
    primaryContainer = Color(0xFF4935A7),
    onPrimaryContainer = Color(0xFFEDE7FF),
    inversePrimary = Color(0xFFB8A8FF),
    secondary = Color(0xFFFFB1D0),
    onSecondary = Color(0xFF5B1132),
    secondaryContainer = Color(0xFF71234A),
    onSecondaryContainer = Color(0xFFFFD9E9),
    tertiary = Color(0xFF79DCCF),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF005047),
    onTertiaryContainer = Color(0xFFA3F4E6),
    background = Color(0xFF171019),
    onBackground = Color(0xFFF5EAF3),
    surface = Color(0xFF211821),
    onSurface = Color(0xFFF5EAF3),
    surfaceVariant = Color(0xFF39313C),
    onSurfaceVariant = Color(0xFFD0C3D4),
    surfaceContainerLowest = Color(0xFF120D15),
    surfaceContainerLow = Color(0xFF261D29),
    surfaceContainer = Color(0xFF2C2230),
    surfaceContainerHigh = Color(0xFF372A3B),
    surfaceContainerHighest = Color(0xFF433348),
    surfaceDim = Color(0xFF171019),
    surfaceBright = Color(0xFF4B3C50),
    inverseSurface = Color(0xFFF5EAF3),
    inverseOnSurface = Color(0xFF342D37),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD4),
    outline = Color(0xFFAB9AAE),
    outlineVariant = Color(0xFF66566B),
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

val InkPaperExtendedLight = ExtendedColors(
    correct = Color(0xFF2E8B57),
    onCorrect = Color(0xFFFFFFFF),
    correctContainer = Color(0xFFD6F0E0),
    onCorrectContainer = Color(0xFF0B3D22),
    partial = Color(0xFFD9A100),
    onPartial = Color(0xFF3A2A00),
    partialContainer = Color(0xFFFBEDC6),
    onPartialContainer = Color(0xFF4A3400),
    incorrect = Color(0xFFC62F3B),
    onIncorrect = Color(0xFFFFFFFF),
    incorrectContainer = Color(0xFFFFDAD9),
    onIncorrectContainer = Color(0xFF410006),
)

val InkPaperExtendedDark = ExtendedColors(
    correct = Color(0xFF6FD39A),
    onCorrect = Color(0xFF00381F),
    correctContainer = Color(0xFF14392A),
    onCorrectContainer = Color(0xFFC8F0DA),
    partial = Color(0xFFF2CB4A),
    onPartial = Color(0xFF3D2E00),
    partialContainer = Color(0xFF3D3000),
    onPartialContainer = Color(0xFFF7E3A0),
    incorrect = Color(0xFFFF8A93),
    onIncorrect = Color(0xFF5C0A12),
    incorrectContainer = Color(0xFF4A1518),
    onIncorrectContainer = Color(0xFFFFDAD9),
)

val LocalExtendedColors = staticCompositionLocalOf { InkPaperExtendedLight }

/** Applies the chosen colour style and light/dark appearance across the whole app. */
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
        ThemeStyle.INK_PAPER -> if (dark) InkPaperDark else InkPaperLight
    }
    val extendedColors = if (dark) InkPaperExtendedDark else InkPaperExtendedLight

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

/** The selectable colour styles; the playful palette is the default for new installs. */
enum class ThemeStyle(val title: String) {
    PLAYFUL("Playful"),
    INK_PAPER("Ink & Paper"),
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

    fun forTopic(name: String, id: String): Color {
        val fixed = BuiltInByName[name.trim().lowercase()]
        if (fixed != null) return fixed
        val index = (id.hashCode() and Int.MAX_VALUE) % Palette.size
        return Palette[index]
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
