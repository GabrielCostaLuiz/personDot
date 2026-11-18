package com.persondot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.persondot.data.model.Goal
import com.persondot.domain.*
import com.persondot.ui.components.*
import com.persondot.ui.dot.LocalDot
import com.persondot.ui.dot.Mood
import com.persondot.ui.theme.*
import com.persondot.ui.viewmodel.FinanceViewModel
import kotlin.math.roundToInt

/**
 * Aba Futuro: tudo para EVOLUIR. A linha do tempo das conquistas, o "E se...?" para testar caminhos mais rápidos,
 * as contas riscadas (regra dos 200) e as metas.
 */
@Composable
fun FutureScreen(data: FinanceData, vm: FinanceViewModel, modifier: Modifier = Modifier) {
    val dot = LocalDot.current
    val c = remember(data) { data.core() }
    val milestones = remember(data) { data.milestones() }
    val riscadas = remember(data) { data.riscadas() }
    var extra by rememberSaveable { mutableFloatStateOf(0f) }
    var raise by rememberSaveable { mutableFloatStateOf(0f) }
    var cutOn by rememberSaveable { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<Any?>(null) }
    val w = remember(data, extra, raise, cutOn) { data.whatIf(extra.toDouble(), cutOn, raise.toDouble()) }
    val nextIdx = milestones.indexOfFirst { !it.done }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp + LocalBarInset.current)) {
        // ---- linha do tempo ----
        item {
            BentoCard {
                SectionHeader("Sua evolução")
                Text("O caminho do que você já conquistou até a independência das contas, no ritmo do seu plano.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                if (milestones.isEmpty()) EmptyHint("Cadastre seus custos e contas para eu montar o caminho.")
                milestones.forEachIndexed { i, m ->
                    val isNext = i == nextIdx
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.width(26.dp), contentAlignment = Alignment.TopCenter) {
                            Text(if (m.done) "✓" else if (isNext) "●" else "○", color = if (m.done) CyberGreen else if (isNext) CyberTeal else SecondaryText, fontWeight = FontWeight.ExtraBold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(m.title, color = if (m.done) CyberGreen else AppTextWhite, fontWeight = if (isNext) FontWeight.ExtraBold else FontWeight.SemiBold)
                            Text(m.detail, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (m.done) "feito" else mLabel(m.month), color = if (isNext) CyberTeal else SecondaryText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                    if (i < milestones.lastIndex) HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                }
            }
        }

        // ---- e se...? ----
        item {
            BentoCard {
                SectionHeader("E se...?")
                Text("Mexa nos controles e veja quanto o caminho encurta. Nada disso é salvo: é só um teste.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Guardar a mais por mês", color = AppTextWhite); Text(brl0(extra.toDouble()), color = CyberGreen, fontWeight = FontWeight.Bold)
                }
                Slider(extra, { extra = (it / 100f).roundToInt() * 100f }, valueRange = 0f..2000f, colors = SliderDefaults.colors(thumbColor = CyberGreen, activeTrackColor = CyberGreen))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Aumento de renda (metade vai para o aporte)", color = AppTextWhite, modifier = Modifier.weight(1f)); Text(brl0(raise.toDouble()), color = CyberGreen, fontWeight = FontWeight.Bold)
                }
                Slider(raise, { raise = (it / 100f).roundToInt() * 100f }, valueRange = 0f..3000f, colors = SliderDefaults.colors(thumbColor = CyberGreen, activeTrackColor = CyberGreen))
                val cutMonthly = remember(data) { data.cutOptional(data.cutRefMonth()).monthly }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Cortar os opcionais", color = AppTextWhite)
                        Text("libera ${brl0(cutMonthly)} por mês", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(cutOn, { cutOn = it }, colors = SwitchDefaults.colors(checkedTrackColor = CyberGreen))
                }
                Spacer(Modifier.height(10.dp))
                val baseRes = remember(data) { data.reserveEta() }
                val baseBill = remember(data) { data.riscadas().firstOrNull { !it.done }?.eta }
                StatRow("Aporte total a mais", "${brl0(w.extraMonthly)}/mês", valueColor = if (w.extraMonthly > 0) CyberGreen else AppTextWhite)
                StatRow("Reserva completa", mLabel(w.reserveEta), sub = if (w.reserveSooner > 0) "${w.reserveSooner} ${if (w.reserveSooner == 1) "mês" else "meses"} antes (hoje: ${mLabel(baseRes)})" else "no ritmo atual: ${mLabel(baseRes)}",
                    valueColor = if (w.reserveSooner > 0) CyberGreen else AppTextWhite)
                if (riscadas.any { !it.done }) StatRow("Primeira conta riscada", mLabel(w.firstBillEta), sub = if (w.firstBillSooner > 0) "${w.firstBillSooner} ${if (w.firstBillSooner == 1) "mês" else "meses"} antes (hoje: ${mLabel(baseBill)})" else "no ritmo atual: ${mLabel(baseBill)}",
                    valueColor = if (w.firstBillSooner > 0) CyberGreen else AppTextWhite)
                if (w.reserveSooner > 0 || w.firstBillSooner > 0) {
                    SoftButton("Dot, comente isso", { dot.react("Com ${brl0(w.extraMonthly)} a mais por mês você chega ${w.reserveSooner} meses mais cedo na reserva.", Mood.HAPPY) }, Modifier.padding(top = 8.dp))
                }
            }
        }

        // ---- contas riscadas ----
        item {
            BentoCard {
                LabelText("Balde das contas riscadas (IPCA+)", CyberGreen)
                Text(brl(c.ipca), color = AppTextWhite, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("Meta de cada conta = valor × ${data.settings.multiplier.toInt()} (rende ${"%.1f".format(data.settings.realRate * 100)}% ao ano acima da inflação). ${riscadas.count { it.done }} de ${riscadas.size} riscadas.",
                    color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Note("Só coloque dinheiro aqui depois que a reserva de emergência estiver completa. No Tesouro IPCA+, vender antes do vencimento pode render menos do que você colocou.")
            }
        }
        item {
            HelpAccordion(
                title = "Como funciona a regra dos 200?",
                explanation = "O rendimento real de 0,5% ao mês (6% ao ano acima da inflação) de um valor guardado paga uma conta sem encostar no principal. Para cada R\$ 1 de conta mensal você precisa de R\$ 200 guardados. Quando o balde cobre uma conta, ela é riscada: está paga pelo dinheiro, não pelo seu trabalho.",
                formula = "Meta da conta = valor mensal × 200",
                example = "Luz de R\$ 134 → R\$ 26.800 guardados rendem ~R\$ 134 por mês. Para mais folga, use ×250 em Ajustes."
            )
        }
        item {
            BentoCard {
                SectionHeader("Da menor para a maior")
                if (riscadas.isEmpty()) EmptyHint("Cadastre custos do tipo Casa na aba Gastos.")
                riscadas.forEach { x ->
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (x.done) "✓ ${x.cost.name}" else x.cost.name, color = if (x.done) CyberGreen else AppTextWhite, fontWeight = FontWeight.Bold)
                            Text("${brl(x.cost.amount)}/mês", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        MetricProgress(x.pct, if (x.done) CyberGreen else CyberTeal)
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Acumulado necessário ${brl0(x.cum)}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                            Text(if (x.done) "riscada" else x.eta?.let { "previsão ${mLabel(it)}" } ?: "defina o aporte", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                }
                Text("Previsão com aporte de ${brl0(data.settings.aporteLongo)}/mês, começando depois que a reserva fechar, em dinheiro de hoje. É estimativa, não garantia.",
                    color = SecondaryText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }

        // ---- metas ----
        item {
            BentoCard {
                SectionHeader("Metas", "+ meta") { dialog = "new" }
                if (data.goals.isEmpty()) EmptyHint("Nenhuma meta ainda. Viagem, curso, o que você quiser conquistar.")
                data.goals.forEach { g ->
                    val pct = if (g.targetValue > 0) g.currentValue / g.targetValue * 100 else 0.0
                    Column(Modifier.padding(vertical = 6.dp)) {
                        StatRow(g.name, "${brl0(g.currentValue)} / ${brl0(g.targetValue)}", sub = "${pct.toInt()}%", onClick = { dialog = g })
                        MetricProgress(pct, if (pct >= 100) CyberGreen else PulseViolet)
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        "new" -> GoalDialog(null, { vm.saveGoal(it) }, null) { dialog = null }
        is Goal -> GoalDialog(d, { vm.saveGoal(it) }, { vm.deleteGoal(d) }) { dialog = null }
    }
}
