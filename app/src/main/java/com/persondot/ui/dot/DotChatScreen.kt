package com.persondot.ui.dot

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.persondot.domain.*
import com.persondot.ui.screens.textFieldDarkColors
import com.persondot.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ChatMessage(val id: Long, val fromUser: Boolean, val text: String, val tool: String? = null)

/** Conversa do Dot. Fica fora da tela para não se perder ao trocar de aba. */
@Stable
class ChatState {
    val messages = mutableStateListOf<ChatMessage>()
    var pending by mutableStateOf<DotAction?>(null)
    var thinking by mutableStateOf(false)
    var quick by mutableStateOf<List<String>>(emptyList())
    private var seq = 0L
    fun nextId() = ++seq
}

/** Chat com o Dot: você pergunta em português e ele chama as ferramentas do app para responder. */
@Composable
fun DotChatScreen(
    data: FinanceData, state: ChatState, design: DotDesign, dotName: String,
    onRun: (DotAction) -> Unit, onCustomize: () -> Unit, modifier: Modifier = Modifier
) {
    val dot = LocalDot.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || state.thinking) return
        state.messages += ChatMessage(state.nextId(), true, text)
        state.quick = emptyList(); input = ""
        state.thinking = true
        scope.launch {
            delay(650)   // o Dot "pensa" um instante, como numa conversa
            val reply = DotBrain.reply(text, data, state.pending)
            state.thinking = false
            state.pending = reply.pending
            state.messages += ChatMessage(state.nextId(), false, reply.text, reply.tool)
            state.quick = reply.quick
            reply.run?.let(onRun)
            dot.react(null, when (reply.level) { Level.OK -> Mood.HAPPY; Level.WARN -> Mood.NEUTRAL; Level.BAD -> Mood.WORRIED; null -> Mood.HAPPY })
        }
    }

    LaunchedEffect(Unit) {
        if (state.messages.isEmpty()) {
            val hello = DotBrain.reply("oi", data)
            state.messages += ChatMessage(state.nextId(), false, hello.text)
            state.quick = hello.quick
        }
    }
    LaunchedEffect(state.messages.size, state.thinking) {
        val last = state.messages.size + (if (state.thinking) 1 else 0) - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        // cabeçalho: o Dot em pessoa
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            DotAvatar(design, size = 76.dp, mood = dot.mood ?: moodFor(data), bounceKey = dot.bounceKey, thinking = state.thinking, onClick = { dot.react(null, Mood.HAPPY) })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(dotName, color = AppTextWhite, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(if (state.thinking) "pensando…" else "pergunte com suas palavras", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onCustomize) { Text("Meu Dot", color = CyberGreen, fontWeight = FontWeight.Bold) }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.messages, key = { it.id }) { m -> Bubble(m, design) }
            if (state.thinking) item(key = "thinking") { TypingBubble(design) }
        }

        if (state.quick.isNotEmpty() && !state.thinking) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                items(state.quick) { q ->
                    AssistChip(onClick = { send(q) }, label = { Text(q) }, colors = AssistChipDefaults.assistChipColors(labelColor = AppTextWhite), border = AssistChipDefaults.assistChipBorder(true, borderColor = CardBorderGlance))
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 10.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), singleLine = true,
                placeholder = { Text("Fale com o $dotName…") }, shape = RoundedCornerShape(24.dp), colors = textFieldDarkColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send(input) })
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = { send(input) }, enabled = input.isNotBlank() && !state.thinking, colors = IconButtonDefaults.filledIconButtonColors(containerColor = CyberGreen, contentColor = Color.Black)) {
                Icon(Icons.AutoMirrored.Filled.Send, "Enviar")
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage, design: DotDesign) {
    if (m.fromUser) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)).background(CyberGreen).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(m.text, color = Color.Black, style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            DotAvatar(design, size = 30.dp, animate = false, modifier = Modifier.padding(end = 6.dp))
            Column(Modifier.widthIn(max = 310.dp)) {
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
