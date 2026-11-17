package com.persondot.ui.dot

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.persondot.domain.FinanceData
import com.persondot.ui.theme.*
import kotlinx.coroutines.delay

/**
 * O "estado emocional" do Dot compartilhado pelo app inteiro. Qualquer tela chama [react] depois de
 * uma ação (confirmar um pagamento, cortar um custo...) e o Dot pula, muda de humor e comenta.
 */
@Stable
class DotController {
    var message by mutableStateOf<String?>(null); private set
    var mood by mutableStateOf<Mood?>(null); private set
    var bounceKey by mutableIntStateOf(0); private set

    fun react(text: String?, mood: Mood = Mood.HAPPY) { message = text; this.mood = mood; bounceKey++ }
    fun clear() { message = null; mood = null }
}

val LocalDot = staticCompositionLocalOf { DotController() }

/**
 * Faixa fina do Dot no topo das telas: ele flutua, pisca e solta dicas que se renovam.
 * Fica pequena de propósito, para não roubar o foco do que você está fazendo.
 */
@Composable
fun DotStrip(data: FinanceData, design: DotDesign, tips: List<String>, onOpenChat: () -> Unit, modifier: Modifier = Modifier) {
    val dot = LocalDot.current
    var idx by remember(tips) { mutableIntStateOf(0) }
    LaunchedEffect(dot.bounceKey) { if (dot.message != null) { delay(4500); dot.clear() } }
    LaunchedEffect(tips, dot.message) {
        while (dot.message == null && tips.size > 1) { delay(9000); idx = (idx + 1) % tips.size }
    }
    val text = dot.message ?: tips.getOrNull(if (tips.isEmpty()) 0 else idx % tips.size) ?: ""
    if (text.isEmpty()) return

    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(20.dp))
            .border(1.dp, CardBorderGlance, RoundedCornerShape(20.dp)).padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DotAvatar(design, size = 46.dp, mood = dot.mood ?: moodFor(data), bounceKey = dot.bounceKey, onClick = onOpenChat)
        Spacer(Modifier.width(8.dp))
        AnimatedContent(
            targetState = text, modifier = Modifier.weight(1f).clickable(enabled = dot.message == null && tips.size > 1) { idx = (idx + 1) % tips.size },
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(160)) }, label = "tip"
        ) { t -> Text(t, color = AppTextWhite, style = MaterialTheme.typography.bodySmall, maxLines = 3) }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Outlined.Chat, "Conversar com o Dot", tint = CyberGreen, modifier = Modifier.size(20.dp).clickable { onOpenChat() })
    }
}
