@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.persondot.ui.dot

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.persondot.domain.*
import com.persondot.ui.components.PrimaryButton
import com.persondot.ui.components.SoftButton
import com.persondot.ui.components.parseMoney
import com.persondot.ui.screens.textFieldDarkColors
import com.persondot.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ChatMessage(val id: Long, val fromUser: Boolean, val text: String, val tool: String? = null, val detailOf: Draft? = null)

/** Conversa e pergunta em construção. Ficam fora da tela para não se perderem ao sair e voltar. */
@Stable
class ChatState {
    val messages = mutableStateListOf<ChatMessage>()
    val blocks = mutableStateListOf<Block>()
    var pending by mutableStateOf<DotAction?>(null)
    var thinking by mutableStateOf(false)
    var detailed by mutableStateOf(false)
    private var seq = 0L
    fun nextId() = ++seq
}

/**
 * A conversa com o Dot em tela cheia. Você não digita: monta a pergunta em blocos (tema → variações → valores) e
 * o Dot responde chamando a ferramenta certa. O modo "Direto" dá a conclusão; o "Detalhado", os números.
 */
@Composable
fun DotChatScreen(
    data: FinanceData, state: ChatState, design: DotDesign, dotName: String,
    onRun: (DotAction) -> Unit, onCustomize: () -> Unit, onExit: () -> Unit, modifier: Modifier = Modifier
) {
    val dot = LocalDot.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // o Dot "abre" a conversa: entra pulando e crescendo
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { dot.react(null, Mood.HAPPY); pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 220f)) }

    fun deliver(reply: BrainReply, draft: Draft?) {
        state.thinking = false
        state.pending = reply.pending
        state.messages += ChatMessage(state.nextId(), false, reply.text, reply.tool, detailOf = if (reply.hasMore) draft else null)
        reply.run?.let(onRun)
        dot.react(null, when (reply.level) { Level.OK -> Mood.HAPPY; Level.WARN -> Mood.NEUTRAL; Level.BAD -> Mood.WORRIED; null -> Mood.HAPPY })
    }

    fun ask(draft: Draft, detailedNow: Boolean, echo: String?) {
        if (state.thinking) return
        if (echo != null) state.messages += ChatMessage(state.nextId(), true, echo)
        state.blocks.clear()
        state.thinking = true
        scope.launch { delay(550); deliver(DotBrain.answer(draft, data, detailedNow), draft) }
    }

    fun answerPending(yes: Boolean) {
        val p = state.pending ?: return
        state.messages += ChatMessage(state.nextId(), true, if (yes) "Sim" else "Não")
        state.pending = null; state.thinking = true
        scope.launch { delay(400); deliver(if (yes) DotBrain.confirm(p) else DotBrain.decline(), null) }
    }

    LaunchedEffect(Unit) { if (state.messages.isEmpty()) state.messages += ChatMessage(state.nextId(), false, DotBrain.greeting().text) }
    LaunchedEffect(state.messages.size, state.thinking) {
        val count = state.messages.size + (if (state.thinking) 1 else 0)
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        // cabeçalho próprio: sair, o Dot, o modo de resposta e personalizar
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) { Icon(Icons.Filled.Close, "Sair da conversa", tint = AppTextWhite) }
            DotAvatar(design, size = 66.dp, mood = dot.mood ?: moodFor(data), bounceKey = dot.bounceKey, thinking = state.thinking, modifier = Modifier.scale(pop.value), onClick = { dot.react(null, Mood.HAPPY) })
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(dotName, color = AppTextWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(if (state.thinking) "pensando…" else "monte sua pergunta", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onCustomize) { Icon(Icons.Outlined.Palette, "Meu Dot", tint = CyberGreen) }
        }
        // modo de resposta
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Respostas", color = SecondaryText, style = MaterialTheme.typography.labelMedium)
            listOf(false to "Diretas", true to "Detalhadas").forEach { (v, label) ->
                FilterChip(
                    selected = state.detailed == v, onClick = { state.detailed = v }, label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyberGreen, selectedLabelColor = Color.Black)
                )
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.messages, key = { it.id }) { m -> Bubble(m, design) }
            if (state.thinking) item(key = "thinking") { TypingBubble(design) }
            val last = state.messages.lastOrNull()
            if (!state.thinking && last != null && !last.fromUser && last.detailOf != null) item(key = "more") {
                AssistChip(
                    onClick = { ask(last.detailOf, true, "Mais detalhes") }, label = { Text("Mais detalhes") },
                    colors = AssistChipDefaults.assistChipColors(labelColor = CyberGreen), border = AssistChipDefaults.assistChipBorder(true, borderColor = CyberGreen)
                )
            }
        }

        BuilderPanel(state, data, onAsk = { val d = state.blocks.toDraft(); ask(d, state.detailed, d.sentence()) }, onAnswerPending = ::answerPending)
    }
}

/** O painel onde a pergunta é construída, bloco a bloco. */
@Composable
private fun BuilderPanel(state: ChatState, data: FinanceData, onAsk: () -> Unit, onAnswerPending: (Boolean) -> Unit) {
    val draft = state.blocks.toDraft()
    val step = nextStep(draft, data)
    var amountText by remember(step.input?.field, state.blocks.size) { mutableStateOf("") }
    var extraInput by remember(state.blocks.size) { mutableStateOf<AmountInput?>(null) }
    val input = extraInput ?: step.input

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(DarkSurface)
            .border(1.dp, CardBorderGlance, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.pending != null) {
            Text("Posso fazer isso?", color = SecondaryText, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Sim", { onAnswerPending(true) }, Modifier.weight(1f))
                SoftButton("Não", { onAnswerPending(false) }, Modifier.weight(1f))
            }
            return@Column
        }

        // a pergunta até agora
        if (state.blocks.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.blocks.size) { i ->
                        Box(Modifier.clip(RoundedCornerShape(50)).background(CyberGreen.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                            Text(state.blocks[i].label, color = CyberGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
                TextButton(onClick = { state.blocks.removeAt(state.blocks.lastIndex) }) { Text("desfazer", color = SecondaryText) }
            }
        }

        Text(if (extraInput != null) extraInput!!.label else step.prompt, color = AppTextWhite, fontWeight = FontWeight.SemiBold)

        if (extraInput == null && step.options.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                step.options.forEach { b ->
                    AssistChip(
                        onClick = { state.blocks += b }, label = { Text(b.label) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = AppTextWhite), border = AssistChipDefaults.assistChipBorder(true, borderColor = CardBorderGlance)
                    )
                }
            }
        }

        if (input != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it }, modifier = Modifier.weight(1f), singleLine = true,
                    label = { Text(input.label) }, placeholder = { Text(input.hint) }, colors = textFieldDarkColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                val v = parseMoney(amountText)
                FilledIconButton(
                    onClick = { state.blocks += AmountBlock(input.field, v); extraInput = null },
                    enabled = v > 0, colors = IconButtonDefaults.filledIconButtonColors(containerColor = CyberGreen, contentColor = Color.Black)
                ) { Text("OK", fontWeight = FontWeight.Bold) }
            }
        }

        if (step.complete) {
            if (extraInput == null && step.extras.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Acrescentar:", color = SecondaryText, style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterVertically))
                    step.extras.forEach { e ->
                        AssistChip(
                            onClick = { if (e.block != null) state.blocks += e.block else extraInput = e.input }, label = { Text("+ ${e.label}") },
                            colors = AssistChipDefaults.assistChipColors(labelColor = CyberTeal), border = AssistChipDefaults.assistChipBorder(true, borderColor = CyberTeal)
                        )
                    }
                }
            }
            PrimaryButton(if (draft.topic == Topic.REGISTRAR) "Registrar" else "Perguntar", onAsk, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage, design: DotDesign) {
    if (m.fromUser) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(Modifier.widthIn(max = 310.dp).clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)).background(CyberGreen).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(m.text, color = Color.Black, style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            DotAvatar(design, size = 30.dp, animate = false, modifier = Modifier.padding(end = 6.dp))
            Column(Modifier.widthIn(max = 320.dp)) {
                Box(Modifier.clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(m.text, color = AppTextWhite, style = MaterialTheme.typography.bodyMedium)
                }
                if (m.tool != null) Text("ferramenta: ${m.tool}", color = SecondaryText, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp, top = 2.dp))
            }
        }
    }
}

@Composable
private fun TypingBubble(design: DotDesign) {
    val t = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.Bottom) {
        DotAvatar(design, size = 30.dp, animate = false, modifier = Modifier.padding(end = 6.dp))
        Row(Modifier.clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)).padding(horizontal = 14.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) { i ->
                val s by t.animateFloat(0.5f, 1f, infiniteRepeatable(tween(450, delayMillis = i * 150, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d$i")
                Box(Modifier.size(8.dp).scale(s).clip(CircleShape).background(SecondaryText))
            }
        }
    }
}
