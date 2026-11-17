package com.persondot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.persondot.data.model.*
import com.persondot.domain.*
import com.persondot.ui.components.*
import com.persondot.ui.theme.*
import com.persondot.ui.viewmodel.FinanceViewModel
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(data: FinanceData, vm: FinanceViewModel, onGoSettings: () -> Unit, onGoFuture: () -> Unit, modifier: Modifier = Modifier) {
    val c = remember(data) { data.core() }
    val health = remember(data) { data.health() }
    val nextMonth = remember { addMonths(monthKey(LocalDate.now()), 1) }
    val plan = remember(data) { data.monthPlan(nextMonth) }
    var dialog by remember { mutableStateOf<Any?>(null) }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)) {
        if (data.isEmpty) item { EmptyStateCard(onGoSettings) }
        else {
            item { WelcomeCard(data, c) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FreeHero(c)
                    HelpAccordion(
                        title = "Entenda o \"livre de verdade\"",
                        explanation = "É o dinheiro que realmente é seu hoje. Quase tudo que está guardado já tem dono: parcelas que vão vencer e gastos que você já combinou. O que sobra depois disso é o seu livre de verdade — a base da reserva de emergência.",
                        formula = "Livre = em conta + a receber − dívidas em aberto − gastos planejados",
                        example = "R\$ 13.292 em conta + R\$ 5.861 a receber − R\$ 11.039 de parcelas − R\$ 1.900 planejados = R\$ 6.214."
                    )
                }
            }
            item { HealthCard(health, data.nextStep()) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReserveCard(c)
                    HelpAccordion(
                        title = "Por que 6 meses de essenciais?",
                        explanation = "Como PJ você não tem 13º, férias nem seguro-desemprego. A reserva cobre casa + trabalho (DAS, ferramentas) caso a renda pare. Ela fica em liquidez diária e não oscila: é o único dinheiro que você pode precisar de um dia para o outro.",
                        formula = "Meta = (custos da casa + custos de trabalho) × meses de reserva"
                    )
                }
            }
            item { NextMonthCard(plan, onGoFuture) }
        }

        item {
            SectionedCard("Contas", "+ conta", { dialog = "newAccount" }) {
                if (data.accounts.isEmpty()) EmptyHint("Nenhuma conta. Cadastre onde seu dinheiro está.")
                data.accounts.forEach { a ->
                    StatRow(a.name, brl(a.balance), sub = listOf(if (a.kind == Account.KIND_IPCA) "balde IPCA+" else null, a.note.ifBlank { null }).filterNotNull().joinToString(" · ").ifBlank { null },
                        onClick = { dialog = a })
                }
            }
        }
        item {
            SectionedCard("A receber", "+ entrada", { dialog = "newReceivable" }) {
                if (data.receivables.isEmpty()) EmptyHint("Nada a receber.")
                data.receivables.forEach { r ->
                    StatRow(r.desc, brl(r.amount), sub = if (r.received) "recebido" else null, valueColor = if (r.received) SecondaryText else CyberGreen, onClick = { dialog = r })
                }
            }
        }
        item {
            SectionedCard("Gastos planejados", "+ gasto", { dialog = "newPlanned" }) {
                if (data.planned.isEmpty()) EmptyHint("Nenhum gasto planejado.")
                data.planned.forEach { p ->
                    StatRow(p.desc, brl(p.amount), sub = listOfNotNull(mLabel(p.month), if (p.paid) "pago" else null, p.note.ifBlank { null }).joinToString(" · "),
                        valueColor = if (p.paid) SecondaryText else ImpulseAmber, onClick = { dialog = p })
                }
            }
        }
    }

    when (val d = dialog) {
        "newAccount" -> AccountDialog(null, { vm.saveAccount(it) }, null) { dialog = null }
        is Account -> AccountDialog(d, { vm.saveAccount(it) }, { vm.deleteAccount(d) }) { dialog = null }
        "newReceivable" -> ReceivableDialog(null, data.accounts, { vm.saveReceivable(it) }, { _, _, _ -> }, null) { dialog = null }
        is Receivable -> ReceivableDialog(d, data.accounts, { vm.saveReceivable(it) }, { r, v, acc -> vm.receive(r, v, acc) }, { vm.deleteReceivable(d) }) { dialog = null }
        "newPlanned" -> PlannedDialog(null, data.accounts, { vm.savePlanned(it) }, { _, _ -> }, null) { dialog = null }
        is Planned -> PlannedDialog(d, data.accounts, { vm.savePlanned(it) }, { p, acc -> vm.payPlanned(p, acc) }, { vm.deletePlanned(d) }) { dialog = null }
    }
}

@Composable
private fun SectionedCard(title: String, action: String, onAction: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BentoCard { SectionHeader(title, action, onAction); content() }
}

@Composable
private fun EmptyStateCard(onGoSettings: () -> Unit) {
    BentoCard {
        LabelText("Bem-vindo ao PersonDot", CyberGreen)
        Spacer(Modifier.height(6.dp))
        Text("Seus dados ficam só neste aparelho.", color = AppTextWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Importe o seu arquivo de dados em Ajustes, ou comece do zero cadastrando contas, custos fixos e dívidas pelas abas.", color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Abrir Ajustes", onGoSettings)
    }
}

@Composable
private fun WelcomeCard(data: FinanceData, c: Core) {
    val s = data.settings
    val pct = if (s.income > 0) (c.essentials / s.income * 100).roundToInt() else 0
    BentoCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                LabelText("Renda · cai dia ${s.incomeDay}", CyberTeal)
                Text("${brl(s.income)}/mês", color = AppTextWhite, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Essenciais (casa + trabalho) usam $pct% da renda", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
            IconBadge(Icons.Default.Terminal, CyberTeal)
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(45.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun FreeHero(c: Core) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(DarkSurface, DarkBackground)))
            .border(1.dp, CardBorderGlance, RoundedCornerShape(24.dp)).padding(18.dp)
    ) {
        LabelText("Livre de verdade")
        Text(brl(c.free), color = if (c.free < 0) ImpulseAmber else if (c.free >= c.reserveTarget && c.reserveTarget > 0) CyberGreen else AppTextWhite,
            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 4.dp))
        Text("Hoje, sem contar o que ainda vai entrar: ${brl(c.freeNow)}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStat("Em conta", brl0(c.inHand), Icons.Default.AccountBalanceWallet, CyberTeal, Modifier.weight(1f))
            MiniStat("Já comprometido", brl0(c.owed + c.planned), Icons.Default.Lock, ImpulseAmber, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, icon: ImageVector, tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)).border(0.5.dp, BorderColor, RoundedCornerShape(16.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
            Text(label, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, color = AppTextWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HealthCard(h: Health, next: String) {
    val scoreColor = if (h.score >= 75) CyberGreen else if (h.score >= 50) levelColor(Level.WARN) else ImpulseAmber
    BentoCard {
        LabelText("Saúde financeira")
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${h.score}", color = scoreColor, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
            Text(" / 100", color = SecondaryText, modifier = Modifier.padding(bottom = 6.dp))
        }
        h.items.forEach { StatRow(it.name, it.value, leading = { Dot(it.status) }) }
        Note("Próximo passo: $next")
    }
}

@Composable
private fun ReserveCard(c: Core) {
    val pct = if (c.reserveTarget > 0) c.free / c.reserveTarget * 100 else 0.0
    BentoCard {
        LabelText("Reserva de emergência")
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Meta", color = SecondaryText); Text(brl0(c.reserveTarget), color = AppTextWhite, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        MetricProgress(pct, if (pct >= 100) CyberGreen else CyberTeal)
        Spacer(Modifier.height(4.dp))
        Text(if (c.free < c.reserveTarget) "Faltam ${brl0(c.reserveTarget - c.free)} (${pct.coerceAtLeast(0.0).roundToInt()}% completo)" else "Reserva completa. Pode começar o balde IPCA+.",
            color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NextMonthCard(p: MonthPlan, onGoFuture: () -> Unit) {
    BentoCard {
        SectionHeader("Próximo mês · ${mLabel(p.month)}", "detalhar", onGoFuture)
        StatRow("Parcelas e faturas", brl(p.parcelas))
        StatRow("Custos fixos", brl(p.fixedTotal))
        StatRow("Aporte planejado", brl(p.aporte))
        StatRow("Sobra para lazer", brl(p.sobra), valueColor = if (p.sobra < 0) ImpulseAmber else CyberGreen)
    }
}
