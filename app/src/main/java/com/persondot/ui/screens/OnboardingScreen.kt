package com.persondot.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.persondot.data.model.Account
import com.persondot.data.model.GroupDef
import com.persondot.data.model.encodeCustom
import com.persondot.data.model.DEFAULT_GROUP_NAMES
import com.persondot.data.model.FixedCost
import com.persondot.data.model.Settings
import com.persondot.domain.brl0
import com.persondot.ui.UiPrefs
import com.persondot.ui.components.*
import com.persondot.ui.dot.DotAvatar
import com.persondot.ui.dot.DotDesign
import com.persondot.ui.dot.DotPicker
import com.persondot.ui.dot.Mood
import com.persondot.ui.dot.dotById
import com.persondot.ui.theme.CyberGreen
import com.persondot.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

private data class DraftAcc(val name: String, val balance: Double)
private data class DraftCost(val name: String, val amount: Double, val grp: String)

private const val LAST_STEP = 5

/**
 * A primeira vez no app: o Dot conduz um passo a passo (visual do Dot, renda, conta, custos fixos, MEI e plano),
 * salva tudo no final, conversa com a pessoa e voa do meio da tela até o lugar dele no Painel.
 */
@Composable
fun OnboardingFlow(prefs: UiPrefs, vm: FinanceViewModel, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var dotId by remember { mutableStateOf(prefs.dotId) }
    var dotColor by remember { mutableStateOf<Color?>(prefs.dotColor?.let { Color(it.toInt()) }) }
    var name by remember { mutableStateOf(prefs.dotName) }
    var income by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("10") }
    var accName by remember { mutableStateOf("Conta principal") }
    var balance by remember { mutableStateOf("") }
    val accs = remember { mutableStateListOf<DraftAcc>() }
    var hint by remember { mutableStateOf<String?>(null) }      // aviso quando falta preencher algo
    fun addAccount(explicit: Boolean = false): Boolean {
        if (accName.isBlank()) { if (explicit) hint = "Dê um nome para a conta."; return false }
        if (!explicit && balance.isBlank()) return false           // ao continuar, só entra o que a pessoa realmente preencheu
        accs += DraftAcc(accName.trim(), parseMoney(balance)); accName = ""; balance = ""; hint = null
        return true
    }
    val customGroups = remember { mutableStateListOf<GroupDef>() }        // grupos de custo criados aqui
    var newGroup by remember { mutableStateOf("") }
    var newGroupEssential by remember { mutableStateOf(true) }
    val costs = remember { mutableStateListOf<DraftCost>() }
    var cName by remember { mutableStateOf("") }
    var cAmount by remember { mutableStateOf("") }
    var cGrp by remember { mutableStateOf(FixedCost.GRP_CASA) }
    var regime by remember { mutableStateOf("NONE") }          // NONE, MEI, ME ou EPP
    var limitStr by remember { mutableStateOf("") }
    var aporte by remember { mutableStateOf("") }
    var reserve by remember { mutableIntStateOf(6) }
    var finishing by remember { mutableStateOf(false) }
    var covered by remember { mutableStateOf(false) }      // o formulário já foi coberto: some, e o voo revela o Painel

    val design = dotById(dotId).let { d -> dotColor?.let { d.copy(color = it) } ?: d }
    val shownName = name.trim().ifEmpty { "Dot" }
    val incomeV = parseMoney(income)
    val groups = DEFAULT_GROUP_NAMES.split('|')
    val allGroups = listOf(
        GroupDef(FixedCost.GRP_CASA, groups[0].substringBefore(" ·"), true), GroupDef(FixedCost.GRP_TRABALHO, groups[1], true),
        GroupDef(FixedCost.GRP_OPCIONAL, groups[2], false)
    ) + customGroups

    val bubble = when (step) {
        0 -> "Oi! Eu sou o $shownName, seu assistente financeiro. Antes de começar, escolha como eu sou e me dê um nome."
        1 -> "Vamos ao básico: quanto entra por mês, já descontados os impostos?"
        2 -> "Agora me diga onde está o seu dinheiro hoje. Adicione quantas contas quiser (conta, carteira, corretora...); dá para mudar depois."
        3 -> "Quais contas você paga todo mês? Adicione as principais (luz, mercado, internet...). Dá para editar depois."
        4 -> "Você tem empresa? Me diga se é MEI, ME ou outra: eu acompanho o seu faturamento e aviso quando chegar perto do limite do ano."
        else -> "Última: quanto você quer guardar por mês? Uma meta comum é 15% da renda${if (incomeV > 0) " (${brl0(incomeV * 0.15)})" else ""}."
    }

    fun finish() {
        val st = Settings(
            income = incomeV, incomeDay = day.toIntOrNull()?.coerceIn(1, 31) ?: 10,
            aporteLongo = parseMoney(aporte).let { if (it > 0) it else incomeV * 0.15 }, reserveMonths = reserve, meiOn = regime != "NONE", meiLimit = parseMoney(limitStr).let { if (it > 0) it else 81000.0 },
            customGroups = customGroups.toList().encodeCustom()
        )
        vm.saveSettings(st)
        addAccount()
        accs.forEach { vm.saveAccount(Account(name = it.name, balance = it.balance)) }
        costs.forEach { vm.saveFixed(FixedCost(name = it.name, amount = it.amount, grp = it.grp)) }
        prefs.updateDot(dotId); prefs.updateDotColor(dotColor); prefs.updateDotName(name)
        prefs.markOnboarded()
        finishing = true
    }

    Box(Modifier.fillMaxSize().then(if (covered) Modifier else Modifier.background(MaterialTheme.colorScheme.background))) {
        if (!covered) Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
            // progresso
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(LAST_STEP + 1) { i ->
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i <= step) CyberGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)))
                }
            }
            // o Dot conversando
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                DotAvatar(design, size = 88.dp, mood = if (step == 0) Mood.HAPPY else Mood.NEUTRAL, bounceKey = step)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)).padding(14.dp)) {
                    AnimatedContent(bubble, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(120)) }, label = "bubble") { t ->
                        Text(t, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            // o passo
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    step, label = "step",
                    transitionSpec = { (slideInHorizontally { it / 4 } + fadeIn(tween(250))) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut(tween(150))) }
                ) { s ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (s) {
                            0 -> {
                                Field("Meu nome", name, { name = it.take(16) })
                                DotPicker(dotId, dotColor, { dotId = it }, { dotColor = it })
                            }
                            1 -> {
                                MoneyField("Renda mensal líquida (R$)", income) { income = it }
                                Field("Dia do mês em que cai", day, { day = it.filter(Char::isDigit).take(2) }, KeyboardType.Number)
                            }
                            2 -> {
                                Field("Nome da conta", accName, { accName = it; hint = null })
                                MoneyField("Saldo de hoje (R$)", balance) { balance = it }
                                SoftButton("+ Adicionar essa conta", { addAccount(explicit = true) }, Modifier.fillMaxWidth())
                                hint?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                if (accs.isNotEmpty()) Text("Contas adicionadas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                accs.forEachIndexed { i, a ->
                                    ItemRow(a.name, brl0(a.balance),
                                        onEdit = { accName = a.name; balance = editMoney(a.balance); accs.removeAt(i) },
                                        onRemove = { accs.removeAt(i) })
                                }
                            }
                            3 -> {
                                Field("Nome da conta", cName, { cName = it; hint = null })
                                MoneyField("Valor por mês (R$)", cAmount) { cAmount = it; hint = null }
                                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    allGroups.forEach { g -> Pill(g.name, cGrp == g.id) { cGrp = g.id } }
                                }
                                SoftButton("+ Adicionar essa conta", {
                                    val v = parseMoney(cAmount)
                                    when {
                                        cName.isBlank() -> hint = "Dê um nome para a conta."
                                        v <= 0 -> hint = "Digite o valor mensal."
                                        else -> { costs += DraftCost(cName.trim(), v, cGrp); cName = ""; cAmount = ""; hint = null }
                                    }
                                }, Modifier.fillMaxWidth())
                                hint?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                if (costs.isNotEmpty()) Text("Contas adicionadas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                costs.forEachIndexed { i, c ->
                                    ItemRow(c.name, "${brl0(c.amount)} · ${allGroups.firstOrNull { it.id == c.grp }?.name ?: ""}",
                                        onEdit = { cName = c.name; cAmount = editMoney(c.amount); cGrp = c.grp; costs.removeAt(i) },
                                        onRemove = { costs.removeAt(i) })
                                }
                                // grupos de custo: além de Casa, Trabalho e Opcionais, a pessoa pode criar os dela
                                Spacer(Modifier.height(8.dp))
                                Text("Quer organizar de outro jeito? Crie um grupo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Field("Nome do grupo (ex.: Saúde, Estudos, Lazer)", newGroup, { newGroup = it })
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Pill("Essencial", newGroupEssential) { newGroupEssential = true }
                                    Pill("Opcional", !newGroupEssential) { newGroupEssential = false }
                                }
                                Text(if (newGroupEssential) "Essencial: entra na conta da reserva de emergência." else "Opcional: pode ser cortado numa simulação.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                SoftButton("+ Criar grupo", {
                                    val n = newGroup.trim().replace("|", " ").replace("~", " ")
                                    if (n.isEmpty() || allGroups.any { it.name.equals(n, ignoreCase = true) }) hint = "Dê um nome diferente dos grupos que já existem."
                                    else {
                                        customGroups += GroupDef("G" + (customGroups.size + 1), n, newGroupEssential, custom = true)
                                        cGrp = customGroups.last().id; newGroup = ""; hint = null
                                    }
                                }, Modifier.fillMaxWidth())
                                customGroups.toList().forEach { g ->
                                    ItemRow(g.name, if (g.essential) "grupo essencial" else "grupo opcional", onEdit = { newGroup = g.name; newGroupEssential = g.essential; customGroups.remove(g) }, onRemove = { customGroups.remove(g); if (cGrp == g.id) cGrp = FixedCost.GRP_CASA })
                                }
                            }
                            4 -> {
                                val options = listOf(
                                    Triple("MEI", "MEI", 81_000.0), Triple("ME", "ME (microempresa)", 360_000.0),
                                    Triple("EPP", "EPP (pequeno porte)", 4_800_000.0), Triple("NONE", "Não tenho empresa", 0.0)
                                )
                                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    options.forEach { (id, label, limit) ->
                                        Pill(label, regime == id) { regime = id; limitStr = if (limit > 0) editMoney(limit) else "" }
                                    }
                                }
                                if (regime != "NONE" && limitStr.isNotEmpty() || regime != "NONE") {
                                    MoneyField("Limite de faturamento por ano (R$)", limitStr) { limitStr = it }
                                    Text("O limite muda com a regra e o regime; ajuste se precisar. Dá para mudar depois em Ajustes → PJ e MEI.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text("Sem problema: o acompanhamento de faturamento fica desligado (dá para ligar depois em Ajustes).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            else -> {
                                MoneyField("Quanto guardar por mês (R$)", aporte) { aporte = it }
                                Text("Meses de essenciais na reserva de emergência", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(3, 6, 9, 12).forEach { m -> Pill("$m", reserve == m) { reserve = m } }
                                }
                            }
                        }
                    }
                }
            }
            // botões fixos
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step > 0) SoftButton("Voltar", { hint = null; step-- }, Modifier.weight(1f))
                PrimaryButton(
                    if (step == LAST_STEP) "Começar" else "Continuar",
                    { if (step == LAST_STEP) finish() else { if (step == 2) addAccount(); hint = null; step++ } },
                    Modifier.weight(1.6f), enabled = step != 1 || incomeV > 0
                )
            }
        }
        if (finishing) FinaleOverlay(design, shownName, onCovered = { covered = true }, onDone = onDone)
    }
}

/** Uma linha de item já adicionado, com editar e remover. */
@Composable
private fun ItemRow(title: String, subtitle: String, onEdit: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Editar", color = CyberGreen, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable(onClick = onEdit).padding(8.dp))
        Text("Remover", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable(onClick = onRemove).padding(8.dp))
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) CyberGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)
    ) { Text(label, color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) }
}

/** O fim do onboarding: o Dot no meio da tela fala com a pessoa e depois voa até o lugar dele no Painel. */
@Composable
private fun FinaleOverlay(design: DotDesign, name: String, onCovered: () -> Unit, onDone: () -> Unit) {
    val density = LocalDensity.current
    val cfg = LocalConfiguration.current
    val wPx = with(density) { cfg.screenWidthDp.dp.toPx() }
    val hPx = with(density) { cfg.screenHeightDp.dp.toPx() }
    val bg = MaterialTheme.colorScheme.background
    val cover = remember { Animatable(0f) }      // o fundo cobre o formulário
    val say = remember { Animatable(0f) }        // a fala
    val fly = remember { Animatable(0f) }        // o voo até o Painel
    var bounce by remember { mutableIntStateOf(0) }
    var go by remember { mutableStateOf(false) }   // a pessoa tocou em "Bora começar"

    LaunchedEffect(Unit) {
        cover.animateTo(1f, tween(450))
        onCovered()
        bounce++
        say.animateTo(1f, tween(450))
        snapshotFlow { go }.first { it }
        say.animateTo(0f, tween(250))
        fly.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        onDone()
    }

    val t = fly.value
    val sizeDp = lerp(190.dp, 96.dp, t)
    val sizePx = with(density) { sizeDp.toPx() }
    val x = wPx / 2 + (wPx * 0.203f - wPx / 2) * t
    val y = hPx * 0.40f + (hPx * 0.196f - hPx * 0.40f) * t

    Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = cover.value * (1f - t) }.background(bg))
        DotAvatar(design, size = sizeDp, mood = Mood.HAPPY, bounceKey = bounce, modifier = Modifier.offset { IntOffset((x - sizePx / 2).roundToInt(), (y - sizePx / 2).roundToInt()) })
        Text(
            "Tudo pronto! Vou ficar de olho nos seus números e te aviso do que importa. Bora lá, $name!", color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).offset { IntOffset(0, (hPx * 0.40f + with(density) { 125.dp.toPx() }).roundToInt()) }
                .padding(horizontal = 32.dp).graphicsLayer { alpha = say.value }
        )
        if (say.value > 0.05f && !go) PrimaryButton(
            "Bora começar", { go = true },
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp).fillMaxWidth().graphicsLayer { alpha = say.value }
        )
    }
}
