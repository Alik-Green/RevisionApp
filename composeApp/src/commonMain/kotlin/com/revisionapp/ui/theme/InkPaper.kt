package com.revisionapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.revisionapp.domain.check.VerdictKind
import com.revisionapp.domain.srs.Rating
import com.revisionapp.generated.resources.Res
import com.revisionapp.generated.resources.nunito_variable
import org.jetbrains.compose.resources.Font

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
    primary = Color(0xFF2F40C7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE2FF),
    onPrimaryContainer = Color(0xFF131D67),
    inversePrimary = Color(0xFFBFC8FF),
    secondary = Color(0xFF006C70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBDECEE),
    onSecondaryContainer = Color(0xFF00393C),
    tertiary = Color(0xFFAD4E00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE7CC),
    onTertiaryContainer = Color(0xFF492000),
    background = Color(0xFFFAF8F2),
    onBackground = Color(0xFF16151A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16151A),
    surfaceVariant = Color(0xFFE5E2D9),
    onSurfaceVariant = Color(0xFF45434D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFAF7),
    surfaceContainer = Color(0xFFF2EFE8),
    surfaceContainerHigh = Color(0xFFE9E5DC),
    surfaceContainerHighest = Color(0xFFE1DDD4),
    surfaceDim = Color(0xFFDEDAD0),
    surfaceBright = Color(0xFFFCFAF6),
    inverseSurface = Color(0xFF2F3039),
    inverseOnSurface = Color(0xFFF3F0F0),
    error = Color(0xFFB3263E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD9),
    onErrorContainer = Color(0xFF410006),
    outline = Color(0xFF706B60),
    outlineVariant = Color(0xFF969080),
    scrim = Color(0xFF000000),
)

val InkPaperDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFC1C9FF),
    onPrimary = Color(0xFF101653),
    primaryContainer = Color(0xFF283285),
    onPrimaryContainer = Color(0xFFDDE2FF),
    inversePrimary = Color(0xFF2F40C7),
    secondary = Color(0xFF5CD1D3),
    onSecondary = Color(0xFF00393C),
    secondaryContainer = Color(0xFF075255),
    onSecondaryContainer = Color(0xFFBDECEE),
    tertiary = Color(0xFFFFC16B),
    onTertiary = Color(0xFF422400),
    tertiaryContainer = Color(0xFF624000),
    onTertiaryContainer = Color(0xFFFFE0AF),
    background = Color(0xFF101117),
    onBackground = Color(0xFFF2F1F6),
    surface = Color(0xFF191A22),
    onSurface = Color(0xFFF2F1F6),
    surfaceVariant = Color(0xFF343540),
    onSurfaceVariant = Color(0xFFDDDCE7),
    surfaceContainerLowest = Color(0xFF101117),
    surfaceContainerLow = Color(0xFF15161C),
    surfaceContainer = Color(0xFF191A22),
    surfaceContainerHigh = Color(0xFF25262F),
    surfaceContainerHighest = Color(0xFF30313B),
    surfaceDim = Color(0xFF101117),
    surfaceBright = Color(0xFF383943),
    inverseSurface = Color(0xFFF2F1F6),
    inverseOnSurface = Color(0xFF2F3039),
    error = Color(0xFFFF8A93),
    onError = Color(0xFF69000A),
    errorContainer = Color(0xFF4A1518),
    onErrorContainer = Color(0xFFFFDAD9),
    outline = Color(0xFF9B9DAA),
    outlineVariant = Color(0xFF686A76),
    scrim = Color(0xFF000000),
)

/** Default, brighter palette: orchid, berry and mint on warm, paper-like surfaces. */
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

val PlayfulDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFD2C8FF),
    onPrimary = Color(0xFF291460),
    primaryContainer = Color(0xFF4733A0),
    onPrimaryContainer = Color(0xFFF2EDFF),
    inversePrimary = Color(0xFFB8A8FF),
    secondary = Color(0xFFFFB7D1),
    onSecondary = Color(0xFF54112D),
    secondaryContainer = Color(0xFF79224D),
    onSecondaryContainer = Color(0xFFFFE5EF),
    tertiary = Color(0xFF81E3D2),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF004B42),
    onTertiaryContainer = Color(0xFFBDF5E9),
    background = Color(0xFF171019),
    onBackground = Color(0xFFFFF7FC),
    surface = Color(0xFF201823),
    onSurface = Color(0xFFFFF7FC),
    surfaceVariant = Color(0xFF3D3342),
    onSurfaceVariant = Color(0xFFE4D8E6),
    surfaceContainerLowest = Color(0xFF120D15),
    surfaceContainerLow = Color(0xFF261D29),
    surfaceContainer = Color(0xFF2C2230),
    surfaceContainerHigh = Color(0xFF372A3B),
    surfaceContainerHighest = Color(0xFF433348),
    surfaceDim = Color(0xFF171019),
    surfaceBright = Color(0xFF4B3C50),
    inverseSurface = Color(0xFFFFF7FC),
    inverseOnSurface = Color(0xFF342D37),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD4),
    outline = Color(0xFFB09BB6),
    outlineVariant = Color(0xFF77647E),
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

val InkPaperExtendedDark = ExtendedColors(
    correct = Color(0xFF72E3A1),
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
    val friendlyFontFamily = FontFamily(
        Font(Res.font.nunito_variable, weight = FontWeight.Normal),
        Font(Res.font.nunito_variable, weight = FontWeight.Bold),
    )
    val friendlyTypography = remember(friendlyFontFamily) { typographyWithFontFamily(friendlyFontFamily) }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(colorScheme = colorScheme, typography = friendlyTypography, content = content)
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
