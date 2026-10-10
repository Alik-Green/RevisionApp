package com.revisionapp.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.revisionapp.ui.AppState
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.MathText
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.render.RichTextRenderer
import com.revisionapp.ui.theme.appCornerShape
import com.revisionapp.ui.theme.appInset

/**
 * A debug screen, reached from Settings > Developer, that shows representative
 * expressions through both renderers side by side so they can be eyeballed.
 *
 * It exists because there is no way to see this app during development: no
 * display in the sandbox it is written in. If a symbol is missing, a script is
 * misplaced or a fraction is mis-sized, this is where it shows up first.
 */
@Composable
fun MathGalleryScreen(state: AppState) {
    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = "Math gallery",
            subtitle = GALLERY.size.toString() + " expressions, both renderers",
            onBack = { state.back() },
        )
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = appInset(16.dp), vertical = appInset(8.dp)),
            verticalArrangement = Arrangement.spacedBy(appInset(8.dp)),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(appInset(8.dp))) {
                    SectionLabel("Typeset (main renderer)", Modifier.weight(1f))
                    SectionLabel("Plain text (fallback)", Modifier.weight(1f))
                }
                HorizontalDivider()
            }
            items(GALLERY) { source ->
                GalleryRow(source, state.renderer)
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun GalleryRow(source: String, renderer: RichTextRenderer) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, appCornerShape(10.dp))
            .padding(appInset(10.dp)),
        verticalArrangement = Arrangement.spacedBy(appInset(6.dp)),
    ) {
        Text(
            source,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(appInset(8.dp))) {
            Column(Modifier.weight(1f)) {
                MathText(source, style = MaterialTheme.typography.bodyLarge)
            }
            Column(Modifier.weight(1f)) {
                Text(renderer.render(source), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * Wraps [body] in the `$...$` delimiters the content packs use. Written with
 * `\u0024` because a literal `$` in a Kotlin string starts a template.
 */
private fun maths(body: String): String = "\u0024" + body + "\u0024"

/**
 * Scripts, fractions, roots, every symbol class, nesting, bare maths written
 * without delimiters, and one unsupported command to show graceful degradation.
 */
private val GALLERY: List<String> = listOf(
    // Scripts, braced and bare.
    maths("x^2"),
    maths("x^{2}"),
    maths("x^{n+1}"),
    maths("x^{-1}"),
    maths("10^{-19}"),
    maths("m/s^2"),
    maths("3.2 m s^-1"),
    maths("x^{abc}"),
    maths("\\log_3 x"),
    maths("x_{n+1}"),
    maths("a_i"),
    maths("H_2O"),
    // Fractions, including one whose numerator carries a script.
    maths("\\frac{a}{b}"),
    maths("\\frac{x^{2}}{2}"),
    maths("\\frac{n(n + 1)}{2}"),
    maths("\\frac{1}{a}\\arctan\\frac{x}{a}"),
    maths("\\frac{e^2 + 1}{4}"),
    // Roots, including a fraction inside one.
    maths("\\sqrt{2}"),
    maths("\\sqrt[3]{x}"),
    maths("\\sqrt{\\frac{1}{2}}"),
    maths("T = 2\\pi\\sqrt{\\frac{l}{g}}"),
    // Arrows. The reported bug was \Rightarrow showing as the word "Rightarrow".
    maths("P \\Rightarrow Q"),
    maths("\\neg Q \\Rightarrow \\neg P"),
    maths("A \\Leftrightarrow B"),
    maths("x \\rightarrow y"),
    maths("a \\mapsto f(a)"),
    maths("P \\implies Q"),
    // Relations and operators.
    maths("a \\leq b"),
    maths("x \\neq 0"),
    maths("a \\approx b"),
    maths("3 \\times 10^{8}"),
    maths("a \\cdot b \\pm c"),
    maths("x \\in S"),
    // Greek, big operators and quantifiers.
    maths("\\alpha + \\beta + \\gamma"),
    maths("\\theta, \\lambda, \\omega, \\Delta, \\Omega"),
    maths("\\sum_{r=1}^{n} r"),
    maths("\\int \\frac{1}{x^2 + a^2}\\,dx"),
    maths("\\forall x, \\exists y"),
    // Real card text.
    maths("v^2 = u^2 + 2as"),
    maths("\\cosh^2 x - \\sinh^2 x = 1"),
    maths("e^{i\\pi} + 1 = 0"),
    // Bare maths with no delimiters at all, as the physics packs write it.
    "9.81 m/s^2",
    "O(n^2)",
    "The area is " + maths("\\frac{1}{2}\\int r^2 d\\theta") + " in polar coordinates.",
    // Degradation: an unsupported command shows its name, never a backslash.
    maths("\\frobnicate{x}"),
)
