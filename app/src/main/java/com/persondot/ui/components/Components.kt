package com.persondot.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.persondot.domain.Level
import com.persondot.domain.mLabel
import com.persondot.ui.theme.*

/** Cartão do Bento Grid: mesma linguagem visual do mindful-finance. */
@Composable
fun BentoCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth()
    val colors = CardDefaults.cardColors(containerColor = DarkSurface)
    val shape = RoundedCornerShape(24.dp)
    val border = BorderStroke(1.dp, CardBorderGlance)
    if (onClick != null) Card(onClick = onClick, modifier = m, colors = colors, shape = shape, border = border) { Column(Modifier.padding(16.dp), content = content) }
    else Card(modifier = m, colors = colors, shape = shape, border = border) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
fun LabelText(text: String, color: Color = SecondaryText) {
    Text(text.uppercase(), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
}

/** Título de seção com um botão opcional à direita (ex.: "+ conta"). */
@Composable
fun SectionHeader(title: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        LabelText(title)
        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                Text(actionText, color = CyberGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun StatRow(
    label: String, value: String, sub: String? = null, valueColor: Color = AppTextWhite,
    leading: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
            Column {
                Text(label, color = AppTextWhite, style = MaterialTheme.typography.bodyMedium)
                if (sub != null) Text(sub, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(value, color = valueColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
    HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
}

@Composable
fun MetricProgress(pct: Double, color: Color = CyberGreen, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { (pct / 100.0).coerceIn(0.0, 1.0).toFloat() },
        modifier = modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
        color = color, trackColor = BorderColor, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {}
    )
}

@Composable
fun levelColor(l: Level): Color = when (l) { Level.OK -> CyberGreen; Level.WARN -> Color(0xFFF59E0B); Level.BAD -> ImpulseAmber }

@Composable
fun Dot(level: Level) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(levelColor(level)))
}

@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)).padding(10.dp)) {
        Text(text, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun MonthChips(months: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(months) { m ->
            FilterChip(
                selected = m == selected, onClick = { onSelect(m) }, label = { Text(mLabel(m)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyberGreen, selectedLabelColor = Color.Black)
            )
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier, colors = ButtonDefaults.buttonColors(containerColor = CyberGreen, contentColor = Color.Black), shape = RoundedCornerShape(14.dp)) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false) {
    OutlinedButton(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, if (danger) ImpulseAmber else CardBorderGlance),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) ImpulseAmber else AppTextWhite)
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun EmptyHint(text: String) {
    Text(text, color = SecondaryText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 10.dp))
}

/** Anel de progresso animado (0 a 1) com um valor no centro e um rótulo embaixo. */
@Composable
fun ScoreRing(progress: Float, center: String, label: String, color: Color, modifier: Modifier = Modifier, size: Dp = 78.dp) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(1000), label = "ring")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = this.size.minDimension * 0.13f
                val d = this.size.minDimension - w
                drawArc(BorderColor, 0f, 360f, false, Offset(w / 2, w / 2), Size(d, d), style = Stroke(w))
                drawArc(color, -90f, 360f * p, false, Offset(w / 2, w / 2), Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
            }
            Text(center, color = AppTextWhite, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        }
        Text(label, color = SecondaryText, style = MaterialTheme.typography.labelSmall)
    }
}
