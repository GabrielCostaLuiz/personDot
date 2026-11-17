package com.persondot.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.persondot.data.model.Reflection
import com.persondot.ui.theme.*
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.delay

fun formatDelayDays(days: Int): String {
    if (days <= 0) return "0 dias"
    val months = days / 30
    val remainingDays = days % 30
    return when {
        months == 0 -> "$days dias"
        months == 1 && remainingDays == 0 -> "1 mês"
        months == 1 && remainingDays > 0 -> "1 mês e $remainingDays ${if (remainingDays == 1) "dia" else "dias"}"
        months > 1 && remainingDays == 0 -> "$months meses"
        else -> "$months meses e $remainingDays ${if (remainingDays == 1) "dia" else "dias"}"
    }
}

@Composable
fun ReflectionTab(
    reflections: List<Reflection>,
    savingGoalRate: Double,
    onAddReflection: (
        name: String, price: Double, category: String, isNecessity: Boolean,
        helpsCarGoal: Boolean, useOneYear: Boolean, buyCash: Boolean,
        emotion: String, comment: String
    ) -> Unit,
    onApprove: (Reflection) -> Unit,
    onCancel: (Reflection) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pfFormat = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    var isCreating by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Welcome and psychology Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReflectionHeader(isCreating = isCreating, onToggleCreate = { isCreating = !isCreating })
                HelpAccordion(
                    title = "O que é Resfriamento de Impulso de Compra?",
                    explanation = "A Zona de Reflexão serve como uma barreira psicológica de 24 horas entre o impulso dopaminérgico momentâneo de gastar e a decisão real de compra. Isso amortece a impulsividade, dando espaço para a racionalidade analisar se o gasto se alinha aos seus objetivos de longo prazo.",
                    formula = "Decisões na fila de espera evitam o dreno financeiro comportamental.",
                    example = "Você desejou um relógio inteligente de R$ 450,00 por impulso. Adicionando-o na fila, você verá um cronômetro regressivo de 24 horas. Após as 24h, você clica conscientemente em:\n- 'Venci o Impulso!' para economizar R$ 450,00 e ganhar R$ 100,00 virtuais em reforço positivo.\n- 'Ainda Quero' para aprovar e faturar a compra (seja por faturas ou à vista) de mente descansada."
                )
            }
        }

        if (isCreating) {
            item {
                CreateReflectionForm(
                    savingGoalRate = savingGoalRate,
                    onSave = { name, price, cat, necessity, car, year, cash, emo, comm ->
                        onAddReflection(name, price, cat, necessity, car, year, cash, emo, comm)
                        isCreating = false
                    },
                    onCancelForm = { isCreating = false },
                    pfFormat = pfFormat
                )
            }
        }

        // Active cooling off timers
        val activeReflections = reflections.filter { !it.isApproved && !it.isCancelled }
        if (activeReflections.isNotEmpty()) {
            item {
                Text(
                    text = "TEMPORIZADORES DE DECISÃO ATIVOS (RESFRIAMENTO 24H)",
                    color = PulseViolet,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp
                )
            }
            items(activeReflections) { refl ->
                ActiveReflectionCard(
                    reflection = refl,
                    onApprove = { onApprove(refl) },
                    onCancel = { onCancel(refl) },
                    savingGoalRate = savingGoalRate,
                    pfFormat = pfFormat
                )
            }
        } else if (!isCreating) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CyberGreen.copy(0.04f))
                        .border(1.dp, CyberGreen.copy(0.15f), RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Limpo",
                            tint = CyberGreen,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Nenhum impulso de compra ativo na fila!",
                            color = AppTextWhite,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Todas as decisões foram pensadas com sobriedade.",
                            color = SecondaryText,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // History of impulsive victories & defeats
        val endedReflections = reflections.filter { it.isApproved || it.isCancelled }
        if (endedReflections.isNotEmpty()) {
            item {
                Text(
                    text = "HISTÓRICO COMPORTAMENTAL / RESULTADOS",
                    color = SecondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(endedReflections) { refl ->
                EndedReflectionCard(
                    refl = refl,
                    onDelete = { onDelete(refl.id) },
                    pfFormat = pfFormat
                )
            }
        }
    }
}

@Composable
fun ReflectionHeader(isCreating: Boolean, onToggleCreate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, CardBorderGlance)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ANTIDOTO CONTRA IMPULSOS",
                        color = PulseViolet,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Zona de Reflexão",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                IconButton(
                    onClick = onToggleCreate,
                    modifier = Modifier.testTag("toggle_creation_button")
                ) {
                    Icon(
                        imageVector = if (isCreating) Icons.Default.Close else Icons.Default.AddCircle,
                        contentDescription = "Toggle",
                        tint = if (isCreating) ImpulseAmber else CyberGreen,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Crie uma barreira entre o estímulo e a compra. O atraso deliberado de 24 horas rompe a resposta automática por dopamina.",
                color = SecondaryText,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun CreateReflectionForm(
    savingGoalRate: Double,
    onSave: (
        name: String, price: Double, category: String, isNecessity: Boolean,
        helpsCarGoal: Boolean, useOneYear: Boolean, buyCash: Boolean,
        emotion: String, comment: String
    ) -> Unit,
    onCancelForm: () -> Unit,
    pfFormat: NumberFormat
) {
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Eletrônicos") }
    
    // The columns of behavioral questions
    var isNecessity by remember { mutableStateOf(false) }
    var helpsCarGoal by remember { mutableStateOf(false) }
    var willUseMoreThanOneYear by remember { mutableStateOf(false) }
    var wouldBuyCash by remember { mutableStateOf(false) }
    var emotionSource by remember { mutableStateOf("Ansiedade/Dopamina") }
    var comment by remember { mutableStateOf("") }

    val doublePrice = priceText.toDoubleOrNull() ?: 0.0
    // how many days delayed to get the car = price / (monthly Savings Goal / 30)
    val carDelayDays = if (savingGoalRate > 0 && doublePrice > 0) {
        (doublePrice / (savingGoalRate / 30.0)).toInt()
    } else {
        0
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorderGlance, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "INICIAR ANÁLISE SOBRE COMPRA",
                color = CyberTeal,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome do Ganho / Desejo") },
                placeholder = { Text("Ex: Relógio Inteligente, Tênis, etc.") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reflection_name_input"),
                colors = textFieldDarkColors()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Preço estimado R$") },
                    placeholder = { Text("Ex: 450") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reflection_price_input"),
                    colors = textFieldDarkColors()
                )

                // Simulating categories
                Column(modifier = Modifier.weight(1.2f)) {
                    Text("Categoria", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
                    val categoriesList = listOf("Eletrônicos", "Aparência/Roupas", "Lazer/Delivery", "Assinatura", "Outros")
                    var expandedCat by remember { mutableStateOf(false) }
                    
                    Box {
                        Button(
                            onClick = { expandedCat = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(0.5.dp, BorderColor, RoundedCornerShape(8.dp))
                        ) {
                            Text(category, maxLines = 1, color = AppTextWhite)
                        }
                        DropdownMenu(
                            expanded = expandedCat,
                            onDismissRequest = { expandedCat = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            categoriesList.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat, color = AppTextWhite) },
                                    onClick = {
                                        category = cat
                                        expandedCat = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Real Time Behavioral Insight Preview
            if (doublePrice > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ImpulseAmber.copy(0.12f))
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = "Alerta", tint = ImpulseAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Aviso: Essa compra adiará suas metas em até ${formatDelayDays(carDelayDays)} se mantiver seu ritmo de poupança.",
                            color = ImpulseAmber,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Psychological checkboxes
            HorizontalDivider(color = BorderColor)
            Text(
                text = "CINCO PERGUNTAS DE INTEGRALIDADE COMPORTAMENTAL:",
                color = SecondaryText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )

            // Q1
            QuestionSwitchRow(
                question = "Isso é uma NECESSIDADE absoluta?",
                checked = isNecessity,
                onCheckedChange = { isNecessity = it }
            )

            // Q2
            QuestionSwitchRow(
                question = "Isso me aproxima das minhas metas?",
                checked = helpsCarGoal,
                onCheckedChange = { helpsCarGoal = it }
            )

            // Q3
            QuestionSwitchRow(
                question = "Vou usar por mais de 1 ANO?",
                checked = willUseMoreThanOneYear,
                onCheckedChange = { willUseMoreThanOneYear = it }
            )

            // Q4
            QuestionSwitchRow(
                question = "Comprometo-me a comprar apenas À VISTA?",
                checked = wouldBuyCash,
                onCheckedChange = { wouldBuyCash = it }
            )

            // Q5: Trigger
            Column {
                Text(
                    text = "Gatilho mental dominante no momento da compra:",
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodySmall
                )
                val triggers = listOf("Ansiedade/Dopamina", "Tédio", "Necessidade Real", "Influência Externa")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    triggers.forEach { trig ->
                        val selected = emotionSource == trig
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) PulseViolet else BorderColor)
                                .clickable { emotionSource = trig }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = trig.split("/").getOrElse(0) { trig },
                                color = if (selected) Color.White else SecondaryText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("O que estou sentindo agora?") },
                placeholder = { Text("Ex: Chateado com bugs da sprint, quero recompensa.") },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldDarkColors()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancelForm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SecondaryText),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Text("Cancelar")
                }

                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onSave(
                                name,
                                doublePrice,
                                category,
                                isNecessity,
                                helpsCarGoal,
                                willUseMoreThanOneYear,
                                wouldBuyCash,
                                emotionSource,
                                comment
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("submit_reflection_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = PulseViolet),
                    enabled = name.isNotBlank() && doublePrice > 0
                ) {
                    Icon(imageVector = Icons.Default.LockClock, contentDescription = "Lock")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ativar Resfriamento 24h", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun QuestionSwitchRow(
    question: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = question,
            color = AppTextWhite.copy(alpha = 0.9f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyberGreen,
                checkedTrackColor = CyberGreen.copy(0.4f),
                uncheckedThumbColor = SecondaryText,
                uncheckedTrackColor = BorderColor
            )
        )
    }
}

@Composable
fun ActiveReflectionCard(
    reflection: Reflection,
    onApprove: () -> Unit,
    onCancel: () -> Unit,
    savingGoalRate: Double,
    pfFormat: NumberFormat
) {
    val carDelayDays = if (savingGoalRate > 0) {
        (reflection.estimatedPrice / (savingGoalRate / 30.0)).toInt()
    } else {
        0
    }

    var timeLeftMs by remember(reflection.id) {
        mutableStateOf((reflection.timestamp + reflection.timerDurationHours * 3600_000L - System.currentTimeMillis()).coerceAtLeast(0L))
    }

    LaunchedEffect(reflection.id) {
        while (timeLeftMs > 0) {
            delay(1000L)
            timeLeftMs = (reflection.timestamp + reflection.timerDurationHours * 3600_000L - System.currentTimeMillis()).coerceAtLeast(0L)
        }
    }

    val hours = timeLeftMs / 3600_000L
    val minutes = (timeLeftMs % 3600_000L) / 60_000L
    val seconds = (timeLeftMs % 60_000L) / 1000L
    val timerString = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorderGlance, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PulseViolet.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Outlined.Timer, contentDescription = "Hold", tint = PulseViolet)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = reflection.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = reflection.category,
                                color = SecondaryText,
                                style = MaterialTheme.typography.bodySmall
                            )
                            // LIVE COUNTDOWN TIMER BADGE
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (timeLeftMs > 0) PulseViolet.copy(0.12f) else CyberGreen.copy(0.12f))
                                    .border(0.5.dp, if (timeLeftMs > 0) PulseViolet.copy(0.4f) else CyberGreen.copy(0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(
                                        imageVector = Icons.Outlined.Timer,
                                        contentDescription = "Timer",
                                        tint = if (timeLeftMs > 0) PulseViolet else CyberGreen,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = if (timeLeftMs > 0) timerString else "LIBERADO",
                                        color = if (timeLeftMs > 0) PulseViolet else CyberGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    text = pfFormat.format(reflection.estimatedPrice),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cool-down active label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(0.02f))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Timeline,
                    contentDescription = "Trigger tracking",
                    tint = PulseViolet,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Gatilho: ${reflection.emotionSource} • Adia suas metas em ${formatDelayDays(carDelayDays)}.",
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp
                )
            }

            if (reflection.reflectionComment.isNotBlank()) {
                Text(
                    text = "\"${reflection.reflectionComment}\"",
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action triggers (Vencer impulso vs Confirmar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // VICTORY BUTTON: Overcame the impulse
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("cancel_impulse_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = "Poupar")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Venci o Impulso!", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                // FAIL TRIGGER: Confirm bought
                OutlinedButton(
                    onClick = onApprove,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ImpulseAmber),
                    border = BorderStroke(1.dp, ImpulseAmber.copy(0.5f)),
                    modifier = Modifier
                        .weight(0.9f)
                        .testTag("confirm_impulse_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Ainda Quero", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun EndedReflectionCard(
    refl: Reflection,
    onDelete: () -> Unit,
    pfFormat: NumberFormat
) {
    val isVictory = refl.isCancelled // cancelled means overcome impulse and didn't spend!
    val accentTone = if (isVictory) CyberGreen else ImpulseAmber

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, BorderColor, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(0.6f)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentTone.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVictory) Icons.Default.Mood else Icons.Default.MoodBad,
                        contentDescription = "Resultado",
                        tint = accentTone,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = refl.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = if (isVictory) "Poupar R$ ${pfFormat.format(refl.estimatedPrice)}" else "Gasto creditado R$ ${pfFormat.format(refl.estimatedPrice)}",
                        color = accentTone.copy(0.9f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(0.04f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isVictory) "VITÓRIA" else "DÉBITO",
                        color = accentTone,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Excluir",
                        tint = SecondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun textFieldDarkColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PulseViolet,
        unfocusedBorderColor = BorderColor,
        focusedLabelColor = PulseViolet,
        unfocusedLabelColor = SecondaryText,
        focusedTextColor = AppTextWhite,
        unfocusedTextColor = AppTextWhite,
        focusedPlaceholderColor = SecondaryText.copy(0.7f),
        unfocusedPlaceholderColor = SecondaryText.copy(0.5f)
    )
}
