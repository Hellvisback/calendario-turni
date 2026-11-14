@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.turni.calendario.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.ui.theme.SemanticTone
import com.turni.calendario.ui.theme.currentTurniPalette
import com.turni.calendario.ui.theme.readableIndicator
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated overlay status popup that slides in from the bottom.
 * Does NOT push or shift existing layout — renders as an overlay on top.
 *
 * Includes:
 * - Entrance and exit spring animations
 * - Confetti celebration particle burst on SUCCESS
 * - Haptic shake animation on ERROR
 * - High-contrast Obsidian Liquid Glass design
 */
enum class PopupType {
    SUCCESS, ERROR, WARNING, INFO
}

private data class ConfettiParticle(
    val initialX: Float,
    val initialY: Float,
    val angleRad: Float,
    val speed: Float,
    val color: Color,
    val size: Float,
    val rotationSpeed: Float
)

@Composable
fun AnimatedStatusPopup(
    message: String?,
    type: PopupType = PopupType.SUCCESS,
    onDismiss: () -> Unit,
    autoDismissMs: Long = 4200L,
    modifier: Modifier = Modifier
) {
    val isVisible = message != null
    val palette = currentTurniPalette

    // Auto-dismiss timer
    LaunchedEffect(message) {
        if (message != null) {
            delay(autoDismissMs)
            onDismiss()
        }
    }

    // Confetti animation progress (0f to 1f) for SUCCESS
    val confettiProgress = remember { Animatable(0f) }
    LaunchedEffect(message, type) {
        if (message != null && type == PopupType.SUCCESS) {
            confettiProgress.snapTo(0f)
            confettiProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing)
            )
        }
    }

    // Shake animation for ERROR
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(message, type) {
        if (message != null && type == PopupType.ERROR) {
            shakeOffset.snapTo(0f)
            repeat(3) {
                shakeOffset.animateTo(12f, tween(40))
                shakeOffset.animateTo(-12f, tween(40))
            }
            shakeOffset.animateTo(0f, tween(40))
        }
    }

    // Pre-calculated delicate sparkle particles
    val particles = remember(message, type, palette) {
        if (message != null && type == PopupType.SUCCESS) {
            val colors = listOf(
                Color(palette.success.ink),
                Color(palette.success.edge),
                Color(palette.secondary),
                Color(palette.travel.edge),
                Color(palette.primary)
            )
            val rnd = Random(message.hashCode())
            List(18) {
                ConfettiParticle(
                    initialX = 0.2f + rnd.nextFloat() * 0.6f,
                    initialY = 0.88f + rnd.nextFloat() * 0.08f,
                    angleRad = (rnd.nextFloat() * Math.PI).toFloat() + Math.PI.toFloat(), // Upward burst
                    speed = 120f + rnd.nextFloat() * 180f,
                    color = colors[rnd.nextInt(colors.size)],
                    size = 2.5f + rnd.nextFloat() * 2.5f,
                    rotationSpeed = 0f
                )
            }
        } else emptyList()
    }

    val tone = when (type) {
        PopupType.SUCCESS -> palette.success
        PopupType.ERROR -> palette.danger
        PopupType.WARNING -> palette.warning
        PopupType.INFO -> SemanticTone(palette.primaryContainer, palette.onPrimaryContainer, palette.primary)
    }
    val icon = when (type) {
        PopupType.SUCCESS -> Icons.Default.CheckCircle
        PopupType.ERROR -> Icons.Default.Error
        PopupType.WARNING -> Icons.Default.Warning
        PopupType.INFO -> Icons.Default.Info
    }
    val bgColor = Color(tone.fill)
    val ink = Color(tone.ink)
    val borderColor = Color(tone.edge)
    val iconFill = lerp(bgColor, borderColor, .20f)
    val closeFill = lerp(bgColor, ink, .10f)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Confetti Canvas Layer (renders above background, below popup card)
        if (isVisible && type == PopupType.SUCCESS && confettiProgress.value < 1f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val p = confettiProgress.value
                val w = size.width
                val h = size.height

                particles.forEach { pt ->
                    val distance = pt.speed * p
                    val gravity = 120f * p * p
                    val currentX = (pt.initialX * w) + (cos(pt.angleRad) * distance * 0.5f)
                    val currentY = (pt.initialY * h) + (sin(pt.angleRad) * distance) + gravity

                    val alpha = ((1f - p) * (1f - p)).coerceIn(0f, 1f)
                    val r = pt.size * density

                    drawCircle(
                        color = pt.color.copy(alpha = alpha * 0.35f),
                        radius = r * 2.2f,
                        center = Offset(currentX, currentY)
                    )
                    drawCircle(
                        color = pt.color.copy(alpha = alpha),
                        radius = r,
                        center = Offset(currentX, currentY)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(220)),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut(animationSpec = tween(180))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(x = shakeOffset.value.toInt(), y = 0) }
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = bgColor, contentColor = ink),
                border = androidx.compose.foundation.BorderStroke(
                    1.2.dp,
                    Brush.horizontalGradient(
                        listOf(
                            borderColor.copy(alpha = 0.9f),
                            borderColor.copy(alpha = 0.4f),
                            borderColor.copy(alpha = 0.8f)
                        )
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(iconFill),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = readableIndicator(iconFill, ink),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = message ?: "",
                        color = ink,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(closeFill)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Chiudi",
                            tint = readableIndicator(closeFill, ink),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
