package com.persondot.ui.dot

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Formas dos Dots. Todas são desenhadas por código, sem imagens. */
enum class DotShape { GHOST, CLOUD, FLOWER, DROP, HEART, SPHERE, PILLOW, BLOB }

/** Humor do Dot (muda os olhos). Ligado à nota de saúde financeira. */
enum class Mood { HAPPY, NEUTRAL, WORRIED, SLEEPY }

data class DotDesign(val id: String, val name: String, val shape: DotShape, val color: Color, val eye: Color = Color.White)

/** Os Dots que você pode escolher. Para criar outro, é só adicionar uma linha aqui. */
val DotCatalog: List<DotDesign> = listOf(
    DotDesign("original", "Original", DotShape.GHOST, Color(0xFFEDEDF0), eye = Color(0xFF1C1C1E)),
    DotDesign("nuvem", "Nuvem", DotShape.CLOUD, Color(0xFF2563EB)),
    DotDesign("gota", "Gota", DotShape.DROP, Color(0xFFF97316)),
    DotDesign("flor", "Flor", DotShape.FLOWER, Color(0xFF14B8A6)),
    DotDesign("coracao", "Coração", DotShape.HEART, Color(0xFFEF4444)),
    DotDesign("bolha", "Bolha", DotShape.SPHERE, Color(0xFF1F1F23)),
    DotDesign("almofada", "Almofada", DotShape.PILLOW, Color(0xFFEC4899)),
    DotDesign("pedrinha", "Pedrinha", DotShape.BLOB, Color(0xFF16A34A)),
    DotDesign("noite", "Noite", DotShape.CLOUD, Color(0xFF8B5CF6)),
)

fun dotById(id: String?): DotDesign = DotCatalog.firstOrNull { it.id == id } ?: DotCatalog.first()

/** Contorno do corpo dentro de um quadrado w × h. */
private fun bodyPath(shape: DotShape, w: Float, h: Float): Path = Path().apply {
    when (shape) {
        DotShape.SPHERE -> addOval(Rect(0f, 0f, w, h))
        DotShape.PILLOW -> addRoundRect(RoundRect(Rect(0f, 0f, w, h), CornerRadius(w * 0.36f)))
        DotShape.GHOST -> {
            moveTo(0f, h * 0.66f)
            cubicTo(0f, h * 0.18f, w * 0.30f, 0f, w * 0.56f, 0f)
            cubicTo(w * 0.84f, 0f, w, h * 0.26f, w, h * 0.56f)
            lineTo(w, h * 0.82f)
            quadraticTo(w, h, w * 0.80f, h)
            lineTo(w * 0.20f, h)
            quadraticTo(0f, h, 0f, h * 0.82f)
            close()
        }
        DotShape.DROP -> {
            moveTo(w * 0.5f, 0f)
            cubicTo(w * 0.5f, 0f, w, h * 0.46f, w, h * 0.68f)
            cubicTo(w, h * 0.90f, w * 0.78f, h, w * 0.5f, h)
            cubicTo(w * 0.22f, h, 0f, h * 0.90f, 0f, h * 0.68f)
            cubicTo(0f, h * 0.46f, w * 0.5f, 0f, w * 0.5f, 0f)
            close()
        }
        DotShape.HEART -> {
            fun x(v: Float) = w * v / 100f
            fun y(v: Float) = h * v / 100f
            moveTo(x(50f), y(92f))
            cubicTo(x(20f), y(70f), x(2f), y(50f), x(2f), y(30f))
            cubicTo(x(2f), y(14f), x(14f), y(4f), x(28f), y(4f))
            cubicTo(x(38f), y(4f), x(46f), y(10f), x(50f), y(20f))
            cubicTo(x(54f), y(10f), x(62f), y(4f), x(72f), y(4f))
            cubicTo(x(86f), y(4f), x(98f), y(14f), x(98f), y(30f))
            cubicTo(x(98f), y(50f), x(80f), y(70f), x(50f), y(92f))
            close()
        }
        DotShape.CLOUD -> {
            addRoundRect(RoundRect(Rect(w * 0.0f, h * 0.38f, w, h), CornerRadius(h * 0.30f)))
            addOval(Rect(w * 0.06f, h * 0.18f, w * 0.52f, h * 0.64f))
            addOval(Rect(w * 0.26f, h * 0.0f, w * 0.78f, h * 0.56f))
            addOval(Rect(w * 0.58f, h * 0.20f, w * 0.98f, h * 0.60f))
        }
        DotShape.FLOWER -> {
            val c = Offset(w / 2, h / 2)
            val petal = w * 0.21f
            val dist = w * 0.27f
            for (k in 0 until 6) {
                val a = Math.toRadians(k * 60.0 - 90.0)
                val p = Offset(c.x + (dist * Math.cos(a)).toFloat(), c.y + (dist * Math.sin(a)).toFloat())
                addOval(Rect(p.x - petal, p.y - petal, p.x + petal, p.y + petal))
            }
            addOval(Rect(c.x - w * 0.30f, c.y - h * 0.30f, c.x + w * 0.30f, c.y + h * 0.30f))
        }
        DotShape.BLOB -> {
            moveTo(w * 0.10f, h * 0.55f)
            cubicTo(w * 0.00f, h * 0.20f, w * 0.38f, h * 0.0f, w * 0.64f, h * 0.06f)
            cubicTo(w * 0.94f, h * 0.12f, w * 1.04f, h * 0.48f, w * 0.92f, h * 0.74f)
            cubicTo(w * 0.82f, h * 0.98f, w * 0.40f, h * 1.04f, w * 0.20f, h * 0.88f)
            cubicTo(w * 0.08f, h * 0.80f, w * 0.12f, h * 0.66f, w * 0.10f, h * 0.55f)
            close()
        }
    }
}

/** Posição vertical do rosto, por forma (fração da altura). */
private fun faceY(shape: DotShape) = when (shape) {
    DotShape.GHOST -> 0.46f
    DotShape.CLOUD -> 0.50f
    DotShape.DROP -> 0.60f
    DotShape.HEART -> 0.42f
    DotShape.BLOB -> 0.46f
    else -> 0.48f
}

/**
 * O Dot: corpo com volume, olhos em pílula que piscam e olham em volta, e um leve flutuar.
 * [animate] = false desenha parado (para a grade de escolha).
 */
@Composable
fun DotAvatar(design: DotDesign, modifier: Modifier = Modifier, size: Dp = 160.dp, mood: Mood = Mood.NEUTRAL, animate: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "dot")
    val float by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float"
    )
    val blink by transition.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes {
            durationMillis = 4600
            1f at 0; 1f at 4300; 0.08f at 4400; 1f at 4560
        }), label = "blink"
    )
    val look by transition.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Reverse), label = "look"
    )
    val f = if (animate) float else 0.5f
    val b = if (animate) blink else 1f
    val l = if (animate) look else 0f

    Canvas(modifier.size(size)) { drawDot(design, mood, f, b, l) }
}

private fun DrawScope.drawDot(design: DotDesign, mood: Mood, float: Float, blink: Float, look: Float) {
    val s = this.size.minDimension
    val body = s * 0.78f
    val left = (s - body) / 2
    val top = s * 0.08f + (1f - float) * s * 0.035f

    // sombra no chão (diminui quando o Dot sobe)
    val shadowW = body * (0.62f + 0.10f * float)
    drawOval(Color.Black.copy(alpha = 0.22f - 0.06f * (1f - float)), Offset((s - shadowW) / 2, s * 0.90f), Size(shadowW, s * 0.055f))

    val path = bodyPath(design.shape, body, body)
    val sx = left; val sy = top
    translate(sx, sy) {
        drawPath(path, design.color)
        // volume: luz no canto superior esquerdo e sombra no inferior direito
        drawPath(path, Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.32f), Color.Transparent, Color.Black.copy(alpha = 0.26f)),
            center = Offset(body * 0.30f, body * 0.22f), radius = body * 1.05f))
        // brilho
        rotate(-28f, Offset(body * 0.28f, body * 0.2f)) {
            drawOval(Color.White.copy(alpha = 0.22f), Offset(body * 0.17f, body * 0.13f), Size(body * 0.20f, body * 0.09f))
        }

        // olhos
        val cy = body * faceY(design.shape)
        val gap = body * 0.15f
        val cx = body / 2 + look * body * 0.025f
        val eyeW = body * 0.085f
        val eyeH = body * 0.20f
        val (tilt, hScale) = when (mood) {
            Mood.HAPPY -> -12f to 1.10f
            Mood.NEUTRAL -> -14f to 1.0f
            Mood.WORRIED -> 14f to 1.0f
            Mood.SLEEPY -> -6f to 0.42f
        }
        val v = hScale * blink
        for (dx in listOf(-gap, gap)) {
            val c = Offset(cx + dx, cy)
            rotate(tilt, c) {
                scale(1f, v, c) {
                    drawRoundRect(design.eye, Offset(c.x - eyeW / 2, c.y - eyeH / 2), Size(eyeW, eyeH), CornerRadius(eyeW / 2))
                }
            }
        }
    }
}
