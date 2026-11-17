@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.persondot.ui.dot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.persondot.domain.FinanceData
import com.persondot.domain.health
import com.persondot.ui.components.LabelText
import com.persondot.ui.theme.*

/** O humor do Dot acompanha a saúde financeira. */
fun moodFor(data: FinanceData): Mood {
    if (data.isEmpty) return Mood.SLEEPY
    val score = data.health().score
    return if (score >= 75) Mood.HAPPY else if (score >= 50) Mood.NEUTRAL else Mood.WORRIED
}

/** Cores para tingir o Dot (além da cor padrão de cada desenho). */
val DotPalette: List<Color> = listOf(
    Color(0xFF10B981), Color(0xFF14B8A6), Color(0xFF06B6D4), Color(0xFF2563EB), Color(0xFF8B5CF6), Color(0xFFEC4899),
    Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFEAB308), Color(0xFF84CC16), Color(0xFF64748B), Color(0xFFE2E8F0)
)

/** Escolha do desenho e da cor do Dot. Usado em Ajustes → Dot. */
@Composable
fun DotPicker(selectedId: String, customColor: Color?, onPick: (String) -> Unit, onColor: (Color?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LabelText("Desenho")
        DotCatalog.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { d ->
                    val selected = d.id == selectedId
                    val shown = if (selected && customColor != null) d.copy(color = customColor) else d
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                            .background(if (selected) CyberGreen.copy(alpha = 0.10f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                            .border(if (selected) BorderStroke(2.dp, CyberGreen) else BorderStroke(0.5.dp, BorderColor), RoundedCornerShape(18.dp))
                            .clickable { onPick(d.id) }.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        DotAvatar(shown, size = 72.dp, animate = false)
                        Text(d.name, color = if (selected) CyberGreen else AppTextWhite, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        LabelText("Cor")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).border(if (customColor == null) BorderStroke(3.dp, CyberGreen) else BorderStroke(1.dp, BorderColor), CircleShape)
                    .clickable { onColor(null) }, contentAlignment = Alignment.Center
            ) { Text("auto", color = AppTextWhite, style = MaterialTheme.typography.labelSmall) }
            DotPalette.forEach { c ->
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(c)
                        .border(if (customColor == c) BorderStroke(3.dp, AppTextWhite) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                        .clickable { onColor(c) }
                )
            }
        }
    }
}
