package com.persondot.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.persondot.data.model.Account
import com.persondot.data.model.Expense
import com.persondot.domain.*
import com.persondot.ui.dot.*
import com.persondot.ui.theme.*
import com.persondot.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate

private enum class Tab(val title: String, val heading: String, val active: ImageVector?, val inactive: ImageVector?) {
    PAINEL("Painel", "Painel de Controle", Icons.Filled.Speed, Icons.Outlined.Speed),
    GASTOS("Gastos", "Gastos e previsões", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    DOT("Dot", "Conversa com o Dot", null, null),   // o ícone é o próprio Dot
    DECIDIR("Decidir", "Posso comprar?", Icons.Filled.Psychology, Icons.Outlined.Psychology),
    FUTURO("Futuro", "Evoluir", Icons.Filled.TrendingUp, Icons.Outlined.TrendingUp)
}

/** Telas que ficam por cima das abas. */
private enum class Overlay { NONE, MYDOT, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: FinanceViewModel, isDarkTheme: Boolean, onToggleTheme: () -> Unit,
    dotId: String, dotName: String, onDotChange: (String) -> Unit, onDotNameChange: (String) -> Unit,
    tipsEnabled: Boolean, onTipsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val data by viewModel.data.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.PAINEL) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }
    BackHandler(enabled = overlay != Overlay.NONE) { overlay = Overlay.NONE }
    BackHandler(enabled = overlay == Overlay.NONE && tab != Tab.PAINEL) { tab = Tab.PAINEL }
    val design = dotById(dotId)

    // o Dot vive em todo o app: um controlador compartilhado (reações) e a conversa (que não se perde ao trocar de aba)
    val dot = remember { DotController() }
    val chat = remember { ChatState() }
    LaunchedEffect(dot.bounceKey) { if (dot.bounceKey > 0) { delay(5000); dot.clear() } }

    fun go(d: Dest) { overlay = Overlay.NONE; tab = when (d) { Dest.PAINEL -> Tab.PAINEL; Dest.GASTOS -> Tab.GASTOS; Dest.DOT -> Tab.DOT; Dest.DECIDIR -> Tab.DECIDIR; Dest.FUTURO -> Tab.FUTURO } }

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
                TopAppBar(
                    navigationIcon = {
                        if (overlay != Overlay.NONE) IconButton(onClick = { overlay = Overlay.NONE }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = MaterialTheme.colorScheme.onBackground) }
                    },
                    title = {
                        Column {
                            Text("PERSONDOT", color = CyberGreen, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp, fontWeight = FontWeight.Black)
                            Text(
                                when (overlay) { Overlay.MYDOT -> "Meu Dot"; Overlay.SETTINGS -> "Ajustes"; Overlay.NONE -> tab.heading },
                                color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        val victories = data.reflections.count { it.isCancelled }
                        if (victories > 0 && overlay == Overlay.NONE) {
                            Row(
                                Modifier.padding(end = 4.dp).background(CyberGreen.copy(0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.EmojiEvents, "Vitórias", tint = CyberGreen, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("$victories", color = CyberGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(onClick = onToggleTheme, modifier = Modifier.testTag("theme_toggle_btn")) {
                            Icon(if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode, "Alternar tema",
                                tint = if (isDarkTheme) Color(0xFFFFD700) else MaterialTheme.colorScheme.onBackground)
                        }
                        if (overlay == Overlay.NONE) IconButton(onClick = { overlay = Overlay.SETTINGS }, modifier = Modifier.testTag("settings_btn")) {
                            Icon(Icons.Filled.Settings, "Ajustes", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onBackground)
                )
            },
            bottomBar = {
                if (overlay == Overlay.NONE) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp, modifier = Modifier.testTag("bottom_nav_bar")) {
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
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { inner ->
            Column(Modifier.fillMaxSize().padding(inner)) {
                // faixa do Dot com dicas (some no Painel e no chat, que já têm o Dot em destaque)
                val tipScreen = when (tab) { Tab.GASTOS -> TipScreen.GASTOS; Tab.DECIDIR -> TipScreen.DECIDIR; Tab.FUTURO -> TipScreen.FUTURO; else -> null }
                if (overlay == Overlay.NONE && tipsEnabled && tipScreen != null && !data.isEmpty) {
                    val tips = remember(data, tipScreen) { data.tipsFor(tipScreen) }
                    DotStrip(data, design, tips, onOpenChat = { tab = Tab.DOT })
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = overlay to tab, label = "screen",
                        transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(120)) }
                    ) { (ov, t) ->
                        when (ov) {
                            Overlay.SETTINGS -> SettingsScreen(data, viewModel, tipsEnabled, onTipsChange)
                            Overlay.MYDOT -> DotScreen(data, dotId, dotName, { onDotChange(it); dot.react("Gostei! Agora eu sou assim.", Mood.HAPPY) }, onDotNameChange)
                            Overlay.NONE -> when (t) {
                                Tab.PAINEL -> DashboardScreen(data, viewModel, dotId, dotName, onNavigate = ::go)
                                Tab.GASTOS -> SpendingScreen(data, viewModel)
                                Tab.DOT -> DotChatScreen(data, chat, design, dotName, runAction, onCustomize = { overlay = Overlay.MYDOT })
                                Tab.DECIDIR -> DecideScreen(data, viewModel)
                                Tab.FUTURO -> FutureScreen(data, viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}
