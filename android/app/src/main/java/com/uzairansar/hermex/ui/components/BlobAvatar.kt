package com.uzairansar.hermex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Autogenerates a cute 2D cartoonish blob face for any AI agent / bot based on its name.
 */
@Composable
fun BlobAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val cleanName = name.lowercase().trim().ifBlank { "agent" }
    val isDefaultAgent = cleanName == "default" || cleanName == "default assistant" || cleanName.contains("default")
    val seed = remember(cleanName) { abs(cleanName.hashCode()) }

    // Palette variants
    val colorPalettes = remember {
        listOf(
            listOf(Color(0xFFFF6B8B), Color(0xFFFF8E53)), // Coral Sunset
            listOf(Color(0xFF4FACFE), Color(0xFF00F2FE)), // Electric Sky
            listOf(Color(0xFF43E97B), Color(0xFF38F9D7)), // Mint Green
            listOf(Color(0xFFFA709A), Color(0xFFFEE140)), // Peach Rose
            listOf(Color(0xFF667EEA), Color(0xFF764BA2)), // Royal Violet
            listOf(Color(0xFFF093FB), Color(0xFFF5576C)), // Bubblegum
            listOf(Color(0xFFFFA726), Color(0xFFFF7043)), // Citrus Orange
            listOf(Color(0xFF00E676), Color(0xFF1DE9B6)), // Emerald Turquoise
            listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)), // Deep Iris
            listOf(Color(0xFFFF5252), Color(0xFFFF79B0)), // Strawberry
        )
    }

    val palette = remember(seed, isDefaultAgent) {
        if (isDefaultAgent) {
            listOf(Color(0xFFFFA726), Color(0xFFFF7043)) // Vibrant Gold-Orange for Default
        } else {
            colorPalettes[seed % colorPalettes.size]
        }
    }
    val eyeStyle = remember(seed) { (seed / 7) % 4 } // 0: standard big anime eyes, 1: happy winking, 2: sleepy cute curves, 3: playful dots
    val mouthStyle = remember(seed) { (seed / 13) % 4 } // 0: happy arc, 1: cute cat 'w' smile, 2: open happy mouth, 3: playful tongue
    val hasBlush = remember(seed) { (seed % 2) == 0 }
    val hasCrown = isDefaultAgent || (seed % 11 == 0)
    val hasSprout = !hasCrown && ((seed % 3) == 0)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val width = this.size.width
            val height = this.size.height
            val center = Offset(width / 2f, height / 2f)
            val radius = width * 0.42f

            // 1. Draw top accessory (Crown or Sprout)
            if (hasCrown) {
                drawCrown(center, radius)
            } else if (hasSprout) {
                drawSprout(center, radius, palette[0])
            }

            // 2. Draw Organic Blob Body
            val blobPath = createBlobPath(center, radius, seed)
            drawPath(
                path = blobPath,
                brush = Brush.linearGradient(
                    colors = palette,
                    start = Offset(0f, 0f),
                    end = Offset(width, height),
                ),
            )

            // Subtle highlight rim on top
            drawPath(
                path = blobPath,
                color = Color.White.copy(alpha = 0.28f),
                style = Stroke(width = width * 0.035f),
            )

            // 3. Draw Cheeks (Blush)
            if (hasBlush) {
                val blushY = center.y + radius * 0.18f
                val blushRadiusX = radius * 0.18f
                val blushRadiusY = radius * 0.09f
                val blushOffset = radius * 0.48f

                drawOval(
                    color = Color(0xFFFF3366).copy(alpha = 0.35f),
                    topLeft = Offset(center.x - blushOffset - blushRadiusX, blushY - blushRadiusY),
                    size = Size(blushRadiusX * 2, blushRadiusY * 2),
                )
                drawOval(
                    color = Color(0xFFFF3366).copy(alpha = 0.35f),
                    topLeft = Offset(center.x + blushOffset - blushRadiusX, blushY - blushRadiusY),
                    size = Size(blushRadiusX * 2, blushRadiusY * 2),
                )
            }

            // 4. Draw Cute Cartoon Eyes
            drawCuteEyes(center, radius, eyeStyle)

            // 5. Draw Mouth
            drawCuteMouth(center, radius, mouthStyle)
        }
    }
}

private fun DrawScope.drawCrown(center: Offset, radius: Float) {
    val startY = center.y - radius * 0.86f
    val crownPath = Path().apply {
        moveTo(center.x - radius * 0.38f, startY + radius * 0.12f)
        lineTo(center.x - radius * 0.48f, startY - radius * 0.38f)
        lineTo(center.x - radius * 0.18f, startY - radius * 0.18f)
        lineTo(center.x, startY - radius * 0.48f)
        lineTo(center.x + radius * 0.18f, startY - radius * 0.18f)
        lineTo(center.x + radius * 0.48f, startY - radius * 0.38f)
        lineTo(center.x + radius * 0.38f, startY + radius * 0.12f)
        close()
    }
    // Gold gradient on crown
    drawPath(
        path = crownPath,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFFFEA00), Color(0xFFFF9100)),
            start = Offset(center.x - radius * 0.5f, startY - radius * 0.5f),
            end = Offset(center.x + radius * 0.5f, startY + radius * 0.2f),
        ),
    )
    drawPath(
        path = crownPath,
        color = Color(0xFFFFD700).copy(alpha = 0.7f),
        style = Stroke(width = radius * 0.05f),
    )
    // Little ruby & diamond jewels on crown tips
    drawCircle(color = Color(0xFFFF1744), radius = radius * 0.07f, center = Offset(center.x - radius * 0.48f, startY - radius * 0.38f))
    drawCircle(color = Color(0xFF00E5FF), radius = radius * 0.08f, center = Offset(center.x, startY - radius * 0.48f))
    drawCircle(color = Color(0xFFFF1744), radius = radius * 0.07f, center = Offset(center.x + radius * 0.48f, startY - radius * 0.38f))
}

private fun DrawScope.drawSprout(center: Offset, radius: Float, color: Color) {
    val startY = center.y - radius * 0.85f
    val path = Path().apply {
        moveTo(center.x, startY)
        quadraticTo(center.x - radius * 0.25f, startY - radius * 0.35f, center.x - radius * 0.15f, startY - radius * 0.4f)
        quadraticTo(center.x, startY - radius * 0.25f, center.x, startY)
        moveTo(center.x, startY)
        quadraticTo(center.x + radius * 0.25f, startY - radius * 0.35f, center.x + radius * 0.15f, startY - radius * 0.4f)
        quadraticTo(center.x, startY - radius * 0.25f, center.x, startY)
    }
    drawPath(path, color = color)
}

private fun createBlobPath(center: Offset, radius: Float, seed: Int): Path {
    val path = Path()
    val numPoints = 8
    val angleStep = (Math.PI * 2 / numPoints).toFloat()

    val points = mutableListOf<Offset>()
    for (i in 0 until numPoints) {
        val angle = i * angleStep
        // Modulate radius slightly for cute organic shape
        val wobble = sin(angle * 3 + seed) * (radius * 0.08f)
        val r = radius + wobble
        val x = center.x + cos(angle) * r
        val y = center.y + sin(angle) * (r * 0.95f) // slightly chubby bottom
        points.add(Offset(x, y))
    }

    path.moveTo(points[0].x, points[0].y)
    for (i in 0 until numPoints) {
        val current = points[i]
        val next = points[(i + 1) % numPoints]
        val midX = (current.x + next.x) / 2f
        val midY = (current.y + next.y) / 2f
        path.quadraticTo(current.x, current.y, midX, midY)
    }
    path.close()
    return path
}

private fun DrawScope.drawCuteEyes(center: Offset, radius: Float, style: Int) {
    val eyeSpacing = radius * 0.38f
    val eyeY = center.y - radius * 0.05f
    val eyeSize = radius * 0.16f
    val darkColor = Color(0xFF1A1A24)

    when (style) {
        0 -> { // Big glossy anime eyes
            // Left eye
            drawCircle(color = darkColor, radius = eyeSize, center = Offset(center.x - eyeSpacing, eyeY))
            drawCircle(color = Color.White, radius = eyeSize * 0.45f, center = Offset(center.x - eyeSpacing - eyeSize * 0.25f, eyeY - eyeSize * 0.25f))
            drawCircle(color = Color.White, radius = eyeSize * 0.20f, center = Offset(center.x - eyeSpacing + eyeSize * 0.25f, eyeY + eyeSize * 0.25f))

            // Right eye
            drawCircle(color = darkColor, radius = eyeSize, center = Offset(center.x + eyeSpacing, eyeY))
            drawCircle(color = Color.White, radius = eyeSize * 0.45f, center = Offset(center.x + eyeSpacing - eyeSize * 0.25f, eyeY - eyeSize * 0.25f))
            drawCircle(color = Color.White, radius = eyeSize * 0.20f, center = Offset(center.x + eyeSpacing + eyeSize * 0.25f, eyeY + eyeSize * 0.25f))
        }
        1 -> { // Winking happy eye
            // Left eye (open glossy)
            drawCircle(color = darkColor, radius = eyeSize, center = Offset(center.x - eyeSpacing, eyeY))
            drawCircle(color = Color.White, radius = eyeSize * 0.45f, center = Offset(center.x - eyeSpacing - eyeSize * 0.25f, eyeY - eyeSize * 0.25f))

            // Right eye (wink curve >)
            val winkPath = Path().apply {
                moveTo(center.x + eyeSpacing - eyeSize, eyeY)
                quadraticTo(center.x + eyeSpacing, eyeY - eyeSize * 0.8f, center.x + eyeSpacing + eyeSize, eyeY)
            }
            drawPath(winkPath, color = darkColor, style = Stroke(width = eyeSize * 0.4f, cap = StrokeCap.Round))
        }
        2 -> { // Happy smiling eyes (^ ^)
            val leftPath = Path().apply {
                moveTo(center.x - eyeSpacing - eyeSize, eyeY)
                quadraticTo(center.x - eyeSpacing, eyeY - eyeSize * 0.9f, center.x - eyeSpacing + eyeSize, eyeY)
            }
            val rightPath = Path().apply {
                moveTo(center.x + eyeSpacing - eyeSize, eyeY)
                quadraticTo(center.x + eyeSpacing, eyeY - eyeSize * 0.9f, center.x + eyeSpacing + eyeSize, eyeY)
            }
            drawPath(leftPath, color = darkColor, style = Stroke(width = eyeSize * 0.45f, cap = StrokeCap.Round))
            drawPath(rightPath, color = darkColor, style = Stroke(width = eyeSize * 0.45f, cap = StrokeCap.Round))
        }
        else -> { // Playful dots with highlights
            drawCircle(color = darkColor, radius = eyeSize * 0.85f, center = Offset(center.x - eyeSpacing, eyeY))
            drawCircle(color = Color.White, radius = eyeSize * 0.35f, center = Offset(center.x - eyeSpacing - eyeSize * 0.2f, eyeY - eyeSize * 0.2f))
            drawCircle(color = darkColor, radius = eyeSize * 0.85f, center = Offset(center.x + eyeSpacing, eyeY))
            drawCircle(color = Color.White, radius = eyeSize * 0.35f, center = Offset(center.x + eyeSpacing - eyeSize * 0.2f, eyeY - eyeSize * 0.2f))
        }
    }
}

private fun DrawScope.drawCuteMouth(center: Offset, radius: Float, style: Int) {
    val mouthY = center.y + radius * 0.22f
    val mouthWidth = radius * 0.22f
    val darkColor = Color(0xFF1A1A24)

    when (style) {
        0 -> { // Simple cute smile curve
            val path = Path().apply {
                moveTo(center.x - mouthWidth, mouthY)
                quadraticTo(center.x, mouthY + mouthWidth * 0.9f, center.x + mouthWidth, mouthY)
            }
            drawPath(path, color = darkColor, style = Stroke(width = radius * 0.06f, cap = StrokeCap.Round))
        }
        1 -> { // Cute 'w' / cat mouth
            val path = Path().apply {
                moveTo(center.x - mouthWidth, mouthY)
                quadraticTo(center.x - mouthWidth * 0.5f, mouthY + mouthWidth * 0.6f, center.x, mouthY + mouthWidth * 0.1f)
                quadraticTo(center.x + mouthWidth * 0.5f, mouthY + mouthWidth * 0.6f, center.x + mouthWidth, mouthY)
            }
            drawPath(path, color = darkColor, style = Stroke(width = radius * 0.06f, cap = StrokeCap.Round))
        }
        2 -> { // Open happy mouth :D
            val path = Path().apply {
                moveTo(center.x - mouthWidth, mouthY)
                quadraticTo(center.x, mouthY + mouthWidth * 1.3f, center.x + mouthWidth, mouthY)
                close()
            }
            drawPath(path, color = darkColor)
            // Tiny tongue
            val tonguePath = Path().apply {
                moveTo(center.x - mouthWidth * 0.6f, mouthY + mouthWidth * 0.6f)
                quadraticTo(center.x, mouthY + mouthWidth * 1.3f, center.x + mouthWidth * 0.6f, mouthY + mouthWidth * 0.6f)
            }
            drawPath(tonguePath, color = Color(0xFFFF5277))
        }
        else -> { // Subtle playful smirk
            val path = Path().apply {
                moveTo(center.x - mouthWidth * 0.7f, mouthY)
                quadraticTo(center.x + mouthWidth * 0.2f, mouthY + mouthWidth * 0.8f, center.x + mouthWidth, mouthY - mouthWidth * 0.2f)
            }
            drawPath(path, color = darkColor, style = Stroke(width = radius * 0.06f, cap = StrokeCap.Round))
        }
    }
}
