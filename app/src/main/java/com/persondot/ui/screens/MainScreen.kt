package com.persondot.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.persondot.data.model.*
import com.persondot.domain.*
import com.persondot.ui.UiPrefs
import com.persondot.ui.dot.*
import com.persondot.ui.components.DotNavBar
import com.persondot.ui.components.LocalBarInset
import com.persondot.ui.components.NavEntry
import com.persondot.ui.theme.*
import com.persondot.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class Tab(val title: String, val heading: String, val active: ImageVector?, val inactive: ImageVector?) {
    PAINEL("Painel", "Painel de Controle", Icons.Filled.Speed, Icons.Outlined.Speed),
    GASTOS("Gastos", "Gastos e previsões", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    DOT("Dot", "Conversa com o Dot", null, null),   // o ícone é o próprio Dot
    RELATORIOS("Relatórios", "Relatórios", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    FUTURO("Futuro", "Evoluir", Icons.Filled.TrendingUp, Icons.Outlined.TrendingUp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: FinanceViewModel, prefs: UiPrefs, modifier: Modifier = Modifier, launchAction: String? = null, onLaunchHandled: () -> Unit = {}) {
    val data by viewModel.data.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.PAINEL) }
    var inSettings by rememberSaveable { mutableStateOf(false) }
    var route by rememberSaveable { mutableStateOf(SettingsRoute.HUB) }
    var purchaseSheet by remember { mutableStateOf(false) }
    var expenseSheet by remember { mutableStateOf(false) }
    var introOpen by remember { mutableStateOf(false) }       // o voo do Dot que antecede o chat (toda vez que ele abre)
    var onboarding by rememberSaveable { mutableStateOf<Boolean?>(null) }   // null = ainda descobrindo se é a primeira vez
    LaunchedEffect(Unit) {
        val first = viewModel.firstData()
        onboarding = !prefs.onboarded && first.isEmpty
        if (onboarding == false) prefs.markOnboarded()
    }
    // "Apagar tudo" nos ajustes zera o app: volta para o onboarding
    LaunchedEffect(prefs.onboarded) {
        if (!prefs.onboarded && onboarding == false) { onboarding = true; inSettings = false; route = SettingsRoute.HUB; tab = Tab.PAINEL }
    }
    var welcome by rememberSaveable { mutableStateOf(true) }  // a entrada do app, uma vez por abertura
    fun select(t: Tab) { if (t == Tab.DOT && tab != Tab.DOT) introOpen = true else tab = t }
    val design = prefs.design
    val scope = rememberCoroutineScope()

    // o Dot vive em todo o app: um controlador compartilhado (reações) e a conversa (que não se perde ao sair e voltar)
    val dot = remember { DotController() }
    // atalhos vindos dos widgets do Dot
    LaunchedEffect(launchAction) {
        when (launchAction) {
            com.persondot.widget.WidgetAction.EXPENSE -> { inSettings = false; expenseSheet = true }
            com.persondot.widget.WidgetAction.CHAT -> { inSettings = false; select(Tab.DOT) }
        }
        if (launchAction != null) onLaunchHandled()
    }
    val chat = remember { ChatState() }
    LaunchedEffect(dot.bounceKey) { if (dot.bounceKey > 0) { delay(5000); dot.clear() } }

    val immersive = !inSettings && tab == Tab.DOT   // a conversa ocupa a tela toda: sem cabeçalho e sem barra inferior

    fun closeSettings() { if (route != SettingsRoute.HUB) route = SettingsRoute.HUB else inSettings = false }
    BackHandler(enabled = inSettings) { closeSettings() }
    BackHandler(enabled = !inSettings && tab != Tab.PAINEL) { tab = Tab.PAINEL }

    fun go(d: Dest) { inSettings = false; tab = when (d) { Dest.PAINEL, Dest.DECIDIR -> Tab.PAINEL; Dest.GASTOS -> Tab.GASTOS; Dest.DOT -> Tab.DOT; Dest.FUTURO -> Tab.FUTURO }.let { t -> if (t == Tab.DOT && tab != Tab.DOT) { introOpen = true; tab } else t } }
    /** Sair da conversa: o Dot acena e a tela encolhe de volta. */
    fun leaveChat() { scope.launch { dot.react("Até já!", Mood.HAPPY); delay(380); tab = Tab.PAINEL } }

    // ações pedidas ao Dot (só rodam depois de você dizer "sim")
    val runAction: (DotAction) -> Unit = { a ->
        val acc = data.accounts.firstOrNull { it.kind != Account.KIND_IPCA }?.id
        when (a) {
            is DotAction.ConfirmForecast -> data.forecasts(a.month).firstOrNull { it.cost.id == a.fixedId }?.let { viewModel.confirmForecast(it, a.amount, a.accountId) }
            is DotAction.CancelFrom -> data.forecasts(a.month).firstOrNull { it.cost.id == a.fixedId }?.let { viewModel.cancelFrom(it) }
            is DotAction.AddExpense -> viewModel.saveExpense(Expense(desc = a.desc, amount = a.amount, cat = a.cat, date = LocalDate.now().toString()), a.accountId)
            is DotAction.AddFixed -> viewModel.saveFixed(FixedCost(name = a.name, amount = a.amount, grp = a.group, onCard = a.onCard, startsAt = a.startMonth))
            is DotAction.SetFixedAmount -> data.fixed.firstOrNull { it.id == a.fixedId }?.let { viewModel.saveFixed(it.copy(amount = a.amount)) }
            is DotAction.DeleteFixed -> data.fixed.firstOrNull { it.id == a.fixedId }?.let { viewModel.deleteFixed(it) }
            is DotAction.RemoveFromMonth -> data.forecasts(a.month).firstOrNull { it.cost.id == a.fixedId }?.let { viewModel.removeForecast(it) }
            is DotAction.DeleteExpense -> data.expenses.firstOrNull { it.id == a.expenseId }?.let { viewModel.deleteExpense(it) }
            is DotAction.AddReceivable -> viewModel.saveReceivable(Receivable(desc = a.desc, amount = a.amount))
            is DotAction.ReceiveReceivable -> data.receivables.firstOrNull { it.id == a.receivableId }?.let { viewModel.receive(it, it.amount, a.accountId) }
            is DotAction.PayNextInstallment -> viewModel.payNext(a.debtId, a.accountId)
            is DotAction.AddRevenue -> viewModel.saveRevenue(a.month, (data.revenues.firstOrNull { it.month == a.month }?.amount ?: 0.0) + a.amount)
            is DotAction.StartReflection -> viewModel.addReflection(Reflection(
                name = a.name, estimatedPrice = a.price, category = "Outros", isNecessity = false, helpsCarGoal = false, willUseMoreThanOneYear = true,
                wouldBuyCash = a.cash, emotionSource = a.emotion))
            is DotAction.DecideReflection -> data.reflections.firstOrNull { it.id == a.id }?.let { if (a.buy) viewModel.approveReflection(it) else viewModel.cancelReflection(it) }
            is DotAction.AddAccount -> viewModel.saveAccount(Account(name = a.name, balance = a.balance, kind = a.kind))
            is DotAction.SetBalance -> data.accounts.firstOrNull { it.id == a.accountId }?.let { viewModel.saveAccount(it.copy(balance = a.balance)) }
            is DotAction.DeleteAccount -> data.accounts.firstOrNull { it.id == a.accountId }?.let { viewModel.deleteAccount(it) }
            is DotAction.DeleteReceivable -> data.receivables.firstOrNull { it.id == a.receivableId }?.let { viewModel.deleteReceivable(it) }
            is DotAction.SetAporte -> viewModel.saveAporte(a.month, a.amount)
            is DotAction.AddCategory -> viewModel.saveSettings(data.settings.copy(categories = (data.settings.categoryList + a.name).distinct().joinToString("|")))
            is DotAction.RemoveCategory -> viewModel.saveSettings(data.settings.copy(categories = data.settings.categoryList.filter { it != a.name }.ifEmpty { listOf("Outros") }.joinToString("|")))
            is DotAction.ApplyChanges -> viewModel.saveSettings(data.settings.applyChanges(a.changes))
        }
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barInset = if (!immersive && !inSettings) 64.dp + navInset else 0.dp
    CompositionLocalProvider(LocalDot provides dot, LocalBarInset provides barInset) {
        Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                AnimatedVisibility(!immersive, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
                    TopAppBar(
                        navigationIcon = {
                            if (inSettings) IconButton(onClick = { closeSettings() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = MaterialTheme.colorScheme.onBackground) }
                        },
                        title = {
                            Text(if (inSettings) route.title else tab.heading, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        },
                        actions = {
                            val victories = data.reflections.count { it.isCancelled }
                            if (victories > 0 && !inSettings) {
                                Row(
                                    Modifier.padding(end = 4.dp).background(CyberGreen.copy(0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.EmojiEvents, "Vitórias", tint = CyberGreen, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("$victories", color = CyberGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            IconButton(onClick = { prefs.updateDark(!prefs.dark) }, modifier = Modifier.padding(end = 6.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), CircleShape).testTag("theme_toggle_btn")) {
                                Icon(if (prefs.dark) Icons.Filled.LightMode else Icons.Filled.DarkMode, "Alternar tema", tint = if (prefs.dark) Color(0xFFFFD700) else MaterialTheme.colorScheme.onBackground)
                            }
                            if (!inSettings) IconButton(onClick = { inSettings = true; route = SettingsRoute.HUB }, modifier = Modifier.padding(end = 8.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), CircleShape).testTag("settings_btn")) {
                                Icon(Icons.Filled.Settings, "Ajustes", tint = MaterialTheme.colorScheme.onBackground)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onBackground)
                    )
                }
            },
            contentWindowInsets = if (!immersive && !inSettings) WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal) else ScaffoldDefaults.contentWindowInsets,
            containerColor = MaterialTheme.colorScheme.background
        ) { inner ->
            Column(Modifier.fillMaxSize().padding(inner)) {
                // faixa do Dot com dicas (some no Painel, no chat e em Ajustes, onde o Dot já está em destaque)
                val tipScreen = when (tab) { Tab.GASTOS -> TipScreen.GASTOS; Tab.RELATORIOS -> TipScreen.RELATORIOS; Tab.FUTURO -> TipScreen.FUTURO; else -> null }
                if (!inSettings && prefs.tips && tipScreen != null && !data.isEmpty) {
                    val tips = remember(data, tipScreen) { data.tipsFor(tipScreen) }
                    DotStrip(data, design, tips, onOpenChat = { select(Tab.DOT) })
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = Triple(inSettings, route, tab), label = "screen",
                        transitionSpec = {
                            val toChat = !targetState.first && targetState.third == Tab.DOT
                            val fromChat = !initialState.first && initialState.third == Tab.DOT
                            when {
                                // abrir/sair da conversa: só um fade (o voo do Dot é feito pela abertura)
                                toChat || fromChat -> fadeIn(tween(250)) togetherWith fadeOut(tween(150))
                                else -> fadeIn(tween(240)) togetherWith fadeOut(tween(120))
                            }
                        }
                    ) { (settings, r, t) ->
                        if (settings) SettingsScreen(data, viewModel, prefs, r, onRoute = { route = it })
                        else when (t) {
                            Tab.PAINEL -> DashboardScreen(data, viewModel, design, prefs.dotName, onNavigate = ::go, onSimulate = { purchaseSheet = true })
                            Tab.GASTOS -> SpendingScreen(data, viewModel, onSimulate = { purchaseSheet = true })
                            Tab.DOT -> DotChatScreen(data, chat, design, prefs.dotName, runAction,
                                onOpenSettings = { inSettings = true; route = SettingsRoute.DOT }, onExit = { leaveChat() })
                            Tab.RELATORIOS -> ReportsScreen(data, viewModel)
                            Tab.FUTURO -> FutureScreen(data, viewModel)
                        }
                    }
                }
            }
        }
                AnimatedVisibility(!immersive && !inSettings, modifier = Modifier.align(Alignment.BottomCenter), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                    val entries = Tab.entries.filter { it.active != null }.map { t ->
                        NavEntry(t.title, if (tab == t) t.active!! else t.inactive!!, tab == t, "nav_${t.name.lowercase()}") { select(t) }
                    }
                    DotNavBar(
                        left = entries.take(2), right = entries.drop(2), accent = design.color, centerSelected = tab == Tab.DOT,
                        onCenter = { select(Tab.DOT) }, modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        DotAvatar(design, size = 46.dp, mood = dot.mood ?: moodFor(data), bounceKey = dot.bounceKey, animate = tab == Tab.DOT, groundShadow = false)
                    }
                }
        }

        if (purchaseSheet) PurchaseSheet(data) { purchaseSheet = false }
        if (expenseSheet) com.persondot.ui.components.ExpenseDialog(null, data.accounts, data.settings.categoryList, { e, acc -> viewModel.saveExpense(e, acc) }, null) { expenseSheet = false }
        if (introOpen) DotIntroOverlay(design, dot.mood ?: moodFor(data), onCovered = { inSettings = false; tab = Tab.DOT }) { introOpen = false }
        if (onboarding == true) OnboardingFlow(prefs, viewModel) { onboarding = false }
        if (welcome) WelcomeOverlay(design, prefs.dotName) { welcome = false }
    }
}
