package com.persondot.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.persondot.data.model.*
import com.persondot.domain.*
import com.persondot.ui.components.*
import com.persondot.ui.dot.*
import com.persondot.ui.theme.*
import com.persondot.ui.viewmodel.FinanceViewModel
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

private fun greeting(): String {
    val h = LocalTime.now().hour
    return if (h < 12) "Bom dia" else if (h < 18) "Boa tarde" else "Boa noite"
}

/**
 * Painel: a visão geral da sua situação e o "norte" do que fazer a seguir.
 * As outras telas são os detalhes; esta responde "como estou e o que faço agora?".
 */
@Composable
fun DashboardScreen(
    data: FinanceData, vm: FinanceViewModel, dotId: String, dotName: String,
    onNavigate: (Dest) -> Unit, modifier: Modifier = Modifier
) {
    val dot = LocalDot.current
    val design = dotById(dotId)
    val today = remember { LocalDate.now() }
    val now = remember { monthKey(today) }
    val c = remember(data) { data.core() }
    val health = remember(data) { data.health() }
    val steps = remember(data) { data.norte() }
    val riscadas = remember(data) { data.riscadas() }
    val plan = remember(data) { data.monthPlan(now) }
    val due = remember(data) { data.dueIn(now) }
    var dialog by remember { mutableStateOf<Any?>(null) }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
        // ---- o Dot e o norte principal ----
        item {
            val first = steps.first()
            BentoCard(onClick = { onNavigate(if (data.isEmpty) Dest.DOT else first.dest) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DotAvatar(design, size = 104.dp, mood = dot.mood ?: moodFor(data), bounceKey = dot.bounceKey, onClick = { onNavigate(Dest.DOT) })
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        LabelText("${greeting()}, eu sou o $dotName", CyberGreen)
                        Spacer(Modifier.height(4.dp))
                        if (data.isEmpty) {
                            Text("Vamos começar?", color = AppTextWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Importe seus dados em Ajustes ou cadastre contas e custos. Eu cuido do resto.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("Seu norte agora", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
                            Text(first.title, color = AppTextWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(first.detail, color = SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                        }
                    }
                }
            }
        }

        if (!data.isEmpty) {
            // ---- livre de verdade + anéis ----
            item {
                val free by animateFloatAsState(c.free.toFloat(), tween(900), label = "free")
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(DarkSurface, DarkBackground)))
                        .border(1.dp, CardBorderGlance, RoundedCornerShape(24.dp)).padding(18.dp)
                ) {
                    LabelText("Livre de verdade")
                    Text(brl(free.toDouble()), color = if (c.free < 0) ImpulseAmber else if (c.reserveTarget > 0 && c.free >= c.reserveTarget) CyberGreen else AppTextWhite,
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 4.dp))
                    Text("Em conta ${brl0(c.inHand)} + a receber ${brl0(c.toReceive)} − parcelas ${brl0(c.owed)}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    Text("Hoje, sem contar o que ainda vai entrar: ${brl(c.freeNow)}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ScoreRing(health.score / 100f, "${health.score}", "Saúde", if (health.score >= 75) CyberGreen else if (health.score >= 50) levelColor(Level.WARN) else ImpulseAmber)
                        val rp = if (c.reserveTarget > 0) (c.free / c.reserveTarget).toFloat() else 0f
                        ScoreRing(rp.coerceAtLeast(0f), "${(rp * 100).roundToInt().coerceAtLeast(0)}%", "Reserva", if (rp >= 1f) CyberGreen else CyberTeal)
                        val done = riscadas.count { it.done }
                        ScoreRing(if (riscadas.isEmpty()) 0f else done / riscadas.size.toFloat(), "$done/${riscadas.size}", "Contas riscadas", PulseViolet)
                    }
                }
            }

            // ---- seu norte ----
            item {
                BentoCard {
                    SectionHeader("Seu norte: o que fazer agora")
                    steps.forEachIndexed { i, s ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onNavigate(s.dest) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(Modifier.size(26.dp).clip(CircleShape).background(if (i == 0) CyberGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", color = if (i == 0) androidx.compose.ui.graphics.Color.Black else AppTextWhite, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.title, color = AppTextWhite, fontWeight = FontWeight.SemiBold)
                                Text(s.detail, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (i < steps.lastIndex) HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                    }
                }
            }

            // ---- este mês ----
            item {
                BentoCard {
                    SectionHeader("Este mês · ${mLabel(now)}", "ver tudo") { onNavigate(Dest.GASTOS) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MonthStat("Previsto", brl0(plan.previsto))
                        MonthStat("Pago", brl0(plan.pago), CyberGreen)
                        MonthStat("Falta pagar", brl0(plan.aPagar), if (plan.aPagar > 0) levelColor(Level.WARN) else CyberGreen)
                    }
                    Spacer(Modifier.height(8.dp))
                    MetricProgress(if (plan.previsto > 0) plan.pago / plan.previsto * 100 else 0.0, CyberGreen)
                    Spacer(Modifier.height(6.dp))
                    if (due.isEmpty()) Text("Tudo confirmado neste mês.", color = CyberGreen, style = MaterialTheme.typography.bodySmall)
                    else {
                        Text("Falta confirmar:", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
                        due.take(3).forEach { d ->
                            StatRow(d.label, brl(d.amount), sub = if (d.inst != null) "parcela/fatura" else if (d.forecast?.cost?.onCard == true) "no cartão" else null,
                                onClick = { dialog = d.forecast ?: d.inst })
                        }
                        if (due.size > 3) TextButton(onClick = { onNavigate(Dest.GASTOS) }) { Text("+ ${due.size - 3} itens", color = CyberGreen) }
                    }
                }
            }

            // ---- detalhes da saúde ----
            item {
                BentoCard {
                    SectionHeader("Como está a sua saúde financeira")
                    health.items.forEach { StatRow(it.name, it.value, leading = { Dot(it.status) }) }
                }
            }
        }

        // ---- dinheiro ----
        item {
            BentoCard {
                SectionHeader("Contas", "+ conta") { dialog = "newAccount" }
                if (data.accounts.isEmpty()) EmptyHint("Nenhuma conta. Cadastre onde seu dinheiro está.")
                data.accounts.forEach { a ->
                    StatRow(a.name, brl(a.balance), sub = listOf(if (a.kind == Account.KIND_IPCA) "balde IPCA+" else null, a.note.ifBlank { null }).filterNotNull().joinToString(" · ").ifBlank { null },
                        onClick = { dialog = a })
                }
            }
        }
        item {
            BentoCard {
                SectionHeader("A receber", "+ entrada") { dialog = "newReceivable" }
                if (data.receivables.isEmpty()) EmptyHint("Nada a receber.")
                data.receivables.forEach { r ->
                    StatRow(r.desc, brl(r.amount), sub = if (r.received) "recebido" else null, valueColor = if (r.received) SecondaryText else CyberGreen, onClick = { dialog = r })
                }
            }
        }
    }

    when (val d = dialog) {
        "newAccount" -> AccountDialog(null, { vm.saveAccount(it) }, null) { dialog = null }
        is Account -> AccountDialog(d, { vm.saveAccount(it) }, { vm.deleteAccount(d) }) { dialog = null }
        "newReceivable" -> ReceivableDialog(null, data.accounts, { vm.saveReceivable(it) }, { _, _, _ -> }, null) { dialog = null }
        is Receivable -> ReceivableDialog(d, data.accounts, { vm.saveReceivable(it) }, { r, v, acc -> vm.receive(r, v, acc); dot.react("Entrada recebida! Mais um passo.", Mood.HAPPY) }, { vm.deleteReceivable(d) }) { dialog = null }
        is Forecast -> ForecastActions(d, data, vm, dot) { dialog = null }
        is InstLine -> PayNextDialog("${d.debtName} · ${mLabel(d.inst.month)}", d.inst.amount, data.accounts, { acc -> vm.payInstallment(d.inst, acc); dot.react("Parcela paga. Falta menos!", Mood.HAPPY) }) { dialog = null }
    }
}

@Composable
private fun MonthStat(label: String, value: String, color: androidx.compose.ui.graphics.Color = AppTextWhite) {
    Column {
        Text(label, color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        Text(value, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

/** Todas as ações possíveis sobre um custo previsto, com o Dot reagindo a cada uma. */
@Composable
fun ForecastActions(f: Forecast, data: FinanceData, vm: FinanceViewModel, dot: DotController, onDismiss: () -> Unit) {
    var editCost by remember { mutableStateOf(false) }
    ForecastDialog(
        f, data.accounts,
        onConfirm = { amt, acc -> vm.confirmForecast(f, amt, acc); dot.react("${f.cost.name} confirmado. Menos uma!", Mood.HAPPY) },
        onAdjust = { amt -> vm.adjustForecast(f, amt); dot.react("Ajustei o valor de ${mLabel(f.month)}.", Mood.NEUTRAL) },
        onRemove = { vm.removeForecast(f); dot.react("Tirei ${f.cost.name} só de ${mLabel(f.month)}.", Mood.NEUTRAL) },
        onRestore = { vm.restoreForecast(f); dot.react("${f.cost.name} voltou para ${mLabel(f.month)}.", Mood.NEUTRAL) },
        onUndo = { vm.undoForecast(f); dot.react("Desfiz a confirmação.", Mood.NEUTRAL) },
        onCancelFrom = { vm.cancelFrom(f); dot.react("${f.cost.name} cancelado daqui pra frente. Economia de ${brl0(f.cost.amount * 12)} por ano!", Mood.HAPPY) },
        onEditCost = { editCost = true },
        onDismiss = { if (!editCost) onDismiss() }
    )
    if (editCost) FixedDialog(f.cost, f.cost.grp, f.month, { vm.saveFixed(it) }, { vm.deleteFixed(f.cost) }) { editCost = false; onDismiss() }
}
