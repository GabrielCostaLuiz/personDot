package com.persondot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.persondot.domain.*
import com.persondot.ui.components.*
import com.persondot.ui.dot.LocalDot
import com.persondot.ui.dot.Mood
import com.persondot.ui.theme.*

/** "Vale a pena comprar?": o simulador, em uma folha que sobe da base (aberta pelo Painel e por Gastos). */
@Composable
fun PurchaseSheet(data: FinanceData, onDismiss: () -> Unit) {
    val dot = LocalDot.current
    val c = remember(data) { data.core() }
    var desc by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(PayMode.VISTA) }
    var parcelas by remember { mutableStateOf("3") }
    var juros by remember { mutableStateOf("") }
    var recorrente by remember { mutableStateOf("") }
    var desejo by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<PurchaseResult?>(null) }

    AppSheet("Vale a pena comprar?", onDismiss, actions = {
        SoftButton("Fechar", onDismiss, Modifier.weight(1f))
        PrimaryButton("Analisar", {
            val r = data.evalPurchase(PurchaseInput(desc.trim(), parseMoney(valor), mode, parcelas.toIntOrNull() ?: 1, parseMoney(juros), parseMoney(recorrente), desejo))
            result = r
            dot.react(when (r.level) { Level.OK -> "Isso cabe no orçamento."; Level.WARN -> "Cabe, mas pense bem."; Level.BAD -> "Eu seguraria essa compra." },
                when (r.level) { Level.OK -> Mood.HAPPY; Level.WARN -> Mood.NEUTRAL; Level.BAD -> Mood.WORRIED })
        }, Modifier.weight(1.6f), enabled = parseMoney(valor) > 0)
    }) {
        Text("Seu livre de verdade hoje: ${brl(c.free)}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        Field("O que é? (ex.: celular, viagem)", desc, { desc = it })
        MoneyField("Valor total (R$)", valor) { valor = it }
        Choice("Como vai pagar?", listOf(PayMode.VISTA.name to "À vista", PayMode.SEM_JUROS.name to "Parcelado sem juros", PayMode.COM_JUROS.name to "Parcelado com juros"), mode.name) { mode = PayMode.valueOf(it) }
        if (mode != PayMode.VISTA) Field("Número de parcelas", parcelas, { parcelas = it.filter(Char::isDigit) }, KeyboardType.Number)
        if (mode == PayMode.COM_JUROS) MoneyField("Juros ao mês (%)", juros) { juros = it }
        MoneyField("Custo mensal que vem junto (opcional, ex.: carro)", recorrente) { recorrente = it }
        Choice("Tipo", listOf("d" to "Desejo / conforto", "e" to "Essencial"), if (desejo) "d" else "e") { desejo = it == "d" }

        result?.let { r ->
            HorizontalDividerThin()
            Text(r.verdict, color = levelColor(r.level), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            r.lines.forEach { (lvl, text) ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 5.dp)) { Dot(lvl) }
                    Spacer(Modifier.width(10.dp))
                    Text(text, color = AppTextWhite, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Note("Parcela mensal: ${brl(r.parcela)} · total pago: ${brl(r.total)}. É uma análise feita com as regras do seu próprio plano, não uma recomendação financeira personalizada.")
        }
    }
}

@Composable
private fun HorizontalDividerThin() = androidx.compose.material3.HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
