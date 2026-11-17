package com.persondot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.persondot.data.model.*
import com.persondot.domain.*
import com.persondot.ui.components.*
import com.persondot.ui.theme.*
import com.persondot.ui.viewmodel.FinanceViewModel

/** Aba Metas: contas riscadas (regra dos 200), metas gerais e o carro à vista com custo real de propriedade. */
@Composable
fun GoalsScreen(data: FinanceData, vm: FinanceViewModel, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            listOf("Contas riscadas", "Carro à vista").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 2),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = CyberGreen, activeContentColor = androidx.compose.ui.graphics.Color.Black, inactiveContentColor = AppTextWhite)
                ) { Text(label) }
            }
        }
        if (tab == 0) CrossedBills(data, vm) else CarGoal(data, vm)
    }
}

@Composable
private fun CrossedBills(data: FinanceData, vm: FinanceViewModel) {
    val c = remember(data) { data.core() }
    val r = remember(data) { data.riscadas() }
    var dialog by remember { mutableStateOf<Any?>(null) }
    val done = r.count { it.done }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        item {
            BentoCard {
                LabelText("Balde das contas riscadas (IPCA+)", CyberGreen)
                Text(brl(c.ipca), color = AppTextWhite, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("Meta de cada conta = valor × ${data.settings.multiplier.toInt()} (rende ${"%.1f".format(data.settings.realRate * 100)}% ao ano acima da inflação). $done de ${r.size} riscadas.",
                    color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Note("Só coloque dinheiro aqui depois que a reserva de emergência estiver completa. No Tesouro IPCA+, vender antes do vencimento pode render menos do que você colocou.")
            }
        }
        item {
            HelpAccordion(
                title = "Como funciona a regra dos 200?",
                explanation = "O rendimento real de 0,5% ao mês (6% ao ano acima da inflação) de um valor guardado paga uma conta sem encostar no principal. Para cada R\$ 1 de conta mensal você precisa de R\$ 200 guardados. Quando o balde cobre uma conta, ela é riscada: está paga pelo dinheiro, não pelo seu trabalho.",
                formula = "Meta da conta = valor mensal × 200",
                example = "Luz de R\$ 134 → R\$ 26.800 guardados rendem ~R\$ 134 por mês. Dica: se quiser mais folga, use ×250 em Ajustes."
            )
        }
        item {
            BentoCard {
                SectionHeader("Da menor para a maior")
                if (r.isEmpty()) EmptyHint("Cadastre custos do tipo Casa na aba Gastos.")
                r.forEach { x ->
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
                Text("Previsão com aporte de ${brl0(data.settings.aporteLongo)}/mês a partir de ${mLabel(data.settings.ipcaStart.ifBlank { null })}, em dinheiro de hoje. É estimativa, não garantia.",
                    color = SecondaryText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            BentoCard {
                SectionHeader("Outras metas", "+ meta") { dialog = "new" }
                val others = data.goals.filter { it.category != "CARRO" }
                if (others.isEmpty()) EmptyHint("Nenhuma meta além do carro.")
                others.forEach { g ->
                    StatRow(g.name, "${brl0(g.currentValue)} / ${brl0(g.targetValue)}", sub = "${(if (g.targetValue > 0) g.currentValue / g.targetValue * 100 else 0.0).toInt()}%", onClick = { dialog = g })
                }
            }
        }
    }
    when (val d = dialog) {
        "new" -> GoalDialog(null, { vm.saveGoal(it) }, null) { dialog = null }
        is Goal -> GoalDialog(d, { vm.saveGoal(it) }, { vm.deleteGoal(d) }) { dialog = null }
    }
}

@Composable
private fun CarGoal(data: FinanceData, vm: FinanceViewModel) {
    val goal = data.goals.firstOrNull { it.category == "CARRO" }
    var dialog by remember { mutableStateOf(false) }
    if (goal == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            BentoCard {
                LabelText("Carro à vista", CyberGreen)
                Text("Crie a meta do carro para acompanhar a velocidade de acúmulo e simular o custo real de ter o carro (combustível, manutenção, IPVA, depreciação).",
                    color = SecondaryText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                PrimaryButton("Criar meta do carro", { dialog = true })
            }
        }
    } else {
        CarGoalTab(
            carTarget = goal.targetValue, carCurrent = goal.currentValue, monthlySavings = data.settings.aporteLongo,
            monthlyIncome = data.settings.income, baseExpenses = data.core().essentials,
            onUpdateCarSaved = { vm.saveGoal(goal.copy(currentValue = it)) }
        )
    }
    if (dialog) GoalDialog(null, { vm.saveGoal(it.copy(category = "CARRO")) }, null) { dialog = false }
}
