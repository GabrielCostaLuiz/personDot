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
import com.persondot.data.model.Account
import com.persondot.data.model.Expense
import com.persondot.domain.*
import com.persondot.ui.UiPrefs
import com.persondot.ui.dot.*
import com.persondot.ui.theme.*
import com.persondot.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class Tab(val title: String, val heading: String, val active: ImageVector?, val inactive: ImageVector?) {
    PAINEL("Painel", "Painel de Controle", Icons.Filled.Speed, Icons.Outlined.Speed),
    GASTOS("Gastos", "Gastos e previsões", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    DOT("Dot", "Conversa com o Dot", null, null),   // o ícone é o próprio Dot
    DECIDIR("Decidir", "Posso comprar?", Icons.Filled.Psychology, Icons.Outlined.Psychology),
    FUTURO("Futuro", "Evoluir", Icons.Filled.TrendingUp, Icons.Outlined.TrendingUp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: FinanceViewModel, prefs: UiPrefs, modifier: Modifier = Modifier) {
    val data by viewModel.data.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.PAINEL) }
    var inSettings by rememberSaveable { mutableStateOf(false) }
    var route by rememberSaveable { mutableStateOf(SettingsRoute.HUB) }
    val design = prefs.design
    val scope = rememberCoroutineScope()

    // o Dot vive em todo o app: um controlador compartilhado (reações) e a conversa (que não se perde ao sair e voltar)
    val dot = remember { DotController() }
    val chat = remember { ChatState() }
    LaunchedEffect(dot.bounceKey) { if (dot.bounceKey > 0) { delay(5000); dot.clear() } }

    val immersive = !inSettings && tab == Tab.DOT   // a conversa ocupa a tela toda: sem cabeçalho e sem barra inferior

    fun closeSettings() { if (route != SettingsRoute.HUB) route = SettingsRoute.HUB else inSettings = false }
    BackHandler(enabled = inSettings) { closeSettings() }
    BackHandler(enabled = !inSettings && tab != Tab.PAINEL) { tab = Tab.PAINEL }

    fun go(d: Dest) { inSettings = false; tab = when (d) { Dest.PAINEL -> Tab.PAINEL; Dest.GASTOS -> Tab.GASTOS; Dest.DOT -> Tab.DOT; Dest.DECIDIR -> Tab.DECIDIR; Dest.FUTURO -> Tab.FUTURO } }
    /** Sair da conversa: o Dot acena e a tela encolhe de volta. */
    fun leaveChat() { scope.launch { dot.react("Até já!", Mood.HAPPY); delay(380); tab = Tab.PAINEL } }

    // ações pedidas pelo chat (só rodam depois de você dizer "sim")
    val runAction: (DotAction) -> Unit = { a ->
        val acc = data.accounts.firstOrNull { it.kind != Account.KIND_IPCA }?.id
        when (a) {
            is DotAction.ConfirmForecast -> data.forecasts(a.month).firstOrNull { it.cost.id == a.fixedId }?.let { viewModel.confirmForecast(it, a.amount, acc) }
            is DotAction.CancelFrom -> data.forecasts(a.month).firstOrNull { it.cost.id == a.fixedId }?.let { viewModel.cancelFrom(it) }
            is DotAction.AddExpense -> viewModel.saveExpense(Expense(desc = a.desc, amount = a.amount, cat = a.cat, date = LocalDate.now().toString()), acc)
        }
    }

    CompositionLocalProvider(LocalDot provides dot) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                AnimatedVisibility(!immersive, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
                    TopAppBar(
                        navigationIcon = {
                            if (inSettings) IconButton(onClick = { closeSettings() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = MaterialTheme.colorScheme.onBackground) }
                        },
                        title = {
                            Column {
                                Text("PERSONDOT", color = CyberGreen, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp, fontWeight = FontWeight.Black)
                                Text(if (inSettings) route.title else tab.heading, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
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
                            IconButton(onClick = { prefs.updateDark(!prefs.dark) }, modifier = Modifier.testTag("theme_toggle_btn")) {
                                Icon(if (prefs.dark) Icons.Filled.LightMode else Icons.Filled.DarkMode, "Alternar tema", tint = if (prefs.dark) Color(0xFFFFD700) else MaterialTheme.colorScheme.onBackground)
                            }
                            if (!inSettings) IconButton(onClick = { inSettings = true; route = SettingsRoute.HUB }, modifier = Modifier.testTag("settings_btn")) {
                                Icon(Icons.Filled.Settings, "Ajustes", tint = MaterialTheme.colorScheme.onBackground)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onBackground)
                    )
                }
            },
            bottomBar = {
                AnimatedVisibility(!immersive && !inSettings, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp, modifier = Modifier.testTag("bottom_nav_bar")) {
                        Tab.entries.forEach { t ->
                            val selected = tab == t
                            NavigationBarItem(
                                selected = selected, onClick = { tab = t },
                                icon = {
                                    if (t.active == null) DotAvatar(design, size = 30.dp, mood = dot.mood ?: moodFor(data), bounceKey = dot.bounceKey, animate = selected)
                                    else Icon(if (selected) t.active else t.inactive!!, t.title, tint = if (selected) CyberGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                label = { Text(t.title, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                                modifier = Modifier.testTag("nav_${t.name.lowercase()}")
                            )
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { inner ->
            Column(Modifier.fillMaxSize().padding(inner)) {
                // faixa do Dot com dicas (some no Painel, no chat e em Ajustes, onde o Dot já está em destaque)
                val tipScreen = when (tab) { Tab.GASTOS -> TipScreen.GASTOS; Tab.DECIDIR -> TipScreen.DECIDIR; Tab.FUTURO -> TipScreen.FUTURO; else -> null }
                if (!inSettings && prefs.tips && tipScreen != null && !data.isEmpty) {
                    val tips = remember(data, tipScreen) { data.tipsFor(tipScreen) }
                    DotStrip(data, design, tips, onOpenChat = { tab = Tab.DOT })
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = Triple(inSettings, route, tab), label = "screen",
                        transitionSpec = {
                            val toChat = !targetState.first && targetState.third == Tab.DOT
                            val fromChat = !initialState.first && initialState.third == Tab.DOT
                            when {
                                // abrir a conversa: o Dot "se abre" a partir da base da tela
                                toChat -> (scaleIn(tween(380), initialScale = 0.82f, transformOrigin = TransformOrigin(0.5f, 1f)) + fadeIn(tween(300))) togetherWith fadeOut(tween(140))
                                // sair: a conversa encolhe de volta
                                fromChat -> fadeIn(tween(260)) togetherWith (scaleOut(tween(300), targetScale = 0.82f, transformOrigin = TransformOrigin(0.5f, 1f)) + fadeOut(tween(220)))
                                else -> fadeIn(tween(240)) togetherWith fadeOut(tween(120))
                            }
                        }
                    ) { (settings, r, t) ->
                        if (settings) SettingsScreen(data, viewModel, prefs, r, onRoute = { route = it })
                        else when (t) {
                            Tab.PAINEL -> DashboardScreen(data, viewModel, design, prefs.dotName, onNavigate = ::go)
                            Tab.GASTOS -> SpendingScreen(data, viewModel)
                            Tab.DOT -> DotChatScreen(data, chat, design, prefs.dotName, runAction,
                                onCustomize = { inSettings = true; route = SettingsRoute.DOT }, onExit = { leaveChat() })
                            Tab.DECIDIR -> DecideScreen(data, viewModel)
                            Tab.FUTURO -> FutureScreen(data, viewModel)
                        }
                    }
                }
            }
        }
    }
}
