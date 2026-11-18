package com.persondot.ui.dot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.persondot.ui.theme.DarkBackground
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * A abertura da conversa (só na primeira vez da sessão): o Dot sai do ícone da barra inferior, sobe até o centro
 * da tela crescendo, e some para revelar a conversa.
 */
@Composable
fun DotIntroOverlay(design: DotDesign, mood: Mood, onCovered: () -> Unit, onFinished: () -> Unit) {
    val density = LocalDensity.current
    val cfg = LocalConfiguration.current
    val wPx = with(density) { cfg.screenWidthDp.dp.toPx() }
    val hPx = with(density) { cfg.screenHeightDp.dp.toPx() }
    val background = DarkBackground
    val move = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        move.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        onCovered()                                  // a conversa só aparece agora, por trás do Dot
        delay(350)                                   // o Dot fica um instante no meio
        fade.animateTo(0f, tween(300))
        onFinished()
    }

    val sizeDp = lerp(30.dp, 220.dp, move.value)
    val sizePx = with(density) { sizeDp.toPx() }
    val startY = hPx - with(density) { 50.dp.toPx() }   // onde fica o ícone do Dot na barra
    val endY = hPx * 0.40f
    val y = startY + (endY - startY) * move.value

    Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
        Canvas(Modifier.fillMaxSize()) { drawRect(background.copy(alpha = move.value)) }
        DotAvatar(design, size = sizeDp, mood = mood, modifier = Modifier.offset { IntOffset((wPx / 2 - sizePx / 2).roundToInt(), (y - sizePx / 2).roundToInt()) })
    }
}

/**
 * A entrada do app: a tela de abertura do Android já é escura e vazia, então o Dot (o da pessoa) aparece no meio,
 * dá um pulo feliz e dá boas-vindas; depois a tela abre sobre o Painel. Sempre escura, para casar com a abertura do sistema.
 */
@Composable
fun WelcomeOverlay(design: DotDesign, name: String, onFinished: () -> Unit) {
    val appear = remember { Animatable(0.88f) }
    val hello = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    var bounce by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        appear.animateTo(1f, androidx.compose.animation.core.spring(0.55f, 380f))
        bounce++                                      // o pulinho feliz
        hello.animateTo(1f, tween(450))
        delay(1300)
        fade.animateTo(0f, tween(400))
        onFinished()
    }

    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Canvas(Modifier.fillMaxSize()) { drawRect(androidx.compose.ui.graphics.Color(0xFF0A0A0A)) }
        androidx.compose.foundation.layout.Column(
            Modifier.align(androidx.compose.ui.Alignment.Center).offset(y = (-40).dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            DotAvatar(design, size = 190.dp, mood = Mood.HAPPY, bounceKey = bounce, modifier = Modifier.graphicsLayer { scaleX = appear.value; scaleY = appear.value })
            androidx.compose.foundation.layout.Spacer(Modifier.height(28.dp))
            androidx.compose.material3.Text(
                "Bem-vindo!", color = androidx.compose.ui.graphics.Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                modifier = Modifier.graphicsLayer { alpha = hello.value }
            )
            androidx.compose.material3.Text(
                "Eu sou o $name. Bora lá?", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                modifier = Modifier.graphicsLayer { alpha = hello.value }
            )
        }
    }
}
