package com.revisionapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.Placeholder
import androidx.compose.foundation.text.PlaceholderVerticalAlign
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.appendInlineContent
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.revisionapp.math.MathNode
import com.revisionapp.math.MathPlainText
import com.revisionapp.math.MathUnicode
import com.revisionapp.math.ScriptStyle
import com.revisionapp.ui.render.RichTextRenderer
import com.revisionapp.ui.render.UnicodeRichTextRenderer

/**
 * The renderer screens read maths through. Provided once in `App`, overridable so
 * the Math Gallery can show two renderers side by side.
 */
val LocalRichTextRenderer = staticCompositionLocalOf<RichTextRenderer> { UnicodeRichTextRenderer() }

@Composable
fun ProvideRichTextRenderer(renderer: RichTextRenderer, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRichTextRenderer provides renderer, content = content)
}

/** Scripts that have no Unicode equivalent are drawn smaller and shifted. */
private const val SCRIPT_SCALE = 0.7f

/** A stacked fraction is set slightly smaller than the surrounding text. */
private const val FRACTION_SCALE = 0.8f

private const val FRACTION_BAR_DP = 1f

/**
 * The main maths renderer: real superscripts, subscripts, symbols and stacked
 * fractions, laid out as text so it inherits the current colour, type scale and
 * theme and wraps like the prose around it.
 *
 * Scripts become Unicode characters when every character in them has one (`x²`,
 * `log₃`, `xₙ₊₁`) and smaller baseline-shifted text otherwise, so nothing can turn
 * into tofu. Fractions are measured with [TextMeasurer] and placed as inline
 * content at exactly the size they need; a fraction nested inside a fraction
 * falls back to the readable `(a)/(b)` form rather than guessing a size.
 *
 * If building the layout throws for any reason the plain-text fallback is drawn
 * instead. A card must never fail to render because of its maths.
 */
@Composable
fun MathText(
    source: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val renderer = LocalRichTextRenderer.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val layout = remember(source, renderer, style, measurer) {
        runCatching { buildMathLayout(renderer, measurer, density, source, style) }.getOrNull()
    }

    if (layout == null) {
        Text(
            text = renderer.render(source),
            modifier = modifier,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
        )
        return
    }
    Text(
        text = layout.text,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = layout.inlineContent,
    )
}

private class MathLayout(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>,
)

private fun buildMathLayout(
    renderer: RichTextRenderer,
    measurer: TextMeasurer,
    density: Density,
    source: String,
    style: TextStyle,
): MathLayout {
    val builder = AnnotatedString.Builder()
    val inline = LinkedHashMap<String, InlineTextContent>()
    val writer = MathWriter(builder, inline, measurer, density, style)
    writer.writeAll(renderer.parse(source))
    return MathLayout(builder.toAnnotatedString(), inline)
}

/** Appends parsed maths to an [AnnotatedString], collecting inline content. */
private class MathWriter(
    private val builder: AnnotatedString.Builder,
    private val inline: MutableMap<String, InlineTextContent>,
    private val measurer: TextMeasurer,
    private val density: Density,
    private val style: TextStyle,
) {

    fun writeAll(nodes: List<MathNode>) {
        for (node in nodes) write(node)
    }

    private fun write(node: MathNode) {
        when (node) {
            is MathNode.Run -> builder.append(node.text)

            // An unsupported command prints its name. Never a backslash.
            is MathNode.Unknown -> builder.append(node.name)

            is MathNode.Group -> writeAll(node.children)

            is MathNode.Script -> {
                writeAll(node.base)
                val sup = node.sup
                if (sup != null) writeScript(sup, BaselineShift.Superscript)
                val sub = node.sub
                if (sub != null) writeScript(sub, BaselineShift.Subscript)
            }

            is MathNode.Fraction -> writeFraction(node)

            is MathNode.Radical -> {
                builder.append(MathPlainText.radicalSymbol(node.index?.let { plain(it) }))
                val body = plain(node.body)
                if (body.length > 1) builder.append("(").append(body).append(")") else builder.append(body)
            }
        }
    }

    private fun writeScript(script: List<MathNode>, shift: BaselineShift) {
        val body = plain(script)
        val mapped = if (shift == BaselineShift.Superscript) {
            MathUnicode.superscript(body)
        } else {
            MathUnicode.subscript(body)
        }
        if (mapped != null) {
            builder.append(mapped)
            return
        }
        val scriptStyle = SpanStyle(fontSize = style.fontSize * SCRIPT_SCALE, baselineShift = shift)
        builder.withStyle(scriptStyle) { append(body) }
    }

    private fun writeFraction(node: MathNode.Fraction) {
        val numerator = plain(node.numerator)
        val denominator = plain(node.denominator)
        if (needsLayout(node.numerator) || needsLayout(node.denominator)) {
            builder.append("(").append(numerator).append(")/(").append(denominator).append(")")
            return
        }

        val fractionStyle = style.copy(fontSize = style.fontSize * FRACTION_SCALE)
        val measuredNumerator = measurer.measure(numerator, fractionStyle)
        val measuredDenominator = measurer.measure(denominator, fractionStyle)
        val barHeight = with(density) { FRACTION_BAR_DP.dp.toPx() }
        val width = maxOf(measuredNumerator.size.width, measuredDenominator.size.width)
        val height = measuredNumerator.size.height + measuredDenominator.size.height + barHeight * 3

        val id = "fraction-" + inline.size
        inline[id] = InlineTextContent(
            placeholder = Placeholder(
                width = with(density) { width.toDp() },
                height = with(density) { height.toDp() },
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(numerator, style = fractionStyle, maxLines = 1)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(FRACTION_BAR_DP.dp)
                        .background(LocalContentColor.current),
                )
                Text(denominator, style = fractionStyle, maxLines = 1)
            }
        }
        // The alternate text is what assistive services and text extraction see.
        builder.appendInlineContent(id, "($numerator)/($denominator)")
    }

    /** True when a subtree contains something that needs a box of its own. */
    private fun needsLayout(nodes: List<MathNode>): Boolean = nodes.any { node ->
        when (node) {
            is MathNode.Fraction, is MathNode.Radical -> true
            is MathNode.Group -> needsLayout(node.children)
            is MathNode.Script -> needsLayout(node.base) ||
                (node.sup?.let { needsLayout(it) } ?: false) ||
                (node.sub?.let { needsLayout(it) } ?: false)

            is MathNode.Run, is MathNode.Unknown -> false
        }
    }

    private fun plain(nodes: List<MathNode>): String = MathPlainText.render(nodes, ScriptStyle.UNICODE)
}
