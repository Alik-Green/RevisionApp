package com.revisionapp.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revisionapp.domain.progression.CharacterCosmetic

/** A compact, gently animated companion that echoes the learner's equipped style. */
@Composable
fun LearningBuddy(
    cosmetic: CharacterCosmetic,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    celebratory: Boolean = false,
    animated: Boolean = true,
) {
    val (bob, sparkleAlpha) = buddyAnimationValues(celebratory, animated)
    val accent = Color(cosmetic.accentArgb)

    Box(
        modifier = modifier
            .size(size)
            .offset(y = bob.dp)
            .semantics { contentDescription = "${cosmetic.name}, your learning companion" },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(size),
            shape = CircleShape,
            color = accent.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.32f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    cosmetic.emoji,
                    fontSize = (size.value * 0.52f).sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "✦",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 5.dp, end = 6.dp)
                        .alpha(sparkleAlpha),
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = (size.value * 0.16f).sp,
                )
            }
        }
    }
}

@Composable
private fun buddyAnimationValues(celebratory: Boolean, animated: Boolean): Pair<Float, Float> {
    if (!animated) return 0f to 0.65f

    val transition = rememberInfiniteTransition(label = "learning-buddy")
    val bob = transition.animateFloat(
        initialValue = 0f,
        targetValue = if (celebratory) -5f else -3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (celebratory) 800 else 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "buddy-bob",
    ).value
    val sparkleAlpha = transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "buddy-sparkle",
    ).value
    return bob to sparkleAlpha
}
