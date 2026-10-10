package com.revisionapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.progression.AppearanceCatalog
import com.revisionapp.domain.progression.CharacterAppearance

/** A small, vector-drawn person whose face responds to the Profile editor controls. */
@androidx.compose.runtime.Composable
fun CharacterPortrait(
    appearance: CharacterAppearance,
    modifier: Modifier = Modifier,
    width: Dp = 148.dp,
    height: Dp = 180.dp,
) {
    val skinName = AppearanceCatalog.find(appearance.skinToneId)?.name ?: "custom"
    val description = "Character portrait, $skinName skin, ${appearance.hairStyleId}, ${appearance.eyeStyleId} eyes"
    Canvas(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFEDE8FF))
            .semantics { contentDescription = description },
    ) {
        val w = size.width
        val h = size.height
        val skin = AppearanceCatalog.find(appearance.skinToneId)?.swatchArgb?.let { Color(it) } ?: Color(0xFFC9916D)
        val hair = AppearanceCatalog.find(appearance.hairColorId)?.swatchArgb?.let { Color(it) } ?: Color(0xFF59382F)
        val eyes = AppearanceCatalog.find(appearance.eyeColorId)?.swatchArgb?.let { Color(it) } ?: Color(0xFF68442F)
        val shirt = Color(0xFF6652C9)
        val faceLeft = w * 0.22f
        val faceTop = h * 0.13f
        val faceWidth = w * 0.56f
        val faceHeight = h * 0.48f
        val faceRight = faceLeft + faceWidth
        val hairScale = 0.76f + appearance.hairSize.coerceIn(0, 100) / 100f * 0.5f
        val hairline = 0.45f - appearance.hairHeight.coerceIn(0, 100) / 100f * 0.25f
        val longHair = appearance.hairStyleId in setOf("hair-long", "hair-wavy", "hair-curly", "hair-locs")
        val bobHair = appearance.hairStyleId == "hair-bob"

        // Neck and shoulders sit behind the head; the rounded shoulders make the portrait read as a person.
        drawRoundRect(
            color = shirt,
            topLeft = Offset(w * 0.15f, h * 0.70f),
            size = Size(w * 0.70f, h * 0.42f),
            cornerRadius = CornerRadius(w * 0.18f),
        )
        drawRoundRect(
            color = skin,
            topLeft = Offset(w * 0.42f, h * 0.55f),
            size = Size(w * 0.16f, h * 0.23f),
            cornerRadius = CornerRadius(w * 0.05f),
        )

        if (longHair || bobHair) {
            val sideLength = if (longHair) h * 0.54f else h * 0.30f
            drawRoundRect(
                color = hair,
                topLeft = Offset(faceLeft - faceWidth * 0.09f, faceTop + faceHeight * 0.18f),
                size = Size(faceWidth * 0.22f, sideLength),
                cornerRadius = CornerRadius(faceWidth * 0.12f),
            )
            drawRoundRect(
                color = hair,
                topLeft = Offset(faceRight - faceWidth * 0.13f, faceTop + faceHeight * 0.18f),
                size = Size(faceWidth * 0.22f, sideLength),
                cornerRadius = CornerRadius(faceWidth * 0.12f),
            )
        }
        if (appearance.hairStyleId == "hair-bun") {
            drawCircle(hair, radius = faceWidth * 0.17f * hairScale, center = Offset(w * 0.5f, faceTop - h * 0.01f))
        }

        drawOval(
            color = skin,
            topLeft = Offset(faceLeft, faceTop),
            size = Size(faceWidth, faceHeight),
        )
        drawOval(
            color = Color(0x1F39261E),
            topLeft = Offset(faceLeft, faceTop),
            size = Size(faceWidth, faceHeight),
            style = Stroke(width = w * 0.008f),
        )

        val cap = Path().apply {
            moveTo(faceLeft - faceWidth * 0.015f, faceTop + faceHeight * 0.44f)
            cubicTo(
                faceLeft - faceWidth * 0.07f,
                faceTop + faceHeight * 0.04f,
                w * 0.34f,
                faceTop - faceHeight * 0.13f * hairScale,
                w * 0.50f,
                faceTop - faceHeight * 0.09f * hairScale,
            )
            cubicTo(
                w * 0.68f,
                faceTop - faceHeight * 0.15f * hairScale,
                faceRight + faceWidth * 0.08f,
                faceTop + faceHeight * 0.04f,
                faceRight + faceWidth * 0.015f,
                faceTop + faceHeight * 0.42f,
            )
            lineTo(faceRight - faceWidth * 0.06f, faceTop + faceHeight * hairline)
            cubicTo(w * 0.56f, faceTop + faceHeight * (hairline + 0.04f), w * 0.44f, faceTop + faceHeight * (hairline - 0.015f), faceLeft + faceWidth * 0.06f, faceTop + faceHeight * hairline)
            close()
        }
        drawPath(cap, hair)

        when (appearance.hairStyleId) {
            "hair-curly" -> {
                for (index in 0..6) {
                    val x = faceLeft + faceWidth * (0.06f + index * 0.145f)
                    val y = faceTop + faceHeight * (0.05f + if (index % 2 == 0) 0f else -0.04f)
                    drawCircle(hair, radius = faceWidth * 0.105f * hairScale, center = Offset(x, y))
                }
            }
            "hair-wavy" -> {
                for (index in 0..2) {
                    val x = faceLeft + faceWidth * (0.18f + index * 0.31f)
                    drawCircle(hair, radius = faceWidth * 0.09f * hairScale, center = Offset(x, faceTop + faceHeight * 0.03f))
                }
            }
            "hair-locs" -> {
                for (index in 0..4) {
                    val x = faceLeft + faceWidth * (0.08f + index * 0.21f)
                    drawLine(
                        color = hair.copy(alpha = 0.78f),
                        start = Offset(x, faceTop + faceHeight * 0.04f),
                        end = Offset(x + faceWidth * 0.015f, faceTop + faceHeight * 0.39f),
                        strokeWidth = faceWidth * 0.025f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            "hair-fringe", "hair-bob" -> {
                val fringe = Path().apply {
                    moveTo(faceLeft + faceWidth * 0.09f, faceTop + faceHeight * 0.10f)
                    cubicTo(faceLeft + faceWidth * 0.29f, faceTop + faceHeight * 0.12f, faceLeft + faceWidth * 0.54f, faceTop + faceHeight * 0.16f, faceRight - faceWidth * 0.06f, faceTop + faceHeight * 0.12f)
                    lineTo(faceRight - faceWidth * 0.26f, faceTop + faceHeight * 0.30f)
                    cubicTo(faceRight - faceWidth * 0.45f, faceTop + faceHeight * 0.24f, faceLeft + faceWidth * 0.31f, faceTop + faceHeight * 0.34f, faceLeft + faceWidth * 0.12f, faceTop + faceHeight * 0.28f)
                    close()
                }
                drawPath(fringe, hair)
            }
        }

        val eyeScale = 0.68f + appearance.eyeSize.coerceIn(0, 100) / 100f * 0.65f
        val eyeWidth = faceWidth * (if (appearance.eyeStyleId == "eyes-almond") 0.17f else 0.145f) * eyeScale
        val eyeHeight = faceHeight * (if (appearance.eyeStyleId == "eyes-round") 0.105f else 0.075f) * eyeScale
        val eyeY = faceTop + faceHeight * (0.57f - appearance.eyeHeight.coerceIn(0, 100) / 100f * 0.20f)
        val gap = faceWidth * (0.17f + appearance.eyeSpacing.coerceIn(0, 100) / 100f * 0.13f)
        for (eyeX in listOf(w * 0.5f - gap, w * 0.5f + gap)) {
            val shape = Size(eyeWidth, eyeHeight)
            drawOval(Color(0xFFFFFBF6), Offset(eyeX - eyeWidth / 2f, eyeY - eyeHeight / 2f), shape)
            drawOval(Color(0xFF3E2924), Offset(eyeX - eyeWidth / 2f, eyeY - eyeHeight / 2f), shape, style = Stroke(width = w * 0.006f))
            val irisSize = if (appearance.eyeStyleId == "eyes-sparkle") 0.59f else 0.49f
            drawCircle(eyes, radius = eyeHeight * irisSize, center = Offset(eyeX, eyeY))
            drawCircle(Color(0xFF211A1A), radius = eyeHeight * 0.30f, center = Offset(eyeX, eyeY))
            drawCircle(Color.White, radius = eyeHeight * 0.12f, center = Offset(eyeX - eyeHeight * 0.10f, eyeY - eyeHeight * 0.13f))
            drawLine(
                color = hair,
                start = Offset(eyeX - eyeWidth * 0.42f, eyeY - eyeHeight * 1.15f),
                end = Offset(eyeX + eyeWidth * 0.42f, eyeY - eyeHeight * 1.22f),
                strokeWidth = w * 0.012f,
                cap = StrokeCap.Round,
            )
            if (appearance.eyeStyleId == "eyes-sleepy") {
                drawLine(
                    color = skin.copy(alpha = 0.88f),
                    start = Offset(eyeX - eyeWidth * 0.54f, eyeY - eyeHeight * 0.18f),
                    end = Offset(eyeX + eyeWidth * 0.54f, eyeY - eyeHeight * 0.18f),
                    strokeWidth = eyeHeight * 0.25f,
                    cap = StrokeCap.Round,
                )
            }
        }

        val noseScale = 0.62f + appearance.noseSize.coerceIn(0, 100) / 100f * 0.72f
        val noseY = faceTop + faceHeight * (0.68f - appearance.noseHeight.coerceIn(0, 100) / 100f * 0.19f)
        val noseShade = skin.copy(alpha = 0.48f)
        val nosePath = Path().apply {
            moveTo(w * 0.5f, noseY - faceHeight * 0.075f * noseScale)
            cubicTo(w * 0.49f, noseY + faceHeight * 0.035f * noseScale, w * 0.46f, noseY + faceHeight * 0.075f * noseScale, w * 0.5f, noseY + faceHeight * 0.065f * noseScale)
        }
        when (appearance.noseStyleId) {
            "nose-button" -> drawCircle(noseShade, radius = faceWidth * 0.028f * noseScale, center = Offset(w * 0.5f, noseY + faceHeight * 0.025f))
            "nose-wide" -> {
                drawLine(
                    color = noseShade,
                    start = Offset(w * 0.5f, noseY - faceHeight * 0.06f),
                    end = Offset(w * 0.5f, noseY + faceHeight * 0.07f),
                    strokeWidth = w * 0.014f * noseScale,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = noseShade,
                    start = Offset(w * 0.5f - faceWidth * 0.055f * noseScale, noseY + faceHeight * 0.065f),
                    end = Offset(w * 0.5f + faceWidth * 0.055f * noseScale, noseY + faceHeight * 0.065f),
                    strokeWidth = w * 0.014f * noseScale,
                    cap = StrokeCap.Round,
                )
            }
            "nose-upturned" -> drawLine(
                color = noseShade,
                start = Offset(w * 0.5f - faceWidth * 0.04f * noseScale, noseY + faceHeight * 0.04f),
                end = Offset(w * 0.5f + faceWidth * 0.04f * noseScale, noseY + faceHeight * 0.015f),
                strokeWidth = w * 0.014f * noseScale,
                cap = StrokeCap.Round,
            )
            "nose-straight" -> drawLine(
                color = noseShade,
                start = Offset(w * 0.5f, noseY - faceHeight * 0.09f * noseScale),
                end = Offset(w * 0.5f, noseY + faceHeight * 0.07f * noseScale),
                strokeWidth = w * 0.013f * noseScale,
                cap = StrokeCap.Round,
            )
            else -> drawPath(nosePath, noseShade, style = Stroke(width = w * 0.014f * noseScale, cap = StrokeCap.Round))
        }

        val mouthY = faceTop + faceHeight * 0.80f
        val mouth = Path().apply {
            moveTo(w * 0.5f - faceWidth * 0.08f, mouthY)
            cubicTo(w * 0.5f - faceWidth * 0.035f, mouthY + faceHeight * 0.045f, w * 0.5f + faceWidth * 0.035f, mouthY + faceHeight * 0.045f, w * 0.5f + faceWidth * 0.08f, mouthY)
        }
        drawPath(mouth, Color(0xFF8B4B4B), style = Stroke(width = w * 0.014f, cap = StrokeCap.Round))

        // Small collar accent adds a little polish while keeping the avatar neutral and reusable.
        drawPath(
            Path().apply {
                moveTo(w * 0.39f, h * 0.76f)
                lineTo(w * 0.50f, h * 0.83f)
                lineTo(w * 0.61f, h * 0.76f)
            },
            Color(0xFFF9E9FF),
            style = Stroke(width = w * 0.018f, cap = StrokeCap.Round),
        )
    }
}
