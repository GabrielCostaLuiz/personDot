package com.persondot.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.persondot.ui.theme.*

@Composable
fun HelpAccordion(
    title: String,
    explanation: String,
    formula: String? = null,
    example: String? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (expanded) PulseViolet.copy(0.06f) else Color.Transparent)
            .border(
                width = 0.5.dp,
                color = if (expanded) PulseViolet.copy(0.4f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = "Entenda o card",
                    tint = if (expanded) CyberTeal else SecondaryText,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = title,
                    color = if (expanded) CyberTeal else SecondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Fechar" else "Detalhar",
                tint = SecondaryText,
                modifier = Modifier.size(16.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(color = BorderColor.copy(0.3f))
                
                Column {
                    Text(
                        text = "ENTENDA EM PORTUGUÊS CLARO:",
                        color = CyberTeal,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = explanation,
                        color = AppTextWhite.copy(0.9f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                if (formula != null) {
                    Column {
                        Text(
                            text = "COMO É CALCULADO / FORMULAÇÃO:",
                            color = PulseViolet,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceElevated)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = formula,
                                color = AppTextWhite,
                                fontSize = 10.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }

                if (example != null) {
                    Column {
                        Text(
                            text = "EXEMPLO PRÁTICO:",
                            color = CyberGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = example,
                            color = SecondaryText,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}
