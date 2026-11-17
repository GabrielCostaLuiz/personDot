package com.persondot.ui.screens

import androidx.activity.compose.BackHandler
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
import com.persondot.ui.dot.DotAvatar
import com.persondot.ui.dot.DotScreen
import com.persondot.ui.dot.dotById
import com.persondot.ui.dot.moodFor
import com.persondot.ui.theme.*
import com.persondot.ui.viewmodel.FinanceViewModel

private enum class Tab(val title: String, val heading: String, val active: ImageVector, val inactive: ImageVector) {
    PAINEL("Painel", "Painel de Controle", Icons.Filled.Speed, Icons.Outlined.Speed),
    GASTOS("Gastos", "Gastos do mês", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    DECIDIR("Decidir", "Posso comprar?", Icons.Filled.Psychology, Icons.Outlined.Psychology),
    FUTURO("Futuro", "Dívidas e fluxo", Icons.Filled.CreditCard, Icons.Outlined.CreditCard),
    METAS("Metas", "Contas riscadas e metas", Icons.Filled.Flag, Icons.Outlined.Flag)
}

/** Telas que ficam por cima das abas. */
private enum class Overlay { NONE, DOT, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: FinanceViewModel, isDarkTheme: Boolean, onToggleTheme: () -> Unit,
    dotId: String, dotName: String, onDotChange: (String) -> Unit, onDotNameChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val data by viewModel.data.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.PAINEL) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }
    BackHandler(enabled = overlay != Overlay.NONE) { overlay = Overlay.NONE }
    val design = dotById(dotId)

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
                            when (overlay) { Overlay.DOT -> "Meu Dot"; Overlay.SETTINGS -> "Ajustes"; Overlay.NONE -> tab.heading },
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
                    if (overlay != Overlay.DOT) IconButton(onClick = { overlay = Overlay.DOT }, modifier = Modifier.testTag("dot_btn")) {
                        DotAvatar(design, size = 34.dp, mood = moodFor(data), animate = false)
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
                        icon = { Icon(if (selected) t.active else t.inactive, t.title, tint = if (selected) CyberGreen else MaterialTheme.colorScheme.onSurfaceVariant) },
                        label = { Text(t.title, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                        modifier = Modifier.testTag("nav_${t.name.lowercase()}")
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (overlay) {
                Overlay.SETTINGS -> SettingsScreen(data, viewModel)
                Overlay.DOT -> DotScreen(data, dotId, dotName, onDotChange, onDotNameChange)
                Overlay.NONE -> when (tab) {
                    Tab.PAINEL -> DashboardScreen(
                        data, viewModel, dotId = dotId, dotName = dotName, onOpenDot = { overlay = Overlay.DOT },
                        onGoSettings = { overlay = Overlay.SETTINGS }, onGoFuture = { tab = Tab.FUTURO }
                    )
                    Tab.GASTOS -> SpendingScreen(data, viewModel)
                    Tab.DECIDIR -> DecideScreen(data, viewModel)
                    Tab.FUTURO -> FutureScreen(data, viewModel)
                    Tab.METAS -> GoalsScreen(data, viewModel)
                }
            }
        }
    }
}
