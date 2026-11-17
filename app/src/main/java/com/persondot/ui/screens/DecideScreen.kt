package com.persondot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.persondot.data.model.Reflection
import com.persondot.domain.*
import com.persondot.ui.components.*
import com.persondot.ui.theme.*
import com.persondot.ui.viewmodel.FinanceViewModel

/** Aba Decidir: "vale a pena?" (simulador com os seus números) e a zona de reflexão com resfriamento de 24h. */
@Composable
fun DecideScreen(data: FinanceData, vm: FinanceViewModel, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            listOf("Vale a pena?", "Reflexão 24h").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 2),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = CyberGreen, activeContentColor = androidx.compose.ui.graphics.Color.Black, inactiveContentColor = AppTextWhite)
                ) { Text(label) }
            }
        }
        if (tab == 0) PurchaseSimulator(data) else ReflectionTab(
            reflections = data.reflections,
            savingGoalRate = data.settings.aporteLongo,
            onAddReflection = { name, price, cat, necessity, car, year, cash, emo, comment ->
                vm.addReflection(Reflection(name = name, estimatedPrice = price, category = cat, isNecessity = necessity, helpsCarGoal = car,
                    willUseMoreThanOneYear = year, wouldBuyCash = cash, emotionSource = emo, reflectionComment = comment))
            },
            onApprove = { vm.approveReflection(it) },
            onCancel = { vm.cancelReflection(it) },
            onDelete = { id -> data.reflections.firstOrNull { it.id == id }?.let { vm.deleteReflection(it) } }
        )
    }
}

@Composable
private fun PurchaseSimulator(data: FinanceData) {
    var desc by rememberSaveable { mutableStateOf("") }
    var valor by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(PayMode.VISTA) }
    var parcelas by rememberSaveable { mutableStateOf("3") }
    var juros by rememberSaveable { mutableStateOf("") }
    var recorrente by rememberSaveable { mutableStateOf("") }
    var desejo by rememberSaveable { mutableStateOf(true) }
    var result by remember { mutableStateOf<PurchaseResult?>(null) }
    val c = remember(data) { data.core() }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        item {
            BentoCard {
                LabelText("Vale a pena comprar?", CyberGreen)
                Text("Seu livre de verdade hoje: ${brl(c.free)}", color = SecondaryText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("O que é? (ex.: celular, viagem)", desc, { desc = it })
                    MoneyField("Valor total (R$)", valor) { valor = it }
                    Choice("Como vai pagar?", listOf(PayMode.VISTA.name to "À vista", PayMode.SEM_JUROS.name to "Parcelado sem juros", PayMode.COM_JUROS.name to "Parcelado com juros"), mode.name) { mode = PayMode.valueOf(it) }
                    if (mode != PayMode.VISTA) Field("Número de parcelas", parcelas, { parcelas = it.filter(Char::isDigit) }, KeyboardType.Number)
                    if (mode == PayMode.COM_JUROS) MoneyField("Juros ao mês (%)", juros) { juros = it }
                    MoneyField("Custo mensal que vem junto (opcional, ex.: carro)", recorrente) { recorrente = it }
                    Choice("Tipo", listOf("d" to "Desejo / conforto", "e" to "Essencial"), if (desejo) "d" else "e") { desejo = it == "d" }
                    PrimaryButton("Analisar", {
                        result = data.evalPurchase(PurchaseInput(desc.trim(), parseMoney(valor), mode, parcelas.toIntOrNull() ?: 1, parseMoney(juros), parseMoney(recorrente), desejo))
                    }, Modifier.fillMaxWidth(), )
                }
            }
        }
        result?.let { r ->
            item {
                BentoCard {
                    LabelText("Veredito${if (desc.isNotBlank()) " · $desc" else ""}")
                    Text(r.verdict, color = levelColor(r.level), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 4.dp))
                    r.lines.forEach { (lvl, text) ->
                        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 5.dp)) { Dot(lvl) }
                            Spacer(Modifier.width(10.dp))
                            Text(text, color = AppTextWhite, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Note("Parcela mensal: ${brl(r.parcela)} · total pago: ${brl(r.total)}. É uma análise feita com as regras do seu próprio plano, não uma recomendação financeira personalizada.")
                }
            }
        }
    }
}
