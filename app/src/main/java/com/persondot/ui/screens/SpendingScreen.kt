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
import com.persondot.data.model.*
import com.persondot.domain.*
import com.persondot.ui.components.*
import com.persondot.ui.theme.*
import com.persondot.ui.viewmodel.FinanceViewModel
import java.time.LocalDate

/** Aba Gastos: tudo que sai por mês, separado em fixos (casa, trabalho, opcionais), parcelas, planejados e variáveis. */
@Composable
fun SpendingScreen(data: FinanceData, vm: FinanceViewModel, modifier: Modifier = Modifier) {
    val current = remember { monthKey(LocalDate.now()) }
    val months = remember { listOf(current) + visibleMonths() }
    var month by rememberSaveable { mutableStateOf(current) }
    var cutLuxury by rememberSaveable { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<Any?>(null) }

    val plan = remember(data, month) { data.monthPlan(month) }
    val opcional = if (cutLuxury) 0.0 else plan.opcional
    val total = plan.casa + plan.trabalho + opcional + plan.parcelas + plan.plannedTotal + plan.spent
    val monthExpenses = remember(data, month) { data.expenses.filter { it.date.startsWith(month) } }
    val byCat = remember(monthExpenses) { monthExpenses.groupBy { it.cat }.mapValues { e -> e.value.sumOf { it.amount } }.toList().sortedByDescending { it.second } }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)) {
        item { MonthChips(months, month) { month = it } }
        item {
            BentoCard {
                LabelText("Todos os gastos de ${mLabel(month)}")
                Text(brl(total), color = AppTextWhite, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("Casa ${brl0(plan.casa)} · trabalho ${brl0(plan.trabalho)} · opcionais ${brl0(opcional)}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Text("Parcelas ${brl0(plan.parcelas)} · planejados ${brl0(plan.plannedTotal)} · variáveis ${brl0(plan.spent)}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                if (plan.subsIncluded) Note("A fatura deste mês já inclui as assinaturas do cartão, então elas não foram somadas de novo.")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Simular corte de opcionais", color = AppTextWhite, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (cutLuxury) "Você economizaria ${brl0(plan.opcional)}/mês, ${brl0(plan.opcional * 12)} por ano." else "Veja o impacto de cortar assinaturas, academia, corte…",
                            color = if (cutLuxury) CyberGreen else SecondaryText, style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = cutLuxury, onCheckedChange = { cutLuxury = it }, colors = SwitchDefaults.colors(checkedTrackColor = CyberGreen))
                }
            }
        }

        listOf(
            Triple(FixedCost.GRP_CASA, "Casa · essenciais", "Luz, mercado, prédio, internet"),
            Triple(FixedCost.GRP_TRABALHO, "Trabalho", "DAS, ferramentas, assinaturas de trabalho"),
            Triple(FixedCost.GRP_OPCIONAL, "Opcionais", "Pode cortar sem problema")
        ).forEach { (grp, title, hint) ->
            item(key = grp) {
                val items = data.fixed.filter { it.grp == grp && !(plan.subsIncluded && it.onCard) }.sortedByDescending { it.amount }
                BentoCard {
                    SectionHeader(title, "+ custo") { dialog = "new:$grp" }
                    if (items.isEmpty()) EmptyHint(hint)
                    items.forEach { f ->
                        StatRow(f.name, brl(f.amount), sub = if (f.onCard) "no cartão" else null,
                            valueColor = if (cutLuxury && grp == FixedCost.GRP_OPCIONAL) SecondaryText else AppTextWhite, onClick = { dialog = f })
                    }
                    if (items.isNotEmpty()) StatRow("Total", brl(items.sumOf { it.amount }), valueColor = CyberGreen)
                }
            }
        }

        if (plan.inst.isNotEmpty()) item {
            BentoCard {
                SectionHeader("Parcelas e faturas")
                plan.inst.forEach { StatRow(it.debtName, brl(it.inst.amount), sub = if (it.inst.includesSubs) "inclui assinaturas" else null) }
            }
        }
        if (plan.planned.isNotEmpty()) item {
            BentoCard {
                SectionHeader("Planejados do mês")
                plan.planned.forEach { StatRow(it.desc, brl(it.amount), sub = it.note.ifBlank { null }) }
            }
        }

        item {
            BentoCard {
                SectionHeader("Gastos variáveis", "+ gasto") { dialog = "newExpense" }
                if (byCat.isEmpty()) EmptyHint("Nenhum lançamento neste mês. Lance o que sair do dia a dia (lazer, transporte, extras).")
                byCat.forEach { (cat, v) -> StatRow(cat, brl(v), valueColor = CyberTeal) }
                if (monthExpenses.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    LabelText("Lançamentos")
                    monthExpenses.forEach { e -> StatRow(e.desc, brl(e.amount), sub = "${e.date.takeLast(2)}/${e.date.substring(5, 7)} · ${e.cat}", onClick = { dialog = e }) }
                }
            }
        }
    }

    when (val d = dialog) {
        "newExpense" -> ExpenseDialog(null, data.accounts, { e, acc -> vm.saveExpense(e, acc) }, null) { dialog = null }
        is Expense -> ExpenseDialog(d, data.accounts, { e, acc -> vm.saveExpense(e, acc) }, { vm.deleteExpense(d) }) { dialog = null }
        is FixedCost -> FixedDialog(d, d.grp, { vm.saveFixed(it) }, { vm.deleteFixed(d) }) { dialog = null }
        is String -> if (d.startsWith("new:")) FixedDialog(null, d.removePrefix("new:"), { vm.saveFixed(it) }, null) { dialog = null }
    }
}
