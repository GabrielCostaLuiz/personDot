@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.persondot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.persondot.data.model.*
import com.persondot.domain.*
import com.persondot.ui.components.*
import com.persondot.ui.theme.*
import com.persondot.ui.viewmodel.FinanceViewModel

/** Aba Futuro: tudo que já está comprometido (dívidas e parcelas), calendário de alívio e fluxo mês a mês. */
@Composable
fun FutureScreen(data: FinanceData, vm: FinanceViewModel, modifier: Modifier = Modifier) {
    val months = remember { visibleMonths() }
    var month by rememberSaveable { mutableStateOf(months.first()) }
    var dialog by remember { mutableStateOf<Any?>(null) }
    val c = remember(data) { data.core() }
    val reliefs = remember(data) { months.map { it to data.monthPlan(it).parcelas } }
    val maxRelief = reliefs.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    val plan = remember(data, month) { data.monthPlan(month) }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)) {
        item {
            BentoCard {
                SectionHeader("Total comprometido", "+ dívida") { dialog = "newDebt" }
                Text(brl(c.owed), color = AppTextWhite, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("Parcelas sem juros não precisam ser quitadas antes. O importante é não gastar o dinheiro que já tem dono.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            BentoCard {
                SectionHeader("Calendário de alívio")
                Text("O quanto de parcelas pesa em cada mês. Quando a barra some, o orçamento folga.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                reliefs.forEach { (m, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(mLabel(m), color = SecondaryText, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(48.dp))
                        Box(Modifier.weight(1f)) { MetricProgress(v / maxRelief * 100, if (v == 0.0) CyberGreen else CyberTeal) }
                        Text(if (v == 0.0) "livre" else brl0(v), color = if (v == 0.0) CyberGreen else AppTextWhite, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(72.dp).padding(start = 8.dp), maxLines = 1)
                    }
                }
            }
        }

        items(data.debts.size) { idx ->
            val d = data.debts[idx]
            val sorted = d.installments.sortedBy { it.month ?: "9999" }
            val next = sorted.firstOrNull { !it.paid }
            BentoCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(d.debt.name, color = AppTextWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(brl(d.remaining), color = if (d.remaining == 0.0) CyberGreen else AppTextWhite, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                MetricProgress(if (d.total > 0) (d.total - d.remaining) / d.total * 100 else 0.0, CyberGreen)
                if (d.debt.note.isNotBlank()) Text(d.debt.note, color = SecondaryText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sorted.forEach { i ->
                        AssistChip(
                            onClick = { dialog = i },
                            label = { Text("${mLabel(i.month)} · ${brl(i.amount)}", textDecoration = if (i.paid) TextDecoration.LineThrough else null) },
                            colors = AssistChipDefaults.assistChipColors(labelColor = if (i.paid) SecondaryText else AppTextWhite)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (next != null) PrimaryButton("Pagar próxima", { dialog = PayTarget(d, next) })
                    SoftButton("+ parcela", { dialog = AddInstTarget(d.debt.id) })
                    SoftButton("Excluir", { dialog = DeleteDebt(d.debt) }, danger = true)
                }
            }
        }

        item { SectionHeader("Fluxo mês a mês") }
        item { MonthChips(months, month) { month = it } }
        item {
            BentoCard {
                LabelText("Fluxo de ${mLabel(month)}")
                StatRow("Renda (dia ${data.settings.incomeDay})", "+ ${brl(plan.income)}", valueColor = CyberGreen)
                StatRow("Casa (sua parte)", "− ${brl(plan.casa)}")
                StatRow("Trabalho", "− ${brl(plan.trabalho)}")
                StatRow("Opcionais", "− ${brl(plan.opcional)}")
                plan.inst.forEach { StatRow(it.debtName, "− ${brl(it.inst.amount)}", sub = if (it.inst.includesSubs) "inclui assinaturas" else null) }
                plan.planned.forEach { StatRow(it.desc, "− ${brl(it.amount)}") }
                StatRow("Aporte planejado (toque para mudar)", "− ${brl(plan.aporte)}", onClick = { dialog = AporteTarget(month, plan.aporte) })
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Sobra para lazer e variáveis", color = AppTextWhite, fontWeight = FontWeight.Bold)
                    Text(brl(plan.sobra), color = if (plan.sobra < 0) ImpulseAmber else CyberGreen, fontWeight = FontWeight.ExtraBold)
                }
                if (plan.sobra < 0) Note("Mês no vermelho: reduza o aporte ou cubra com o dinheiro a receber.")
            }
        }
    }

    when (val d = dialog) {
        "newDebt" -> NewDebtDialog({ n, note, a, cnt, st -> vm.createDebt(n, note, a, cnt, st) }) { dialog = null }
        is DebtInstallment -> InstallmentDialog(d, { vm.saveInstallment(it) }, { vm.deleteInstallment(d) }) { dialog = null }
        is AddInstTarget -> AddInstallmentDialog({ m, a -> vm.addInstallment(d.debtId, m, a) }) { dialog = null }
        is PayTarget -> PayNextDialog("${d.debt.debt.name} · ${mLabel(d.inst.month)}", d.inst.amount, data.accounts, { acc -> vm.payNext(d.debt.debt.id, acc) }) { dialog = null }
        is AporteTarget -> AporteDialog(d.month, d.current, { vm.saveAporte(d.month, it) }) { dialog = null }
        is DeleteDebt -> AlertDialog(
            onDismissRequest = { dialog = null }, containerColor = DarkSurface,
            title = { Text("Excluir \"${d.debt.name}\"?", color = AppTextWhite) },
            text = { Text("Todas as parcelas dessa dívida serão apagadas. Isso não devolve saldo às contas.", color = SecondaryText) },
            confirmButton = { TextButton(onClick = { vm.deleteDebt(d.debt.id); dialog = null }) { Text("Excluir", color = ImpulseAmber) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar", color = SecondaryText) } }
        )
    }
}

private data class PayTarget(val debt: DebtWithInstallments, val inst: DebtInstallment)
private data class AddInstTarget(val debtId: Long)
private data class AporteTarget(val month: String, val current: Double)
private data class DeleteDebt(val debt: Debt)
