package com.persondot.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.persondot.data.model.FixedCost
import com.persondot.data.model.groupLabel
import com.persondot.data.model.groups
import com.persondot.domain.*
import com.persondot.ui.components.*
import com.persondot.ui.theme.*
import com.persondot.ui.viewmodel.FinanceViewModel
import java.time.LocalDate

/** Relatórios: a sua vida financeira em gráficos simples, para enxergar a evolução. */
@Composable
fun ReportsScreen(data: FinanceData, vm: FinanceViewModel, modifier: Modifier = Modifier) {
    val today = remember { LocalDate.now() }
    val now = remember { monthKey(today) }
    val months = remember { (-3..2).map { addMonths(now, it) } }
    var month by rememberSaveable { mutableStateOf(now) }
    var editRevenue by remember { mutableStateOf<String?>(null) }
    val c = remember(data) { data.core() }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp + LocalBarInset.current)) {
        // ---- patrimônio ----
        item {
            val points = remember(data) {
                val saved = data.snapshots.associate { it.month to (it.inHand + it.ipca - it.owed) }.toMutableMap()
                saved[now] = c.inHand + c.ipca - c.owed
                saved.toList().sortedBy { it.first }.takeLast(12)
            }
            BentoCard {
                SectionHeader("Patrimônio")
                Text(brl(c.inHand + c.ipca - c.owed), color = AppTextWhite, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("Em conta ${brl0(c.inHand)} + IPCA+ ${brl0(c.ipca)} − parcelas ${brl0(c.owed)}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                if (points.size < 2) Note("O histórico começa agora: eu guardo uma foto do seu patrimônio todo mês. Volte mais tarde para ver a curva.")
                else {
                    LineChart(points.map { it.second.toFloat() }, CyberGreen)
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(mLabel(points.first().first), color = SecondaryText, style = MaterialTheme.typography.labelSmall)
                        val delta = points.last().second - points.first().second
                        Text("${if (delta >= 0) "+" else "−"}${brl0(kotlin.math.abs(delta))} no período", color = if (delta >= 0) CyberGreen else ImpulseAmber, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(mLabel(points.last().first), color = SecondaryText, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // ---- previsto x pago ----
        item {
            val plans = remember(data) { months.map { it to data.monthPlan(it) } }
            val maxV = plans.maxOfOrNull { it.second.previsto }?.takeIf { it > 0 } ?: 1.0
            BentoCard {
                SectionHeader("Previsto x pago")
                Text("O que estava previsto sair em cada mês e o que você já confirmou.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                    plans.forEach { (m, p) ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                                Bar((p.previsto / maxV).toFloat(), CyberTeal)
                                Bar((p.pago / maxV).toFloat(), CyberGreen)
                            }
                            Text(mLabel(m).substringBefore('/'), color = if (m == now) CyberGreen else SecondaryText, style = MaterialTheme.typography.labelSmall, fontWeight = if (m == now) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { Legend("Previsto", CyberTeal); Legend("Pago", CyberGreen) }
            }
        }

        // ---- gastos por categoria ----
        item {
            val p = remember(data, month) { data.monthPlan(month) }
            val st = data.settings
            val rows = remember(data, month) {
                buildList {
                    st.groups.forEach { g -> add(g.name to p.forecasts.filter { it.cost.grp == g.id && it.counts }.sumOf { it.amount }) }
                    add("Parcelas e faturas" to p.parcelas)
                    data.expenses.filter { it.date.startsWith(month) }.groupBy { it.cat }.forEach { (k, v) -> add(k to v.sumOf { it.amount }) }
                }.filter { it.second > 0 }.sortedByDescending { it.second }
            }
            val total = rows.sumOf { it.second }
            BentoCard {
                SectionHeader("Para onde vai o dinheiro")
                MonthChips(months, month) { month = it }
                Spacer(Modifier.height(10.dp))
                if (rows.isEmpty()) EmptyHint("Nada previsto ou lançado neste mês.")
                rows.forEach { (name, v) ->
                    Column(Modifier.padding(vertical = 5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, color = AppTextWhite, style = MaterialTheme.typography.bodyMedium)
                            Text("${brl0(v)} · ${(v / total * 100).toInt()}%", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        }
                        MetricProgress(v / rows.first().second * 100, CyberTeal, Modifier.padding(top = 4.dp))
                    }
                }
                if (rows.isNotEmpty()) StatRow("Total", brl(total), valueColor = CyberGreen)
            }
        }

        // ---- MEI ----
        data.mei(today)?.let { m ->
            item {
                val maxV = maxOf(m.monthly.maxOf { it.second }, m.limit / 12 * 1.2).takeIf { it > 0 } ?: 1.0
                BentoCard {
                    SectionHeader("Faturamento do MEI em ${today.year}")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Até agora", color = SecondaryText, style = MaterialTheme.typography.labelSmall); Text(brl0(m.ytd), color = AppTextWhite, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) }
                        Column(horizontalAlignment = Alignment.End) { Text("Limite", color = SecondaryText, style = MaterialTheme.typography.labelSmall); Text(brl0(m.limit), color = AppTextWhite, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) }
                    }
                    Spacer(Modifier.height(8.dp))
                    MetricProgress(m.usedPct * 100, if (m.projectedYear > m.limit) ImpulseAmber else CyberGreen)
                    Text(
                        if (m.projectedYear > m.limit) "No ritmo atual você passa do limite em ${mLabel(m.crossMonth)}." else "No ritmo atual você fecha o ano com ${brl0(m.projectedYear)}, dentro do limite.",
                        color = if (m.projectedYear > m.limit) ImpulseAmber else SecondaryText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                        m.monthly.forEach { (mm, v) ->
                            val real = data.revenues.any { it.month == mm }
                            val future = mm > now
                            Column(Modifier.weight(1f).fillMaxHeight().clickable { editRevenue = mm }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                Row(Modifier.weight(1f)) { Bar((v / maxV).toFloat(), if (future) SecondaryText.copy(0.4f) else if (real) CyberGreen else CyberTeal.copy(0.6f), Modifier.fillMaxWidth()) }
                                Text(mLabel(mm).substringBefore('/').take(1).uppercase(), color = SecondaryText, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Legend("Real", CyberGreen); Legend("Estimado", CyberTeal.copy(0.6f)); Legend("Futuro", SecondaryText.copy(0.4f)) }
                    Note("Toque em um mês para registrar o faturamento real. Regras do MEI (limite, DAS, declaração) devem ser conferidas na Receita ou com um contador.")
                }
            }
        }
    }

    editRevenue?.let { mm ->
        val cur = data.revenues.firstOrNull { it.month == mm }
        NumberDialog("Faturamento de ${mLabel(mm)}", "Valor (R$)", cur?.amount ?: data.revenueOf(mm),
            hint = if (cur == null) "Hoje esse mês usa a sua renda (${brl0(data.settings.income)}) como estimativa." else null,
            onSave = { vm.saveRevenue(mm, it) }) { editRevenue = null }
    }
}

@Composable
private fun RowScope.Bar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.weight(1f).fillMaxHeight(fraction.coerceIn(0.02f, 1f)).align(Alignment.Bottom).clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)).background(color))
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color)); Spacer(Modifier.width(5.dp))
        Text(text, color = SecondaryText, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LineChart(values: List<Float>, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val min = values.min(); val max = values.max(); val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v -> Offset(i * stepX, size.height * 0.92f - (v - min) / range * size.height * 0.8f) }
        val area = Path().apply { moveTo(pts.first().x, size.height); pts.forEach { lineTo(it.x, it.y) }; lineTo(pts.last().x, size.height); close() }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), Color.Transparent)))
        for (i in 0 until pts.size - 1) drawLine(color, pts[i], pts[i + 1], strokeWidth = 5f, cap = StrokeCap.Round)
        pts.forEach { drawCircle(color, 7f, it) }
    }
}
