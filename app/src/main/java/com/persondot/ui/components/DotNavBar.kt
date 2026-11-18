package com.persondot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.persondot.ui.theme.CyberGreen
import kotlin.math.asin
import kotlin.math.sqrt

/** Altura que a barra inferior ocupa por cima do conteúdo (0 quando ela está escondida). As listas usam como respiro no fim. */
val LocalBarInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

/** Uma aba da barra inferior. */
data class NavEntry(val label: String, val icon: ImageVector, val selected: Boolean, val tag: String, val onClick: () -> Unit)

/**
 * Barra inferior com a ação principal (o Dot) num círculo que salta para fora, no centro, apoiado num "berço" recortado
 * na barra (mesmo desenho do app do Mesly). As abas se dividem dos dois lados.
 */
@Composable
fun DotNavBar(
    left: List<NavEntry>, right: List<NavEntry>, accent: Color, centerSelected: Boolean,
    onCenter: () -> Unit, modifier: Modifier = Modifier, center: @Composable () -> Unit
) {
    val bar = MaterialTheme.colorScheme.surface
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(64.dp)) {
            Canvas(Modifier.matchParentSize()) {
                val cx = size.width / 2
                val cy = 5.dp.toPx()                       // o centro do furo fica 5dp abaixo do topo da barra
                val hole = 41.dp.toPx()
                val body = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
                val cut = Path().apply { addOval(Rect(Offset(cx, cy), hole)) }
                drawPath(Path.combine(PathOperation.Difference, body, cut), bar)
                // linha do topo, interrompida onde o berço começa
                val stroke = 1.dp.toPx()
                val dx = sqrt(hole * hole - cy * cy)
                drawLine(line, Offset(0f, 0f), Offset(cx - dx, 0f), stroke)
                drawLine(line, Offset(cx + dx, 0f), Offset(size.width, 0f), stroke)
                // a borda do berço: só a parte do círculo que fica abaixo da linha da barra
                val ra = hole + 1.dp.toPx()
                val start = Math.toDegrees(asin((-cy / ra).toDouble())).toFloat()
                drawArc(line, startAngle = start, sweepAngle = 180f - 2 * start, useCenter = false,
                    topLeft = Offset(cx - ra, cy - ra), size = Size(ra * 2, ra * 2), style = Stroke(stroke))
            }
            Row(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) { left.forEach { Tab(it, Modifier.weight(1f)) } }
                Spacer(Modifier.width(84.dp))
                Row(Modifier.weight(1f)) { right.forEach { Tab(it, Modifier.weight(1f)) } }
            }
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = (-28).dp).size(64.dp)
                    .shadow(if (centerSelected) 10.dp else 6.dp, CircleShape, ambientColor = accent, spotColor = accent)
                    .clip(CircleShape).background(bar)
                    .border(BorderStroke(if (centerSelected) 3.dp else 2.dp, accent), CircleShape)
                    .clickable(onClick = onCenter),
                contentAlignment = Alignment.Center
            ) { center() }
        }
        Spacer(Modifier.fillMaxWidth().background(bar).windowInsetsPadding(WindowInsets.navigationBars))
    }
}

@Composable
private fun Tab(e: NavEntry, modifier: Modifier) {
    val color = if (e.selected) CyberGreen else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.fillMaxHeight().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).clickable(onClick = e.onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(e.icon, e.label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        Text(e.label, color = color, fontSize = 10.sp, fontWeight = if (e.selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}
