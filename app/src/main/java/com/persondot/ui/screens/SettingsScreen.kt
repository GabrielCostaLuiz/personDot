package com.persondot.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.persondot.BuildConfig
import com.persondot.data.model.*
import com.persondot.domain.*
import com.persondot.ui.UiPrefs
import com.persondot.ui.components.*
import com.persondot.ui.dot.*
import com.persondot.ui.theme.*
import com.persondot.ui.viewmodel.FinanceViewModel
import com.persondot.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Os assuntos de Ajustes. Cada um abre uma tela só com as opções daquele assunto. */
enum class SettingsRoute(val title: String, val icon: ImageVector, val tint: Color) {
    HUB("Ajustes", Icons.Outlined.Settings, CyberGreen),
    DOT("Dot e aparência", Icons.Outlined.Face, CyberTeal),
    PLANO("Meu plano", Icons.Outlined.Tune, CyberGreen),
    CONTAS("Contas e saldos", Icons.Outlined.AccountBalanceWallet, CyberTeal),
    CUSTOS("Custos previstos", Icons.AutoMirrored.Outlined.ReceiptLong, ImpulseAmber),
    CATEGORIAS("Categorias e grupos", Icons.Outlined.Label, PulseViolet),
    PJ("PJ e MEI", Icons.Outlined.BusinessCenter, ImpulseAmber),
    APORTES("Aportes por mês", Icons.Outlined.Savings, CyberGreen),
    BACKUP("Dados e backup", Icons.Outlined.Backup, DopamineBlue),
    ATUALIZACAO("Atualização do app", Icons.Outlined.SystemUpdate, SocialOrange)
}

/** Ajustes: um hub com o Dot no topo e os assuntos embaixo; tocar em um assunto abre a edição dele. */
@Composable
fun SettingsScreen(data: FinanceData, vm: FinanceViewModel, prefs: UiPrefs, route: SettingsRoute, onRoute: (SettingsRoute) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        when (route) {
            SettingsRoute.HUB -> Hub(data, prefs, onRoute)
            SettingsRoute.PLANO -> PlanSection(data, vm)
            SettingsRoute.CONTAS -> AccountsSection(data, vm)
            SettingsRoute.CUSTOS -> CostsSection(data, vm)
            SettingsRoute.CATEGORIAS -> CategoriesSection(data, vm)
            SettingsRoute.PJ -> PjSection(data, vm)
            SettingsRoute.APORTES -> ContributionsSection(data, vm)
            SettingsRoute.DOT -> DotSection(data, prefs)
            SettingsRoute.BACKUP -> BackupSection(vm, prefs)
            SettingsRoute.ATUALIZACAO -> UpdateSection()
        }
    }
}

// =====================================================================================
// Hub
// =====================================================================================
@Composable
private fun Hub(data: FinanceData, prefs: UiPrefs, onRoute: (SettingsRoute) -> Unit) {
    val dot = LocalDot.current
    val design = prefs.design
    val c = remember(data) { data.core() }
    val st = data.settings
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val lines = remember { listOf("Aqui você ajusta tudo. Nada no app é fixo.", "Toque em um assunto para editar os valores dele.", "Quer mudar o meu visual? Vá em Dot e aparência.", "Pode mexer à vontade: dá para voltar atrás.") }
    var idx by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(5500); idx = (idx + 1) % lines.size } }

    val summaries = mapOf(
        SettingsRoute.PLANO to "${brl0(st.income)} por mês · reserva de ${st.reserveMonths} meses · ×${st.multiplier.toInt()}",
        SettingsRoute.CONTAS to "${data.accounts.size} ${if (data.accounts.size == 1) "conta" else "contas"} · ${brl0(c.inHand)} em conta",
        SettingsRoute.CUSTOS to "${data.fixed.size} custos · ${brl0(data.fixed.sumOf { it.amount })} por mês",
        SettingsRoute.CATEGORIAS to "${st.categoryList.size} categorias de gasto · 3 grupos",
        SettingsRoute.PJ to if (st.meiOn) "MEI: limite de ${brl0(st.meiLimit)} por ano" else "acompanhamento do MEI desligado",
        SettingsRoute.APORTES to "padrão ${brl0(st.aporteLongo)} por mês · ${data.aportes.size} ${if (data.aportes.size == 1) "mês ajustado" else "meses ajustados"}",
        SettingsRoute.DOT to "${prefs.dotName} · ${design.name} · tema ${if (prefs.dark) "escuro" else "claro"}",
        SettingsRoute.BACKUP to "exportar, importar ou apagar seus dados",
        SettingsRoute.ATUALIZACAO to "versão instalada ${BuildConfig.VERSION_NAME}"
    )
    val tiles = SettingsRoute.entries.filter { it != SettingsRoute.HUB }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp + LocalBarInset.current)) {
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.verticalGradient(listOf(design.color.copy(alpha = 0.26f), DarkSurface)))
                    .border(1.dp, CardBorderGlance, RoundedCornerShape(28.dp)).padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DotAvatar(design, size = 150.dp, mood = dot.mood ?: moodFor(data), bounceKey = dot.bounceKey, onClick = { dot.react(null, Mood.HAPPY) })
                Spacer(Modifier.height(6.dp))
                Box(Modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                    AnimatedContent(lines[idx], transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(200)) }, label = "hubline") { t ->
                        Text(t, color = AppTextWhite, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        itemsIndexed(tiles) { i, r ->
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(320, delayMillis = 80 + i * 55)) + slideInVertically(tween(320, delayMillis = 80 + i * 55)) { it / 3 }
            ) { SectionTile(r, summaries[r].orEmpty()) { onRoute(r) } }
        }
    }
}

@Composable
private fun SectionTile(route: SettingsRoute, summary: String, onClick: () -> Unit) {
    BentoCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(route.tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(route.icon, null, tint = route.tint)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(route.title, color = AppTextWhite, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(summary, color = SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SecondaryText)
        }
    }
}

@Composable
private fun Section(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp + LocalBarInset.current), content = content)
}

// =====================================================================================
// Meu plano
// =====================================================================================
@Composable
private fun PlanSection(data: FinanceData, vm: FinanceViewModel) {
    val st = data.settings
    var dialog by remember { mutableStateOf<String?>(null) }
    Section {
        item {
            BentoCard {
                SectionHeader("Renda")
                StatRow("Renda mensal líquida", brl(st.income), sub = "o que cai todo mês", onClick = { dialog = "income" })
                StatRow("Dia em que a renda cai", "dia ${st.incomeDay}", onClick = { dialog = "day" })
            }
        }
        item {
            BentoCard {
                SectionHeader("Reserva e investimento")
                StatRow("Meses de reserva de emergência", "${st.reserveMonths} meses", sub = "de custos de casa + trabalho", onClick = { dialog = "months" })
                StatRow("Multiplicador (regra dos 200)", "× ${st.multiplier.toInt()}", sub = "200 = 6% real ao ano · 250 = mais conservador", onClick = { dialog = "mult" })
                StatRow("Rendimento real esperado", "${"%.1f".format(st.realRate * 100)}% ao ano", sub = "acima da inflação", onClick = { dialog = "rate" })
                StatRow("Início do balde IPCA+", mLabel(st.ipcaStart.ifBlank { null }), sub = "vazio = logo depois que a reserva fechar", onClick = { dialog = "ipca" })
            }
        }
        item {
            BentoCard {
                SectionHeader("Aporte")
                StatRow("Aporte mensal padrão", brl(st.aporteLongo), sub = "vale para todos os meses sem ajuste (veja Aportes por mês)", onClick = { dialog = "aporte" })
            }
        }
    }
    when (dialog) {
        "income" -> NumberDialog("Renda mensal líquida", "Valor (R$)", st.income, onSave = { vm.saveSettings(st.copy(income = it)) }) { dialog = null }
        "day" -> NumberDialog("Dia em que a renda cai", "Dia do mês (1 a 31)", st.incomeDay.toDouble(), decimal = false, onSave = { vm.saveSettings(st.copy(incomeDay = it.toInt().coerceIn(1, 31))) }) { dialog = null }
        "months" -> NumberDialog("Meses de reserva", "Quantidade de meses", st.reserveMonths.toDouble(), decimal = false, onSave = { vm.saveSettings(st.copy(reserveMonths = it.toInt().coerceAtLeast(1))) }) { dialog = null }
        "mult" -> NumberDialog("Multiplicador", "Valor (200 = 6% real; 250 = conservador)", st.multiplier, decimal = false, hint = "A meta de cada conta é o valor dela vezes este número.", onSave = { vm.saveSettings(st.copy(multiplier = it.coerceAtLeast(1.0))) }) { dialog = null }
        "rate" -> NumberDialog("Rendimento real esperado", "% ao ano acima da inflação", st.realRate * 100, onSave = { vm.saveSettings(st.copy(realRate = (if (it > 0) it else 6.0) / 100)) }) { dialog = null }
        "aporte" -> NumberDialog("Aporte mensal padrão", "Valor (R$)", st.aporteLongo, onSave = { vm.saveSettings(st.copy(aporteLongo = it)) }) { dialog = null }
        "ipca" -> MonthPickDialog("Início do balde IPCA+", st.ipcaStart, allowNone = true, onSave = { vm.saveSettings(st.copy(ipcaStart = it)) }) { dialog = null }
    }
}

// =====================================================================================
// Contas e saldos
// =====================================================================================
@Composable
private fun AccountsSection(data: FinanceData, vm: FinanceViewModel) {
    var dialog by remember { mutableStateOf<Any?>(null) }
    Section {
        item {
            BentoCard {
                SectionHeader("Contas", "+ conta") { dialog = "newAccount" }
                if (data.accounts.isEmpty()) EmptyHint("Nenhuma conta. Cadastre onde seu dinheiro está.")
                data.accounts.forEach { a ->
                    StatRow(a.name, brl(a.balance), sub = listOf(if (a.kind == Account.KIND_IPCA) "balde IPCA+" else "liquidez diária", a.note.ifBlank { null }).filterNotNull().joinToString(" · "), onClick = { dialog = a })
                }
            }
        }
        item {
            BentoCard {
                SectionHeader("A receber", "+ entrada") { dialog = "newReceivable" }
                if (data.receivables.isEmpty()) EmptyHint("Nada a receber.")
                data.receivables.forEach { r -> StatRow(r.desc, brl(r.amount), sub = if (r.received) "recebido" else null, valueColor = if (r.received) SecondaryText else CyberGreen, onClick = { dialog = r }) }
            }
        }
    }
    when (val d = dialog) {
        "newAccount" -> AccountDialog(null, { vm.saveAccount(it) }, null) { dialog = null }
        is Account -> AccountDialog(d, { vm.saveAccount(it) }, { vm.deleteAccount(d) }) { dialog = null }
        "newReceivable" -> ReceivableDialog(null, data.accounts, { vm.saveReceivable(it) }, { _, _, _ -> }, null) { dialog = null }
        is Receivable -> ReceivableDialog(d, data.accounts, { vm.saveReceivable(it) }, { r, v, acc -> vm.receive(r, v, acc) }, { vm.deleteReceivable(d) }) { dialog = null }
    }
}

// =====================================================================================
// Custos previstos
// =====================================================================================
@Composable
private fun CostsSection(data: FinanceData, vm: FinanceViewModel) {
    val st = data.settings
    val now = remember { monthKey(LocalDate.now()) }
    var dialog by remember { mutableStateOf<Any?>(null) }
    Section {
        item { Text("Estes são os valores PADRÃO de cada mês. Para mudar só um mês, use a aba Gastos.", color = SecondaryText, style = MaterialTheme.typography.bodySmall) }
        st.groups.map { it.id }.forEach { grp ->
            item(key = grp) {
                val items = data.fixed.filter { it.grp == grp }.sortedByDescending { it.amount }
                BentoCard {
                    SectionHeader(st.groupLabel(grp), "+ custo") { dialog = "new:$grp" }
                    if (items.isEmpty()) EmptyHint("Nenhum custo neste grupo.")
                    items.forEach { f ->
                        StatRow(f.name, brl(f.amount),
                            sub = listOfNotNull(if (f.onCard) "no cartão" else null, f.startsAt?.let { "desde ${mLabel(it)}" }, f.endsBefore?.let { "cancelado a partir de ${mLabel(it)}" }).joinToString(" · ").ifBlank { null },
                            valueColor = if (f.endsBefore != null) SecondaryText else AppTextWhite, onClick = { dialog = f })
                    }
                    if (items.isNotEmpty()) StatRow("Total por mês", brl(items.sumOf { it.amount }), valueColor = CyberGreen)
                }
            }
        }
    }
    when (val d = dialog) {
        is FixedCost -> FixedDialog(d, d.grp, now, st, { vm.saveFixed(it) }, { vm.deleteFixed(d) }) { dialog = null }
        is String -> if (d.startsWith("new:")) FixedDialog(null, d.removePrefix("new:"), now, st, { vm.saveFixed(it) }, null) { dialog = null }
    }
}

// =====================================================================================
// Categorias e grupos
// =====================================================================================
@Composable
private fun CategoriesSection(data: FinanceData, vm: FinanceViewModel) {
    val st = data.settings
    var dialog by remember { mutableStateOf<Any?>(null) }
    fun clean(s: String) = s.replace("|", " ").trim()
    fun saveCats(list: List<String>) = vm.saveSettings(st.copy(categories = list.joinToString("|").ifBlank { "Outros" }))
    fun saveGroup(i: Int, name: String) {
        val parts = st.groupNames.split('|').toMutableList().also { while (it.size < 3) it += "" }
        parts[i] = clean(name); vm.saveSettings(st.copy(groupNames = parts.take(3).joinToString("|")))
    }
    Section {
        item {
            BentoCard {
                SectionHeader("Categorias dos gastos do dia a dia", "+ categoria") { dialog = "newCat" }
                Text("Aparecem ao lançar um gasto e nas perguntas do Dot.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                st.categoryList.forEach { c -> StatRow(c, "editar", valueColor = CyberGreen, onClick = { dialog = CatEdit(c) }) }
            }
        }
        item {
            BentoCard {
                SectionHeader("Grupos de custo", "+ grupo") { dialog = "newGroup" }
                Text("Casa e trabalho entram na reserva; opcionais podem ser cortados. Você pode renomear e criar outros grupos (essenciais ou opcionais) para se organizar melhor.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                st.groups.forEachIndexed { i, g ->
                    val tag = if (g.essential) "essencial" else "opcional"
                    StatRow("${g.name} · $tag", "editar", valueColor = CyberGreen, onClick = { dialog = if (g.custom) CustomGroupEdit(g) else GroupEdit(i, g.name) })
                }
            }
        }
    }
    when (val d = dialog) {
        "newCat" -> TextDialog("Nova categoria", "Nome", "", onSave = { n -> clean(n).takeIf { it.isNotEmpty() && it !in st.categoryList }?.let { saveCats(st.categoryList + it) } }) { dialog = null }
        is CatEdit -> TextDialog("Editar categoria", "Nome", d.name,
            onSave = { n -> clean(n).takeIf { it.isNotEmpty() }?.let { nn -> saveCats(st.categoryList.map { if (it == d.name) nn else it }.distinct()) } },
            onDelete = if (st.categoryList.size > 1) ({ saveCats(st.categoryList - d.name) }) else null) { dialog = null }
        is GroupEdit -> TextDialog("Nome do grupo", "Nome", d.name, onSave = { n -> if (clean(n).isNotEmpty()) saveGroup(d.index, n) }) { dialog = null }
        "newGroup" -> NewGroupDialog(onCreate = { n, ess ->
            val id = "G" + (st.groups.filter { it.custom }.mapNotNull { it.id.drop(1).toIntOrNull() }.maxOrNull()?.plus(1) ?: 1)
            vm.saveSettings(st.copy(customGroups = (st.groups + GroupDef(id, clean(n), ess, custom = true)).encodeCustom()))
        }) { dialog = null }
        is CustomGroupEdit -> TextDialog("Nome do grupo", "Nome", d.def.name,
            onSave = { n -> if (clean(n).isNotEmpty()) vm.saveSettings(st.copy(customGroups = st.groups.map { if (it.id == d.def.id) it.copy(name = clean(n)) else it }.encodeCustom())) },
            onDelete = if (data.fixed.none { it.grp == d.def.id }) ({ vm.saveSettings(st.copy(customGroups = st.groups.filter { it.id != d.def.id }.encodeCustom())) }) else null) { dialog = null }
    }
}
private data class CatEdit(val name: String)
private data class GroupEdit(val index: Int, val name: String)
private data class CustomGroupEdit(val def: GroupDef)

// =====================================================================================
// PJ e MEI
// =====================================================================================
@Composable
private fun PjSection(data: FinanceData, vm: FinanceViewModel) {
    val st = data.settings
    val today = remember { LocalDate.now() }
    val months = remember { (1..12).map { "%04d-%02d".format(today.year, it) } }
    var dialog by remember { mutableStateOf<String?>(null) }
    Section {
        item {
            BentoCard {
                SectionHeader("Acompanhar o MEI")
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Avisar sobre o limite e os prazos", color = AppTextWhite, fontWeight = FontWeight.SemiBold)
                        Text("O Dot projeta o seu faturamento e avisa quando o MEI pode estourar.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = st.meiOn, onCheckedChange = { vm.saveSettings(st.copy(meiOn = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = CyberGreen))
                }
                StatRow("Limite anual de faturamento", brl(st.meiLimit), sub = "valor que você controla: confira o vigente, a regra pode mudar", onClick = { dialog = "limit" })
            }
        }
        item {
            BentoCard {
                SectionHeader("Faturamento de ${today.year}")
                Text("Meses sem registro usam a sua renda (${brl0(st.income)}) como estimativa. Toque para registrar o valor real.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                months.forEach { m ->
                    val real = data.revenues.firstOrNull { it.month == m }
                    StatRow(mLabel(m), brl(data.revenueOf(m)), sub = if (real != null) "registrado" else if (m <= monthKey(today)) "estimado" else "previsto",
                        valueColor = if (real != null) CyberGreen else AppTextWhite, onClick = { dialog = m })
                }
                data.mei(today)?.let { Note("Acumulado no ano: ${brl0(it.ytd)} de ${brl0(it.limit)} (${(it.usedPct * 100).toInt()}%).") }
            }
        }
        item {
            BentoCard {
                SectionHeader("Datas que o Dot acompanha")
                StatRow("DAS mensal", "dia 20", sub = "ele avisa quando o seu custo \"DAS\" ainda não foi confirmado")
                StatRow("Declaração anual (DASN-SMEI)", "até 31/05", sub = "confira a data oficial no Portal do Empreendedor")
                Note("São orientações educativas. Para decisões como abrir uma ME, fale com um contador.")
            }
        }
    }
    when (val d = dialog) {
        "limit" -> NumberDialog("Limite anual do MEI", "Valor (R$)", st.meiLimit, onSave = { vm.saveSettings(st.copy(meiLimit = it)) }) { dialog = null }
        null -> {}
        else -> {
            val real = data.revenues.firstOrNull { it.month == d }
            NumberDialog("Faturamento de ${mLabel(d)}", "Valor (R$)", real?.amount ?: data.revenueOf(d), onSave = { vm.saveRevenue(d, it) }) { dialog = null }
        }
    }
}

// =====================================================================================
// Aportes por mês
// =====================================================================================
@Composable
private fun ContributionsSection(data: FinanceData, vm: FinanceViewModel) {
    val st = data.settings
    val months = remember { visibleMonths() }
    var dialog by remember { mutableStateOf<String?>(null) }
    Section {
        item {
            BentoCard {
                SectionHeader("Aporte padrão")
                StatRow("Todos os meses sem ajuste", brl(st.aporteLongo), onClick = { dialog = "default" })
                Text("Para um mês específico (por exemplo, mais apertado por causa de parcelas), ajuste abaixo. Toque no mês para editar; \"voltar ao padrão\" desfaz.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            BentoCard {
                SectionHeader("Próximos meses")
                months.forEach { m ->
                    val custom = data.aportes[m]
                    StatRow(mLabel(m), brl(custom ?: st.aporteLongo), sub = if (custom != null) "ajustado" else "padrão", valueColor = if (custom != null) CyberTeal else AppTextWhite, onClick = { dialog = m })
                }
            }
        }
    }
    when (val d = dialog) {
        "default" -> NumberDialog("Aporte padrão", "Valor (R$)", st.aporteLongo, onSave = { vm.saveSettings(st.copy(aporteLongo = it)) }) { dialog = null }
        null -> {}
        else -> AporteDialog(d, data.aportes[d] ?: st.aporteLongo, { vm.saveAporte(d, it) }, onReset = if (data.aportes[d] != null) ({ vm.clearAporte(d) }) else null) { dialog = null }
    }
}

// =====================================================================================
// Dot e aparência
// =====================================================================================
@Composable
private fun DotSection(data: FinanceData, prefs: UiPrefs) {
    val dot = LocalDot.current
    val design = prefs.design
    var nameDialog by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    fun pin(cls: Class<*>) {
        val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
        if (mgr.isRequestPinAppWidgetSupported) mgr.requestPinAppWidget(android.content.ComponentName(ctx, cls), null, null)
        else Toast.makeText(ctx, "Seu launcher não aceita adicionar por aqui. Segure na tela inicial, toque em Widgets e procure PersonDot.", Toast.LENGTH_LONG).show()
    }
    Section {
        item {
            BentoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DotAvatar(design, size = 96.dp, mood = dot.mood ?: moodFor(data), bounceKey = dot.bounceKey, onClick = { dot.react("Gostei do visual!", Mood.HAPPY) })
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(prefs.dotName, color = AppTextWhite, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text("${design.name}${if (prefs.dotColor != null) " · cor sua" else ""}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { nameDialog = true }, contentPadding = PaddingValues(0.dp)) { Text("mudar o nome", color = CyberGreen, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        item {
            BentoCard {
                SectionHeader("Visual do Dot")
                DotPicker(prefs.dotId, prefs.dotColor?.let { Color(it.toInt()) }, onPick = { prefs.updateDot(it); dot.react("Esse sou eu agora!", Mood.HAPPY) }, onColor = { prefs.updateDotColor(it); dot.react(null, Mood.HAPPY) })
            }
        }
        item {
            BentoCard {
                SectionHeader("Comportamento")
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Dicas do Dot nas telas", color = AppTextWhite, fontWeight = FontWeight.SemiBold)
                        Text("A faixa fina no topo com dicas e reações.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = prefs.tips, onCheckedChange = { prefs.updateTips(it) }, colors = SwitchDefaults.colors(checkedTrackColor = CyberGreen))
                }
                HorizontalDivider(color = BorderColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Tema escuro", color = AppTextWhite, fontWeight = FontWeight.SemiBold)
                        Text("Desligue para o visual claro.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = prefs.dark, onCheckedChange = { prefs.updateDark(it) }, colors = SwitchDefaults.colors(checkedTrackColor = CyberGreen))
                }
            }
        }
        item {
            BentoCard {
                SectionHeader("Widgets na tela inicial")
                Text("O Dot na tela do celular: livre de verdade, saúde, próximo passo e atalhos para lançar um gasto ou falar com ele.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("Painel (grande)", { pin(com.persondot.widget.DotInfoWidget::class.java) }, Modifier.weight(1f))
                    SoftButton("Gasto rápido", { pin(com.persondot.widget.DotQuickWidget::class.java) }, Modifier.weight(1f))
                }
            }
        }
    }
    if (nameDialog) TextDialog("Nome do seu Dot", "Nome", prefs.dotName, onSave = { prefs.updateDotName(it); dot.react("Adorei meu nome!", Mood.HAPPY) }) { nameDialog = false }
}

// =====================================================================================
// Dados e backup
// =====================================================================================
@Composable
private fun BackupSection(vm: FinanceViewModel, prefs: UiPrefs) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf(false) }
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(vm.exportJson().toByteArray()) } } }
                .onSuccess { toast("Backup salvo.") }.onFailure { toast("Não consegui salvar o backup.") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } } }.getOrNull()
            if (text == null) toast("Não consegui ler o arquivo.")
            else vm.importJson(text) { r -> toast(if (r.isSuccess) "Dados importados." else "Arquivo inválido. Use um backup do PersonDot.") }
        }
    }
    Section {
        item {
            BentoCard {
                SectionHeader("Backup")
                Text("Seus dados ficam só neste aparelho. Exporte de vez em quando; o mesmo arquivo leva tudo para outro celular.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Exportar", { exportLauncher.launch("persondot-${LocalDate.now()}.json") })
                    SoftButton("Importar", { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
                }
            }
        }
        item {
            BentoCard {
                SectionHeader("Zona de perigo")
                Text("Apaga contas, dívidas, custos e lançamentos deste aparelho. Exporte um backup antes.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                SoftButton("Apagar tudo", { dialog = true }, danger = true)
            }
        }
    }
    if (dialog) ConfirmSheet("Apagar tudo?", "Isso não pode ser desfeito. Só continue se você já exportou um backup.", "Apagar", onConfirm = { vm.resetAll(); prefs.resetAll() }) { dialog = false }
}

// =====================================================================================
// Atualização do app
// =====================================================================================
private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object NoRelease : UpdateState
    data class Available(val release: Updater.Release) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Error(val message: String) : UpdateState
}

@Composable
private fun UpdateSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    fun checkUpdate() = scope.launch {
        update = UpdateState.Checking
        update = try {
            val r = Updater.latest()
            when { r == null -> UpdateState.NoRelease; Updater.isNewer(r) -> UpdateState.Available(r); else -> UpdateState.UpToDate }
        } catch (e: Exception) { UpdateState.Error("Sem conexão com o GitHub. Tente de novo.") }
    }
    fun downloadAndInstall(r: Updater.Release) = scope.launch {
        update = UpdateState.Downloading(0f)
        try {
            val apk = Updater.download(ctx, r) { update = UpdateState.Downloading(it) }
            update = UpdateState.Available(r)
            if (Updater.install(ctx, apk) == Updater.InstallResult.NEEDS_PERMISSION) toast("Permita instalar apps do PersonDot e toque em baixar de novo.")
        } catch (e: Exception) { update = UpdateState.Error("Falha no download. Tente de novo.") }
    }

    Section {
        item {
            BentoCard {
                SectionHeader("Versão")
                Text("Instalada: ${BuildConfig.VERSION_NAME}", color = AppTextWhite, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                when (val u = update) {
                    UpdateState.Idle -> Text("Toque para ver se há uma versão nova.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    UpdateState.Checking -> Row { CircularProgressIndicator(Modifier.size(18.dp), color = CyberGreen, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Procurando…", color = SecondaryText) }
                    UpdateState.UpToDate -> Text("Você já está na versão mais recente.", color = CyberGreen)
                    UpdateState.NoRelease -> Text("Nenhuma versão publicada ainda.", color = SecondaryText)
                    is UpdateState.Available -> {
                        Text("Nova versão: ${u.release.tag}", color = CyberGreen, fontWeight = FontWeight.Bold)
                        if (u.release.notes.isNotBlank()) Text(u.release.notes.take(300), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    is UpdateState.Downloading -> { Text("Baixando… ${(u.progress * 100).toInt()}%", color = SecondaryText); Spacer(Modifier.height(6.dp)); MetricProgress(u.progress * 100.0) }
                    is UpdateState.Error -> Text(u.message, color = ImpulseAmber)
                }
                Spacer(Modifier.height(10.dp))
                val avail = update as? UpdateState.Available
                if (avail != null) PrimaryButton("Baixar e instalar ${avail.release.tag}", { downloadAndInstall(avail.release) })
                else SoftButton("Procurar atualização", { checkUpdate() })
            }
        }
    }
}
