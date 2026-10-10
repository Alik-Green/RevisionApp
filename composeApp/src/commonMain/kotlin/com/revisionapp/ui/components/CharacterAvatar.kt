package com.revisionapp.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.progression.AvatarPartCatalog
import com.revisionapp.domain.progression.CharacterAppearance

/** A simple vector-drawn, fully personalizable learner avatar. */
@Composable
fun CharacterAvatar(
    appearance: CharacterAppearance,
    modifier: Modifier = Modifier,
    size: Dp = 92.dp,
    animated: Boolean = true,
    celebratory: Boolean = false,
) {
    val bob = avatarBob(celebratory, animated)
    val celebrationScale = animateFloatAsState(
        targetValue = if (celebratory) 1.035f else 1f,
        animationSpec = tween(260),
        label = "avatar-celebration",
    ).value
    val skin = avatarColor(appearance.skinToneId, 0xFFD59D78)
    val hair = avatarColor(appearance.hairColorId, 0xFF3A241C)
    val eyes = avatarColor(appearance.eyeColorId, 0xFF593622)
    val shirt = MaterialTheme.colorScheme.primary
    val onShirt = MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = modifier
            .size(size)
            .offset(y = bob.dp)
            .graphicsLayer(scaleX = celebrationScale, scaleY = celebrationScale)
            .semantics { contentDescription = avatarDescription(appearance) },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Canvas(Modifier.fillMaxSize().padding(size * 0.04f)) {
                drawAvatar(
                    appearance = appearance,
                    skin = skin,
                    hair = hair,
                    eyes = eyes,
                    shirt = shirt,
                    onShirt = onShirt,
                )
            }
        }
    }
}

@Composable
private fun avatarBob(celebratory: Boolean, animated: Boolean): Float {
    if (!animated) return 0f
    val transition = rememberInfiniteTransition(label = "avatar-bob")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = if (celebratory) -3f else -1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (celebratory) 650 else 1450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "avatar-float",
    ).value
}

private fun avatarColor(id: String, fallback: Long): Color =
    Color(AvatarPartCatalog.find(id)?.colorArgb ?: fallback)

private fun avatarDescription(appearance: CharacterAppearance): String {
    val skin = AvatarPartCatalog.find(appearance.skinToneId)?.name ?: "custom"
    val hair = AvatarPartCatalog.find(appearance.hairStyleId)?.name ?: "custom"
    return "Character preview: $skin skin tone, ${appearance.hairColorId.removePrefix("hair-")} hair, $hair style"
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAvatar(
    appearance: CharacterAppearance,
    skin: Color,
    hair: Color,
    eyes: Color,
    shirt: Color,
    onShirt: Color,
) {
    val w = size.width
    val h = size.height
    val faceLeft = w * 0.205f
    val faceTop = h * 0.19f
    val faceWidth = w * 0.59f
    val faceHeight = h * 0.59f
    val faceRight = faceLeft + faceWidth
    val centerX = w * 0.5f
    val hairWidth = faceWidth * (0.84f + appearance.hairSize.coerceIn(0f, 1f) * 0.32f)
    val hairLeft = centerX - hairWidth / 2f
    val hairRight = centerX + hairWidth / 2f
    val hairTop = faceTop + (appearance.hairHeight.coerceIn(0f, 1f) - 0.5f) * h * 0.12f

    // Neck and shoulders give the portrait a friendly, person-shaped silhouette.
    drawRoundRect(
        color = shirt,
        topLeft = androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.71f),
        size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.34f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.23f),
    )
    drawRoundRect(
        color = onShirt.copy(alpha = 0.72f),
        topLeft = androidx.compose.ui.geometry.Offset(w * 0.39f, h * 0.76f),
        size = androidx.compose.ui.geometry.Size(w * 0.22f, h * 0.12f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
    )
    drawRoundRect(
        color = skin,
        topLeft = androidx.compose.ui.geometry.Offset(w * 0.415f, h * 0.60f),
        size = androidx.compose.ui.geometry.Size(w * 0.17f, h * 0.21f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.055f),
    )

    // Longer styles are drawn behind the face; the face masks their inner edge.
    when (appearance.hairStyleId) {
        "hair-long", "hair-locs" -> {
            drawRoundRect(hair, androidx.compose.ui.geometry.Offset(faceLeft - w * 0.045f, faceTop + h * 0.13f), androidx.compose.ui.geometry.Size(w * 0.16f, h * 0.51f), androidx.compose.ui.geometry.CornerRadius(w * 0.08f))
            drawRoundRect(hair, androidx.compose.ui.geometry.Offset(faceRight - w * 0.11f, faceTop + h * 0.13f), androidx.compose.ui.geometry.Size(w * 0.16f, h * 0.51f), androidx.compose.ui.geometry.CornerRadius(w * 0.08f))
        }
        "hair-bob" -> {
            drawRoundRect(hair, androidx.compose.ui.geometry.Offset(faceLeft - w * 0.025f, faceTop + h * 0.16f), androidx.compose.ui.geometry.Size(w * 0.12f, h * 0.39f), androidx.compose.ui.geometry.CornerRadius(w * 0.06f))
            drawRoundRect(hair, androidx.compose.ui.geometry.Offset(faceRight - w * 0.095f, faceTop + h * 0.16f), androidx.compose.ui.geometry.Size(w * 0.12f, h * 0.39f), androidx.compose.ui.geometry.CornerRadius(w * 0.06f))
        }
        "hair-ponytail" -> {
            drawCircle(hair, radius = w * 0.12f, center = androidx.compose.ui.geometry.Offset(faceRight - w * 0.015f, faceTop + h * 0.12f))
            drawRoundRect(hair, androidx.compose.ui.geometry.Offset(faceRight - w * 0.04f, faceTop + h * 0.16f), androidx.compose.ui.geometry.Size(w * 0.12f, h * 0.38f), androidx.compose.ui.geometry.CornerRadius(w * 0.06f))
        }
        else -> Unit
    }

    // Ears and face.
    drawOval(skin.copy(alpha = 0.97f), androidx.compose.ui.geometry.Offset(faceLeft - w * 0.055f, faceTop + faceHeight * 0.42f), androidx.compose.ui.geometry.Size(w * 0.12f, h * 0.14f))
    drawOval(skin.copy(alpha = 0.97f), androidx.compose.ui.geometry.Offset(faceRight - w * 0.065f, faceTop + faceHeight * 0.42f), androidx.compose.ui.geometry.Size(w * 0.12f, h * 0.14f))
    drawOval(skin, androidx.compose.ui.geometry.Offset(faceLeft, faceTop), androidx.compose.ui.geometry.Size(faceWidth, faceHeight))

    // Hairline silhouette: each unlocked hairstyle has its own outline.
    val volume = 0.13f + appearance.hairVolume.coerceIn(0f, 1f) * 0.10f
    val cap = Path().apply {
        when (appearance.hairStyleId) {
            "hair-spiky" -> {
                moveTo(hairLeft, hairTop + faceHeight * 0.24f)
                lineTo(hairLeft + hairWidth * 0.05f, hairTop - h * volume * 0.1f)
                lineTo(hairLeft + hairWidth * 0.22f, hairTop + faceHeight * 0.12f)
                lineTo(hairLeft + hairWidth * 0.39f, hairTop - h * volume)
                lineTo(hairLeft + hairWidth * 0.56f, hairTop + faceHeight * 0.11f)
                lineTo(hairLeft + hairWidth * 0.76f, hairTop - h * volume * 0.75f)
                lineTo(hairRight, hairTop + faceHeight * 0.24f)
                lineTo(hairRight - hairWidth * 0.04f, hairTop + faceHeight * 0.31f)
                lineTo(hairLeft + hairWidth * 0.04f, hairTop + faceHeight * 0.31f)
                close()
            }
            "hair-fringe" -> {
                moveTo(hairLeft, hairTop + faceHeight * 0.27f)
                cubicTo(hairLeft - w * 0.015f, hairTop + h * 0.02f, centerX - hairWidth * 0.33f, hairTop - h * volume, centerX, hairTop - h * volume)
                cubicTo(centerX + hairWidth * 0.37f, hairTop - h * volume, hairRight + w * 0.02f, hairTop + h * 0.02f, hairRight, hairTop + faceHeight * 0.27f)
                cubicTo(centerX + hairWidth * 0.26f, hairTop + faceHeight * 0.16f, centerX - hairWidth * 0.05f, hairTop + faceHeight * 0.49f, hairLeft + hairWidth * 0.08f, hairTop + faceHeight * 0.35f)
                close()
            }
            else -> {
                moveTo(hairLeft, hairTop + faceHeight * 0.27f)
                cubicTo(hairLeft - w * 0.015f, hairTop + h * 0.02f, centerX - hairWidth * 0.33f, hairTop - h * volume, centerX, hairTop - h * volume)
                cubicTo(centerX + hairWidth * 0.37f, hairTop - h * volume, hairRight + w * 0.02f, hairTop + h * 0.02f, hairRight, hairTop + faceHeight * 0.27f)
                lineTo(hairRight - hairWidth * 0.05f, hairTop + faceHeight * 0.19f)
                cubicTo(centerX + hairWidth * 0.15f, hairTop + faceHeight * 0.26f, centerX - hairWidth * 0.20f, hairTop + faceHeight * 0.18f, hairLeft + hairWidth * 0.05f, hairTop + faceHeight * 0.27f)
                close()
            }
        }
    }
    drawPath(cap, hair)

    when (appearance.hairStyleId) {
        "hair-curly" -> {
            val curlRadius = w * 0.052f
            for (index in 0..5) {
                val x = hairLeft + hairWidth * (0.12f + index * 0.15f)
                val y = hairTop - h * volume * 0.52f + if (index % 2 == 0) 0f else h * 0.025f
                drawCircle(hair, curlRadius, androidx.compose.ui.geometry.Offset(x, y))
            }
        }
        "hair-bun" -> drawCircle(hair, w * 0.105f, androidx.compose.ui.geometry.Offset(centerX, hairTop - h * volume * 0.48f))
        "hair-locs" -> {
            for (index in 0..4) {
                val startX = hairLeft + hairWidth * (0.12f + index * 0.18f)
                val lock = Path().apply {
                    moveTo(startX, hairTop + faceHeight * 0.06f)
                    cubicTo(startX - w * 0.02f, hairTop + faceHeight * 0.18f, startX + w * 0.02f, hairTop + faceHeight * 0.31f, startX, hairTop + faceHeight * 0.44f)
                }
                drawPath(lock, hair.copy(alpha = 0.68f), style = Stroke(width = w * 0.018f))
            }
        }
        "hair-waves", "hair-long", "hair-bob" -> {
            for (index in 0..2) {
                val startX = hairLeft + hairWidth * (0.19f + index * 0.25f)
                val wave = Path().apply {
                    moveTo(startX, hairTop + faceHeight * 0.07f)
                    cubicTo(startX - w * 0.025f, hairTop + faceHeight * 0.13f, startX + w * 0.025f, hairTop + faceHeight * 0.17f, startX, hairTop + faceHeight * 0.22f)
                }
                drawPath(wave, hair.copy(alpha = 0.42f), style = Stroke(width = w * 0.012f))
            }
        }
        else -> Unit
    }

    val eyeScale = 0.68f + appearance.eyeSize.coerceIn(0f, 1f) * 0.66f
    val eyeOffset = faceWidth * (0.15f + appearance.eyeSpacing.coerceIn(0f, 1f) * 0.12f)
    val eyeY = faceTop + faceHeight * (0.39f + (appearance.eyeHeight.coerceIn(0f, 1f) - 0.5f) * 0.22f)
    val eyeWidth = faceWidth * 0.16f * eyeScale
    val eyeHeight = faceHeight * 0.095f * eyeScale
    val pupilRadius = eyeHeight * 0.34f
    val pupilColor = Color(0xFF201A20)
    val eyePoints = listOf(centerX - eyeOffset, centerX + eyeOffset)

    for ((index, eyeX) in eyePoints.withIndex()) {
        val wink = appearance.eyeStyleId == "eyes-wink" && index == 0
        if (wink) {
            val winkPath = Path().apply {
                moveTo(eyeX - eyeWidth * 0.55f, eyeY)
                cubicTo(eyeX - eyeWidth * 0.15f, eyeY + eyeHeight * 0.8f, eyeX + eyeWidth * 0.15f, eyeY + eyeHeight * 0.8f, eyeX + eyeWidth * 0.55f, eyeY)
            }
            drawPath(winkPath, hair, style = Stroke(width = w * 0.018f))
            continue
        }
        if (appearance.eyeStyleId == "eyes-almond" || appearance.eyeStyleId == "eyes-cat") {
            val lift = if (appearance.eyeStyleId == "eyes-cat") eyeHeight * 0.28f else 0f
            val leftY = eyeY - if (index == 0) lift else 0f
            val rightY = eyeY - if (index == 1) lift else 0f
            val almond = Path().apply {
                moveTo(eyeX - eyeWidth / 2f, leftY)
                cubicTo(
                    eyeX - eyeWidth * 0.18f,
                    eyeY - eyeHeight * 0.72f,
                    eyeX + eyeWidth * 0.18f,
                    eyeY - eyeHeight * 0.72f,
                    eyeX + eyeWidth / 2f,
                    rightY,
                )
                cubicTo(
                    eyeX + eyeWidth * 0.18f,
                    eyeY + eyeHeight * 0.72f,
                    eyeX - eyeWidth * 0.18f,
                    eyeY + eyeHeight * 0.72f,
                    eyeX - eyeWidth / 2f,
                    leftY,
                )
                close()
            }
            drawPath(almond, Color(0xFFFFFCFA))
        } else {
            drawOval(
                Color(0xFFFFFCFA),
                androidx.compose.ui.geometry.Offset(eyeX - eyeWidth / 2f, eyeY - eyeHeight / 2f),
                androidx.compose.ui.geometry.Size(eyeWidth, eyeHeight),
            )
        }
        drawCircle(eyes, radius = pupilRadius, center = androidx.compose.ui.geometry.Offset(eyeX, eyeY + eyeHeight * 0.02f))
        drawCircle(pupilColor, radius = pupilRadius * 0.48f, center = androidx.compose.ui.geometry.Offset(eyeX, eyeY + eyeHeight * 0.03f))
        drawCircle(Color.White, radius = pupilRadius * 0.22f, center = androidx.compose.ui.geometry.Offset(eyeX - pupilRadius * 0.23f, eyeY - pupilRadius * 0.25f))
        if (appearance.eyeStyleId == "eyes-sleepy") {
            drawLine(hair, androidx.compose.ui.geometry.Offset(eyeX - eyeWidth * 0.55f, eyeY - eyeHeight * 0.06f), androidx.compose.ui.geometry.Offset(eyeX + eyeWidth * 0.55f, eyeY - eyeHeight * 0.06f), w * 0.018f)
        }
        if (appearance.eyeStyleId == "eyes-sparkle") {
            drawCircle(Color.White, radius = pupilRadius * 0.18f, center = androidx.compose.ui.geometry.Offset(eyeX + pupilRadius * 0.55f, eyeY + pupilRadius * 0.45f))
        }
        drawLine(hair, androidx.compose.ui.geometry.Offset(eyeX - eyeWidth * 0.43f, eyeY - eyeHeight * 0.9f), androidx.compose.ui.geometry.Offset(eyeX + eyeWidth * 0.35f, eyeY - eyeHeight * 0.92f), w * 0.012f)
    }

    // Nose position and scale are independent of the chosen nose silhouette.
    val noseScale = 0.68f + appearance.noseSize.coerceIn(0f, 1f) * 0.65f
    val noseY = faceTop + faceHeight * (0.60f + (appearance.noseHeight.coerceIn(0f, 1f) - 0.5f) * 0.20f)
    val noseColor = skin.copy(red = (skin.red * 0.79f).coerceIn(0f, 1f), green = (skin.green * 0.72f).coerceIn(0f, 1f), blue = (skin.blue * 0.70f).coerceIn(0f, 1f))
    when (appearance.noseStyleId) {
        "nose-soft" -> {
            val nosePath = Path().apply {
                moveTo(centerX, noseY - h * 0.025f * noseScale)
                cubicTo(centerX - w * 0.015f, noseY + h * 0.025f * noseScale, centerX + w * 0.018f, noseY + h * 0.04f * noseScale, centerX + w * 0.038f, noseY + h * 0.022f * noseScale)
            }
            drawPath(nosePath, noseColor, style = Stroke(width = w * 0.014f))
        }
        "nose-upturned" -> {
            val upturned = Path().apply {
                moveTo(centerX - w * 0.04f * noseScale, noseY + h * 0.025f * noseScale)
                cubicTo(
                    centerX - w * 0.015f * noseScale,
                    noseY - h * 0.005f * noseScale,
                    centerX + w * 0.015f * noseScale,
                    noseY - h * 0.005f * noseScale,
                    centerX + w * 0.04f * noseScale,
                    noseY + h * 0.025f * noseScale,
                )
            }
            drawPath(upturned, noseColor, style = Stroke(width = w * 0.014f))
        }
        "nose-bridge" -> {
            drawLine(noseColor, androidx.compose.ui.geometry.Offset(centerX, noseY - h * 0.04f * noseScale), androidx.compose.ui.geometry.Offset(centerX, noseY + h * 0.02f * noseScale), w * 0.015f)
            drawOval(noseColor, androidx.compose.ui.geometry.Offset(centerX - w * 0.025f * noseScale, noseY + h * 0.01f * noseScale), androidx.compose.ui.geometry.Size(w * 0.05f * noseScale, h * 0.026f * noseScale))
        }
        else -> {
            drawCircle(noseColor, radius = w * 0.019f * noseScale, center = androidx.compose.ui.geometry.Offset(centerX, noseY))
            if (appearance.noseStyleId == "nose-freckles") {
                drawCircle(noseColor.copy(alpha = 0.8f), w * 0.012f, androidx.compose.ui.geometry.Offset(centerX - w * 0.045f, noseY + h * 0.025f))
                drawCircle(noseColor.copy(alpha = 0.8f), w * 0.012f, androidx.compose.ui.geometry.Offset(centerX + w * 0.045f, noseY + h * 0.025f))
            }
        }
    }

    val mouthY = faceTop + faceHeight * 0.76f
    val smile = Path().apply {
        moveTo(centerX - w * 0.055f, mouthY)
        cubicTo(centerX - w * 0.022f, mouthY + h * 0.04f, centerX + w * 0.022f, mouthY + h * 0.04f, centerX + w * 0.055f, mouthY)
    }
    drawPath(smile, Color(0xFF8E4F4D), style = Stroke(width = w * 0.016f))
}
