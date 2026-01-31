package com.wildlifespotter.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.*
import kotlin.random.Random

@Composable
fun AnimatedWaveBackground(
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF4CAF50),
    secondaryColor: Color = Color(0xFF2EA333)
) {
    var phase by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                phase += 0.02f
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        
        // Disegna 3 onde sovrapposte
        for (i in 0..2) {
            val path = Path()
            val amplitude = height * 0.08f * (i + 1)
            val frequency = 0.008f / (i + 1)
            val phaseShift = phase + i * PI.toFloat() / 3
            
            path.moveTo(0f, height / 2)
            
            for (x in 0..width.toInt() step 5) {
                val y = height / 2 + amplitude * sin(x * frequency + phaseShift)
                path.lineTo(x.toFloat(), y)
            }
            
            path.lineTo(width, height)
            path.lineTo(0f, height)
            path.close()
            
            val alpha = 0.3f - (i * 0.1f)
            val color = if (i % 2 == 0) primaryColor else secondaryColor
            
            drawPath(
                path = path,
                color = color.copy(alpha = alpha),
                style = Fill
            )
        }
    }
}

@Composable
fun FloatingParticles(
    modifier: Modifier = Modifier,
    particleCount: Int = 20,
    color: Color = Color.White.copy(alpha = 0.3f)
) {
    val particles = remember {
        List(particleCount) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                speed = Random.nextInt(20, 50) / 10_000f,
                size = Random.nextInt(2, 6).toFloat()
            )
        }
    }

    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                time += 0.016f
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        particles.forEach { particle ->
            val x = size.width * particle.x
            val y = ((particle.y + time * particle.speed) % 1f) * size.height

            drawCircle(
                color = color,
                radius = particle.size,
                center = Offset(x, y)
            )
        }
    }
}

private data class Particle(
    val x: Float,
    val y: Float,
    val speed: Float,
    val size: Float
)

@Composable
fun AnimatedCompass(
    azimuth: Float,
    modifier: Modifier = Modifier,
    size: Float = 200f
) {
    val animatedAzimuth by animateFloatAsState(
        targetValue = azimuth,
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
        label = "compass"
    )
    
    Canvas(
        modifier = modifier.size(size.dp)
    ) {
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val radius = this.size.minDimension / 2
        
        // Cerchio esterno
        drawCircle(
            color = Color(0xFF374B5E),
            radius = radius,
            center = center,
            style = Fill
        )
        
        // Cerchio interno
        drawCircle(
            color = Color(0xFF2D3E50),
            radius = radius * 0.85f,
            center = center,
            style = Fill
        )
        
        // Direzioni cardinali
        val directions = listOf("N", "E", "S", "W")
        directions.forEachIndexed { index, direction ->
            val angle = index * 90f
            val rad = Math.toRadians(angle.toDouble())
            val textOffset = Offset(
                center.x + (radius * 0.7f * sin(rad)).toFloat(),
                center.y - (radius * 0.7f * cos(rad)).toFloat()
            )
            
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 40f
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.DEFAULT,
                        android.graphics.Typeface.BOLD
                    )
                }
                drawText(
                    direction,
                    textOffset.x,
                    textOffset.y + 12f,
                    paint
                )
            }
        }
        
        // Ago della bussola
        rotate(animatedAzimuth, center) {
            val arrowPath = Path().apply {
                moveTo(center.x, center.y - radius * 0.6f)
                lineTo(center.x - 15f, center.y + 15f)
                lineTo(center.x, center.y)
                lineTo(center.x + 15f, center.y + 15f)
                close()
            }
            
            drawPath(
                path = arrowPath,
                color = Color(0xFFE53935)
            )
            
            // Parte posteriore dell'ago
            val backPath = Path().apply {
                moveTo(center.x, center.y + radius * 0.6f)
                lineTo(center.x - 10f, center.y - 10f)
                lineTo(center.x, center.y)
                lineTo(center.x + 10f, center.y - 10f)
                close()
            }
            
            drawPath(
                path = backPath,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        
        // Centro
        drawCircle(
            color = Color(0xFF4CAF50),
            radius = 12f,
            center = center
        )
    }
}

@Composable
fun CircularStepIndicator(
    currentSteps: Int,
    goalSteps: Int = 10000,
    modifier: Modifier = Modifier,
    size: Float = 200f,
    primaryColor: Color = Color(0xFF4CAF50),
    backgroundColor: Color = Color(0xFF374B5E)
) {
    val progress = (currentSteps.toFloat() / goalSteps).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "progress"
    )
    
    Canvas(modifier = modifier.size(size.dp)) {
        val strokeWidth = 20f
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val radius = (this.size.minDimension - strokeWidth) / 2
        
        // Cerchio di sfondo
        drawCircle(
            color = backgroundColor,
            radius = radius + strokeWidth / 2,
            center = center,
            style = Stroke(width = strokeWidth)
        )
        
        // Arco di progresso
        val sweepAngle = 360f * animatedProgress
        drawArc(
            color = primaryColor,
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        
        // Testo centrale
        drawContext.canvas.nativeCanvas.apply {
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 48f
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
                )
            }
            
            drawText(
                currentSteps.toString(),
                center.x,
                center.y,
                textPaint
            )
            
            textPaint.textSize = 24f
            textPaint.color = android.graphics.Color.LTGRAY
            
            drawText(
                "steps",
                center.x,
                center.y + 40f,
                textPaint
            )
        }
    }
}

@Composable
fun WaveActivityGraph(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF4CAF50)
) {
    var phase by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                phase += 0.03f
            }
        }
    }
    
    Canvas(modifier = modifier
        .fillMaxWidth()
        .height(100.dp)) {
        val width = size.width
        val height = size.height
        val path = Path()
        
        path.moveTo(0f, height / 2)
        
        for (x in 0..width.toInt() step 10) {
            val y = height / 2 + (height / 4) * sin((x / 30f) + phase)
            if (x == 0) {
                path.moveTo(x.toFloat(), y)
            } else {
                path.lineTo(x.toFloat(), y)
            }
        }
        
        // Linea principale
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
        
        // Area sottostante
        val filledPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        
        drawPath(
            path = filledPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = 0.3f),
                    Color.Transparent
                )
            )
        )
    }
}

@Composable
fun GeometricParallaxBackground(
    modifier: Modifier = Modifier,
    scrollOffset: Float = 0f
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        
        // Triangoli grandi sullo sfondo
        for (i in 0..3) {
            val offsetY = (scrollOffset * 0.3f * i) % height
            val triangle = Path().apply {
                moveTo(width * (i * 0.3f), offsetY)
                lineTo(width * (i * 0.3f + 0.2f), offsetY + 200f)
                lineTo(width * (i * 0.3f - 0.1f), offsetY + 200f)
                close()
            }
            
            drawPath(
                path = triangle,
                color = Color.White.copy(alpha = 0.05f)
            )
        }
        
        // Cerchi piccoli
        for (i in 0..5) {
            val offsetY = (scrollOffset * 0.5f * i) % height
            drawCircle(
                color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                radius = 30f + (i * 10f),
                center = Offset(width * (i * 0.2f), offsetY)
            )
        }
    }
}

@Composable
fun GradientBorderCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var rotation by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                rotation += 0.5f
                if (rotation >= 360f) rotation = 0f
            }
        }
    }
    
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier.matchParentSize()
        ) {
            val gradient = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFF4CAF50),
                    Color(0xFF2EA333),
                    Color(0xFFFFC107),
                    Color(0xFF4CAF50)
                ),
                center = Offset(size.width / 2, size.height / 2)
            )
            
            rotate(rotation) {
                drawRoundRect(
                    brush = gradient,
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                )
            }
        }
        
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(3.dp)
        ) {
            content()
        }
    }
}
