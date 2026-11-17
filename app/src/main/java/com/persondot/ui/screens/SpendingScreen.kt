@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.persondot.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.persondot.data.model.*
import com.persondot.domain.*
import com.persondot.ui.components.*
import com.persondot.ui.dot.LocalDot
import com.persondot.ui.dot.Mood
import com.persondot.ui.theme.*
import com.persondot.ui.viewmodel.FinanceViewModel
import java.time.LocalDate

private data class PayTarget(val debt: DebtWithInstallments, val inst: DebtInstallment)
private data class PayMonthInst(val line: InstLine)
private data class AddInstTarget(val debtId: Long)
private data class AporteTarget(val month: String, val current: Double)
private data class DeleteDebt(val debt: Debt)
private data class NewFixed(val group: String)

/**
 * Aba Gastos: tudo que sai. Três visões: o Mês (previsões que você confirma), as Parcelas (o que já está
 * comprometido) e o Fluxo (calendário de alívio e mês a mês).
 */
@Composable
fun SpendingScreen(data: FinanceData, vm: FinanceViewModel, modifier: Modifier = Modifier) {
    var seg by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            listOf("Mês", "Parcelas", "Fluxo").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = seg == i, onClick = { seg = i }, shape = SegmentedButtonDefaults.itemShape(i, 3),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = CyberGreen, activeContentColor = Color.Black, inactiveContentColor = AppTextWhite)
                ) { Text(label) }
            }
        }
        when (seg) {
            0 -> MonthSegment(data, vm)
            1 -> InstallmentsSegment(data, vm)
            else -> FlowSegment(data, vm)
        }
    }
}

// =====================================================================================
// Mês: previsões
// =====================================================================================
@Composable
private fun MonthSegment(data: FinanceData, vm: FinanceViewModel) {
    val dot = LocalDot.current
    val current = remember { monthKey(LocalDate.now()) }
    val months = remember { listOf(current) + visibleMonths() }
    var month by rememberSaveable { mutableStateOf(current) }
    var cutOn by rememberSaveable { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<Any?>(null) }

    val plan = remember(data, month) { data.monthPlan(month) }
    val cut = remember(data, month) { data.cutOptional(month) }
    val shownPrevisto = plan.previsto - if (cutOn) cut.monthly else 0.0
    val monthExpenses = remember(data, month) { data.expenses.filter { it.date.startsWith(month) } }
    val byCat = remember(monthExpenses) { monthExpenses.groupBy { it.cat }.mapValues { e -> e.value.sumOf { it.amount } }.toList().sortedByDescending { it.second } }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)) {
        item { MonthChips(months, month) { month = it } }

        // resumo do mês, com a soma explicada
        item {
            BentoCard {
                LabelText("Previsto para ${mLabel(month)}")
                Text(brl(shownPrevisto), color = AppTextWhite, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("Fixos ${brl0(plan.fixedTotal - if (cutOn) cut.monthly else 0.0)} + parcelas ${brl0(plan.parcelas)}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Summary("Pago", brl0(plan.pago), CyberGreen)
                    Summary("Falta pagar", brl0(plan.aPagar - if (cutOn) cut.monthly else 0.0), if (plan.aPagar > 0) levelColor(Level.WARN) else CyberGreen)
                    Summary("Gasto no dia a dia", brl0(plan.spent), CyberTeal)
                }
                Spacer(Modifier.height(8.dp))
                MetricProgress(if (plan.previsto > 0) plan.pago / plan.previsto * 100 else 0.0, CyberGreen)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Sobra para lazer (renda − previsto − aporte − dia a dia)", color = SecondaryText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(brl(plan.restante + if (cutOn) cut.monthly else 0.0), color = if (plan.restante < 0) ImpulseAmber else CyberGreen, fontWeight = FontWeight.Bold)
                }
                if (plan.subsIncluded) Note("A fatura deste mês já inclui as assinaturas do cartão, então elas aparecem como \"na fatura\" e não são somadas de novo.")
                if (month == current) Note("Os valores abaixo são previsões. Quando pagar cada um, toque nele e confirme; se algo mudou, ajuste o valor ou remova.")
            }
        }

        // simulação de corte de opcionais
        item {
            BentoCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Simular corte de opcionais", color = AppTextWhite, fontWeight = FontWeight.SemiBold)
                        Text(if (cutOn) "Veja abaixo exatamente o que sairia." else "Veja o impacto de cortar assinaturas, academia, corte de cabelo…", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = cutOn, onCheckedChange = {
                        cutOn = it
                        if (it && cut.items.isNotEmpty()) dot.react("Cortando tudo isso você libera ${brl0(cut.monthly)} por mês.", Mood.NEUTRAL)
                    }, colors = SwitchDefaults.colors(checkedTrackColor = CyberGreen))
                }
                if (cutOn) {
                    Spacer(Modifier.height(8.dp))
                    if (cut.items.isEmpty()) Text("Não há opcionais em aberto neste mês.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    else {
                        LabelText("O que sairia", ImpulseAmber)
                        cut.items.forEach { f ->
                            StatRow(f.cost.name, "− ${brl(f.amount)}", sub = if (f.cost.onCard) "no cartão · toque para cancelar de verdade" else "toque para cancelar de verdade", valueColor = ImpulseAmber, onClick = { dialog = f })
                        }
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text("Você economizaria", color = AppTextWhite, fontWeight = FontWeight.Bold)
                            Text("${brl(cut.monthly)} por mês · ${brl0(cut.yearly)} por ano", color = CyberGreen, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        }
                        val w = remember(data) { data.whatIf(0.0, true, 0.0) }
                        if (w.reserveSooner > 0) Note("Esse valor, indo para o aporte, adianta a reserva em ${w.reserveSooner} ${if (w.reserveSooner == 1) "mês" else "meses"}.")
                    }
                }
            }
        }

        // previsões por tipo
        listOf(
            Triple(FixedCost.GRP_CASA, "Casa · essenciais", "Luz, mercado, prédio, internet"),
            Triple(FixedCost.GRP_TRABALHO, "Trabalho", "DAS, ferramentas, assinaturas de trabalho"),
            Triple(FixedCost.GRP_OPCIONAL, "Opcionais", "Pode cortar sem problema")
        ).forEach { (grp, title, hint) ->
            item(key = grp) {
                val items = plan.forecasts.filter { it.cost.grp == grp }.sortedWith(compareBy({ it.status == ForecastStatus.REMOVIDO }, { -it.amount }))
                val counted = items.filter { it.counts }
                BentoCard {
                    SectionHeader(data.settings.groupLabel(grp), "+ custo") { dialog = NewFixed(grp) }
                    if (items.isEmpty()) EmptyHint(hint)
                    items.forEach { f -> ForecastRow(f, cutOn && grp == FixedCost.GRP_OPCIONAL && f.status == ForecastStatus.PREVISTO) { dialog = f } }
                    if (counted.isNotEmpty()) {
                        val paid = counted.count { it.status == ForecastStatus.PAGO }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total previsto · $paid de ${counted.size} pagos", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                            Text(brl(counted.sumOf { it.amount }), color = AppTextWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (plan.inst.isNotEmpty()) item {
            BentoCard {
                SectionHeader("Parcelas e faturas do mês")
                plan.inst.forEach { l ->
                    StatRow(
                        l.debtName, brl(l.inst.amount),
                        sub = listOfNotNull(if (l.inst.paid) "pago" else "previsto", if (l.inst.includesSubs) "inclui assinaturas" else null).joinToString(" · "),
                        valueColor = if (l.inst.paid) CyberGreen else AppTextWhite,
                        leading = { StatusDot(l.inst.paid, false) },
                        onClick = { if (!l.inst.paid) dialog = PayMonthInst(l) else dialog = l.inst }
                    )
                }
            }
        }

        // gastos do dia a dia (já aconteceram)
        item {
            BentoCard {
                SectionHeader("Gastos do dia a dia", "+ gasto") { dialog = "newExpense" }
                if (byCat.isEmpty()) EmptyHint("Nada lançado neste mês. Lance o que sair do dia a dia (lazer, transporte, extras). Compras maiores, simule em Decidir.")
                byCat.forEach { (cat, v) -> StatRow(cat, brl(v), valueColor = CyberTeal) }
                if (monthExpenses.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LabelText("Lançamentos")
                    monthExpenses.forEach { e -> StatRow(e.desc, brl(e.amount), sub = "${e.date.takeLast(2)}/${e.date.substring(5, 7)} · ${e.cat}", onClick = { dialog = e }) }
                }
            }
        }
    }

    when (val d = dialog) {
        "newExpense" -> ExpenseDialog(null, data.accounts, data.settings.categoryList, { e, acc -> vm.saveExpense(e, acc) }, null) { dialog = null }
        is Expense -> ExpenseDialog(d, data.accounts, data.settings.categoryList, { e, acc -> vm.saveExpense(e, acc) }, { vm.deleteExpense(d) }) { dialog = null }
        is Forecast -> ForecastActions(d, data, vm, dot) { dialog = null }
        is NewFixed -> FixedDialog(null, d.group, month, data.settings, { vm.saveFixed(it); dot.react("Novo custo previsto adicionado.", Mood.NEUTRAL) }, null) { dialog = null }
        is PayMonthInst -> PayNextDialog("${d.line.debtName} · ${mLabel(d.line.inst.month)}", d.line.inst.amount, data.accounts, { acc -> vm.payInstallment(d.line.inst, acc); dot.react("Parcela paga. Falta menos!", Mood.HAPPY) }) { dialog = null }
        is DebtInstallment -> InstallmentDialog(d, { vm.saveInstallment(it) }, { vm.deleteInstallment(d) }) { dialog = null }
    }
}

@Composable
private fun Summary(label: String, value: String, color: Color) {
    Column {
        Text(label, color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        Text(value, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun StatusDot(paid: Boolean, removed: Boolean) {
    when {
        paid -> Text("✓", color = CyberGreen, fontWeight = FontWeight.ExtraBold)
        removed -> Text("–", color = SecondaryText)
        else -> Box(Modifier.size(10.dp).padding(1.dp)) { Dot(Level.WARN) }
    }
}

@Composable
private fun ForecastRow(f: Forecast, cutPreview: Boolean, onClick: () -> Unit) {
    val removed = f.status == ForecastStatus.REMOVIDO
    val subs = listOfNotNull(
        when { removed -> "removido deste mês"; f.status == ForecastStatus.PAGO -> "pago"; f.inInvoice -> "na fatura do cartão"; else -> "previsto" },
        if (f.cost.onCard && !f.inInvoice) "no cartão" else null,
        if (f.adjusted) "padrão ${brl(f.cost.amount)}" else null
    ).joinToString(" · ")
    val color = when { removed || f.inInvoice -> SecondaryText; f.status == ForecastStatus.PAGO -> CyberGreen; cutPreview -> ImpulseAmber; else -> AppTextWhite }
    val strike = if (removed || cutPreview) TextDecoration.LineThrough else null
    Row(Modifier.fillMaxWidth().then(Modifier), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            StatRowStruck(f.cost.name, brl(f.amount), subs, color, strike, leading = { StatusDot(f.status == ForecastStatus.PAGO, removed) }, onClick = onClick)
        }
    }
}

@Composable
private fun StatRowStruck(label: String, value: String, sub: String, color: Color, deco: TextDecoration?, leading: @Composable () -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickableRow(onClick).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) { leading() }
            Spacer(Modifier.width(6.dp))
            Column {
                Text(label, color = color, style = MaterialTheme.typography.bodyMedium, textDecoration = deco)
                Text(sub, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(value, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, textDecoration = deco)
    }
    HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
}

private fun Modifier.clickableRow(onClick: () -> Unit) = this.then(Modifier.clickable(onClick = onClick))

// =====================================================================================
// Parcelas: o que já está comprometido
// =====================================================================================
@Composable
private fun InstallmentsSegment(data: FinanceData, vm: FinanceViewModel) {
    val dot = LocalDot.current
    var dialog by remember { mutableStateOf<Any?>(null) }
    val c = remember(data) { data.core() }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)) {
        item {
            BentoCard {
                SectionHeader("Total comprometido", "+ dívida") { dialog = "newDebt" }
                Text(brl(c.owed), color = AppTextWhite, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("Parcelas sem juros não precisam ser quitadas antes. O importante é não gastar o dinheiro que já tem dono. Toque em uma parcela para editar ou pagar.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (data.debts.isEmpty()) item { BentoCard { EmptyHint("Nenhuma dívida cadastrada.") } }
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
    }

    when (val d = dialog) {
        "newDebt" -> NewDebtDialog({ n, note, a, cnt, st -> vm.createDebt(n, note, a, cnt, st) }) { dialog = null }
        is DebtInstallment -> InstallmentDialog(d, { vm.saveInstallment(it) }, { vm.deleteInstallment(d) }) { dialog = null }
        is AddInstTarget -> AddInstallmentDialog({ m, a -> vm.addInstallment(d.debtId, m, a) }) { dialog = null }
        is PayTarget -> PayNextDialog("${d.debt.debt.name} · ${mLabel(d.inst.month)}", d.inst.amount, data.accounts, { acc -> vm.payNext(d.debt.debt.id, acc); dot.react("Parcela paga. Falta menos!", Mood.HAPPY) }) { dialog = null }
        is DeleteDebt -> AlertDialog(
            onDismissRequest = { dialog = null }, containerColor = DarkSurface,
            title = { Text("Excluir \"${d.debt.name}\"?", color = AppTextWhite) },
            text = { Text("Todas as parcelas dessa dívida serão apagadas. Isso não devolve saldo às contas.", color = SecondaryText) },
            confirmButton = { TextButton(onClick = { vm.deleteDebt(d.debt.id); dialog = null }) { Text("Excluir", color = ImpulseAmber) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar", color = SecondaryText) } }
        )
    }
}

// =====================================================================================
// Fluxo: calendário de alívio e mês a mês
// =====================================================================================
@Composable
private fun FlowSegment(data: FinanceData, vm: FinanceViewModel) {
    val months = remember { visibleMonths() }
    var month by rememberSaveable { mutableStateOf(months.first()) }
    var dialog by remember { mutableStateOf<Any?>(null) }
    val reliefs = remember(data) { months.map { it to data.monthPlan(it).parcelas } }
    val maxRelief = reliefs.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    val plan = remember(data, month) { data.monthPlan(month) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)) {
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
        item { SectionHeader("Fluxo mês a mês") }
        item { MonthChips(months, month) { month = it } }
        item {
            BentoCard {
                LabelText("Fluxo de ${mLabel(month)}")
                StatRow("Renda (dia ${data.settings.incomeDay})", "+ ${brl(plan.income)}", valueColor = CyberGreen)
                StatRow("Casa (previsto)", "− ${brl(plan.casa)}")
                StatRow("Trabalho (previsto)", "− ${brl(plan.trabalho)}")
                StatRow("Opcionais (previsto)", "− ${brl(plan.opcional)}")
                plan.inst.forEach { StatRow(it.debtName, "− ${brl(it.inst.amount)}", sub = if (it.inst.includesSubs) "inclui assinaturas" else null) }
                StatRow("Aporte planejado (toque para mudar)", "− ${brl(plan.aporte)}", onClick = { dialog = AporteTarget(month, plan.aporte) })
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Sobra para lazer e variáveis", color = AppTextWhite, fontWeight = FontWeight.Bold)
                    Text(brl(plan.sobra), color = if (plan.sobra < 0) ImpulseAmber else CyberGreen, fontWeight = FontWeight.ExtraBold)
                }
                if (plan.sobra < 0) Note("Mês no vermelho: reduza o aporte desse mês ou cubra com o que vai receber.")
            }
        }
    }
    when (val d = dialog) {
        is AporteTarget -> AporteDialog(d.month, d.current, { vm.saveAporte(d.month, it) }, onReset = { vm.clearAporte(d.month) }) { dialog = null }
    }
}
