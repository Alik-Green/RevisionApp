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
import androidx.compose.foundation.shape.RoundedCornerShape
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            source,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
 * Scripts, fractions, roots, every symbol class, nesting, bare maths written
 * without delimiters, and one unsupported command to show graceful degradation.
 */
private val GALLERY: List<String> = listOf(
    // Scripts, braced and bare.
    "$x^2$",
    "$x^{2}$",
    "$x^{n+1}$",
    "$x^{-1}$",
    "$10^{-19}$",
    "$m/s^2$",
    "$3.2 m s^-1$",
    "$x^{abc}$",
    "$\\log_3 x$",
    "$x_{n+1}$",
    "$a_i$",
    "$H_2O$",
    // Fractions.
    "$\\frac{a}{b}$",
    "$\\frac{x^{2}}{2}$",
    "$\\frac{n(n + 1)}{2}$",
    "$\\frac{1}{a}\\arctan\\frac{x}{a}$",
    "$\\frac{e^2 + 1}{4}$",
    // Roots, including one with a fraction inside.
    "$\\sqrt{2}$",
    "$\\sqrt[3]{x}$",
    "$\\sqrt{\\frac{1}{2}}$",
    "$T = 2\\pi\\sqrt{\\frac{l}{g}}$",
    // Arrows: the reported bug was \Rightarrow showing as the word "Rightarrow".
    "$P \\Rightarrow Q$",
    "$\\neg Q \\Rightarrow \\neg P$",
    "$A \\Leftrightarrow B$",
    "$x \\rightarrow y$",
    "$a \\mapsto f(a)$",
    // Relations and operators.
    "$a \\leq b$",
    "$x \\neq 0$",
    "$a \\approx b$",
    "$3 \\times 10^{8}$",
    "$a \\cdot b \\pm c$",
    "$x \\in S$",
    // Greek and big operators.
    "$\\alpha + \\beta + \\gamma$",
    "$\\theta, \\lambda, \\omega, \\Delta, \\Omega$",
    "$\\sum_{r=1}^{n} r$",
    "$\\int \\frac{1}{x^2 + a^2}\\,dx$",
    "$\\forall x, \\exists y$",
    // Real card text, bare and delimited.
    "$v^2 = u^2 + 2as$",
    "$\\cosh^2 x - \\sinh^2 x = 1$",
    "$e^{i\\pi} + 1 = 0$",
    "9.81 m/s^2",
    "O(n^2)",
    "The area is $\\frac{1}{2}\\int r^2 d\\theta$ in polar coordinates.",
    // Degradation: an unsupported command shows its name, never a backslash.
    "$\\frobnicate{x}$",
)
