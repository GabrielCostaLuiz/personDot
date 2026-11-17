package com.persondot.ui.dot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.persondot.domain.*
import com.persondot.ui.components.*
import com.persondot.ui.theme.*
import java.time.LocalTime

/** O humor do Dot acompanha a saúde financeira. */
fun moodFor(data: FinanceData): Mood {
    if (data.isEmpty) return Mood.SLEEPY
    val score = data.health().score
    return if (score >= 75) Mood.HAPPY else if (score >= 50) Mood.NEUTRAL else Mood.WORRIED
}

private fun greeting(): String {
    val h = LocalTime.now().hour
    return if (h < 12) "Bom dia" else if (h < 18) "Boa tarde" else "Boa noite"
}

/** Tela "Meu Dot": o Dot grande com o que ele tem a dizer, a escolha do avatar e o que vem por aí. */
@Composable
fun DotScreen(
    data: FinanceData, dotId: String, dotName: String,
    onDotChange: (String) -> Unit, onNameChange: (String) -> Unit, modifier: Modifier = Modifier
) {
    val design = dotById(dotId)
    val mood = remember(data) { moodFor(data) }
    var nameDialog by remember { mutableStateOf(false) }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)) {
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brush.verticalGradient(listOf(design.color.copy(alpha = 0.28f), DarkSurface)))
                    .border(1.dp, CardBorderGlance, RoundedCornerShape(28.dp)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DotAvatar(design, size = 210.dp, mood = mood)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { nameDialog = true }) {
                    Text(dotName, color = AppTextWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(8.dp))
                    Text("editar", color = CyberGreen, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    when (mood) { Mood.HAPPY -> "feliz com as suas finanças"; Mood.NEUTRAL -> "de olho nas suas finanças"; Mood.WORRIED -> "preocupado com as suas finanças"; Mood.SLEEPY -> "esperando seus dados" },
                    color = SecondaryText, style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(14.dp))
                SpeechBubble(data, dotName)
            }
        }

        item {
            BentoCard {
                SectionHeader("Escolha o seu Dot")
                Text("Toque em um para ele virar o seu Dot pessoal.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                DotCatalog.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { d ->
                            val selected = d.id == design.id
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                                    .background(if (selected) CyberGreen.copy(alpha = 0.10f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                                    .border(if (selected) BorderStroke(2.dp, CyberGreen) else BorderStroke(0.5.dp, BorderColor), RoundedCornerShape(18.dp))
                                    .clickable { onDotChange(d.id) }.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                DotAvatar(d, size = 78.dp, mood = Mood.NEUTRAL, animate = false)
                                Text(d.name, color = if (selected) CyberGreen else AppTextWhite, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        item {
            BentoCard {
                SectionHeader("O que o Dot vai aprender")
                Text("O PersonDot vai ganhar novos módulos, todos conversando com o seu Dot. Isto é o plano, ainda não está pronto:", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                RoadmapRow("Conteúdo para TikTok", "gerar criativos, você aprova ou recusa e ele publica")
                RoadmapRow("Indicações de investimento", "sugestões a partir da sua reserva e das contas riscadas")
                RoadmapRow("Conversa", "perguntar ao Dot em vez de procurar nas telas")
            }
        }
    }

    if (nameDialog) {
        var text by remember { mutableStateOf(dotName) }
        FormDialog("Nome do seu Dot", { nameDialog = false }, { onNameChange(text.trim().ifBlank { "Dot" }); nameDialog = false }) {
            Field("Nome", text, { text = it.take(16) })
        }
    }
}

@Composable
private fun RoadmapRow(title: String, sub: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp)) { Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(CyberTeal)) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = AppTextWhite, fontWeight = FontWeight.SemiBold)
            Text(sub, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Balão de fala com o que importa agora, calculado dos seus dados (sem inventar nada). */
@Composable
private fun SpeechBubble(data: FinanceData, name: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(14.dp)
    ) {
        if (data.isEmpty) {
            Text("${greeting()}! Eu sou o $name. Importe seus dados em Ajustes e eu começo a cuidar das suas finanças com você.", color = AppTextWhite, style = MaterialTheme.typography.bodyMedium)
        } else {
            val c = data.core(); val h = data.health()
            Text("${greeting()}! Eu sou o $name.", color = AppTextWhite, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Seu livre de verdade está em ${brl(c.free)} e sua saúde financeira em ${h.score}/100.", color = AppTextWhite, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(data.nextStep(), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Cartão compacto do Dot no topo do Painel. */
@Composable
fun DotGreetingCard(data: FinanceData, dotId: String, dotName: String, onOpen: () -> Unit) {
    val design = dotById(dotId)
    val mood = remember(data) { moodFor(data) }
    BentoCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DotAvatar(design, size = 72.dp, mood = mood)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${greeting()}, eu sou o $dotName", color = AppTextWhite, fontWeight = FontWeight.Bold)
                Text(
                    if (data.isEmpty) "Importe seus dados para eu começar." else "Saúde financeira ${data.health().score}/100. Toque para ver o que tenho a dizer.",
                    color = SecondaryText, style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
