package com.persondot.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DirectionsCar
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
import com.persondot.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CarGoalTab(
    carTarget: Double,
    carCurrent: Double,
    monthlySavings: Double,
    monthlyIncome: Double,
    baseExpenses: Double,
    onUpdateCarSaved: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val pfFormat = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

    val remainingValue = (carTarget - carCurrent).coerceAtLeast(0.0)

    // Months left calculation
    val monthsLeft = if (monthlySavings > 0) remainingValue / monthlySavings else 0.0

    // Dynamic state for manually updating current car savings in-app
    var inputSavingsAmount by remember { mutableStateOf("") }
    var showUpdatePanel by remember { mutableStateOf(false) }

    // Car ownership simulation states
    var isSimulatingCosts by remember { mutableStateOf(true) }
    var yearlyKm by remember { mutableStateOf(10000f) } // slider for fuel/maintenance impact

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Main Car Goal state
        item {
            CarVelocityControlCard(
                target = carTarget,
                current = carCurrent,
                remaining = remainingValue,
                monthsLeft = monthsLeft,
                pfFormat = pfFormat,
                onToggleUpdate = { showUpdatePanel = !showUpdatePanel }
            )
        }

        // Quick deposit/update panel
        if (showUpdatePanel) {
            item {
                UpdateCarAllocatedCard(
                    onSave = {
                        val v = inputSavingsAmount.toDoubleOrNull() ?: 0.0
                        if (v >= 0) {
                            onUpdateCarSaved(v)
                            inputSavingsAmount = ""
                            showUpdatePanel = false
                        }
                    },
                    inputValue = inputSavingsAmount,
                    onValueChange = { inputSavingsAmount = it },
                    onCancel = { showUpdatePanel = false }
                )
            }
        }

        // Checklist of progress
        item {
            CarProgressIndicatorCard(current = carCurrent, target = carTarget)
        }

        // Total Cost of Ownership Simulator (Extremamente Importante)
        item {
            Text(
                text = "CUSTO REAL DO VEÍCULO (TCO DE PROPRIEDADE)",
                color = PulseViolet,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.0.sp
            )
        }

        item {
            CarTcoSimulatorCard(
                yearlyKm = yearlyKm,
                onYearlyKmChange = { yearlyKm = it },
                monthlyIncome = monthlyIncome,
                baseExpenses = baseExpenses,
                pfFormat = pfFormat
            )
        }

        // Warning or reinforcement
        item {
            CarKeepWarningCard(
                monthlyIncome = monthlyIncome,
                tcoTotal = computeTcoValue(yearlyKm),
                pfFormat = pfFormat
            )
        }
    }
}

@Composable
fun CarVelocityControlCard(
    target: Double,
    current: Double,
    remaining: Double,
    monthsLeft: Double,
    pfFormat: NumberFormat,
    onToggleUpdate: () -> Unit
) {
    val progress = (current / target).toFloat().coerceIn(0f, 1f)

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
                Column {
                    Text(
                        text = "METAS DE PATRIMÔNIO: CARRO",
                        color = PulseViolet,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Compra à Vista R$ 30k",
                        color = AppTextWhite,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PulseViolet.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DirectionsCar,
                        contentDescription = "Carro",
                        tint = PulseViolet,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Large displays
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Alocado", color = SecondaryText, fontSize = 11.sp)
                    Text(
                        pfFormat.format(current),
                        color = CyberGreen,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Falta Acumular", color = SecondaryText, fontSize = 11.sp)
                    Text(
                        pfFormat.format(remaining),
                        color = AppTextWhite,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = PulseViolet,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(0.04f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format("%.1f%% completo", progress * 100),
                    color = SecondaryText,
                    fontSize = 11.sp
                )
                Text(
                    text = "Aproximadamente %.1f meses".format(monthsLeft),
                    color = CyberTeal,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onToggleUpdate,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("car_deposit_toggle"),
                colors = ButtonDefaults.buttonColors(containerColor = PulseViolet),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Atualizar Valor Guardado do Carro", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun UpdateCarAllocatedCard(
    onSave: () -> Unit,
    inputValue: String,
    onValueChange: (String) -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorderGlance, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "REMANEJAR RESERVA PARA META DO CARRO",
                color = CyberTeal,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = inputValue,
                onValueChange = onValueChange,
                label = { Text("Valor Total Alocado Para o Carro R$") },
                placeholder = { Text("Ex: 8500") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("car_savings_input"),
                colors = textFieldDarkColors()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onCancel, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SecondaryText),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Text("Voltar")
                }
                Button(
                    onSave, modifier = Modifier.weight(1.2f).testTag("car_savings_save"),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberTeal),
                    enabled = inputValue.isNotBlank()
                ) {
                    Text("Salvar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CarProgressIndicatorCard(current: Double, target: Double) {
    val items = listOf(
        Pair("R$ 5.000: Entrada simbólica ultrapassada", current >= 5000),
        Pair("R$ 15.000: Metade do caminho conquistada!", current >= 15000),
        Pair("R$ 22.000: Poder de barganha à vista de 70%", current >= 22000),
        Pair("R$ 30.000: Carro comprado à vista, sem parcelas!", current >= 30000)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, BorderColor, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "MARCOS COMPORTAMENTAIS",
                color = SecondaryText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            items.forEach { (label, completed) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "State",
                        tint = if (completed) CyberGreen else SecondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = label,
                        color = if (completed) AppTextWhite else SecondaryText,
                        fontSize = 11.sp,
                        fontWeight = if (completed) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun CarTcoSimulatorCard(
    yearlyKm: Float,
    onYearlyKmChange: (Float) -> Unit,
    monthlyIncome: Double,
    baseExpenses: Double,
    pfFormat: NumberFormat
) {
    // TCO costs calculations
    val seguro = 250.0
    val ipva = 180.0
    val preventiva = 150.0
    val imprevistos = 120.0
    
    // Dynamic fuel cost depending on Km driven (e.g. 1 liter = 10km, fuel = R$6.00)
    val fuelMonthly = (yearlyKm / 12f / 10f) * 6.00

    val tcoTotal = seguro + ipva + preventiva + imprevistos + fuelMonthly

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
                Text(
                    text = "SIMULADOR DE CUSTO REAL MENSAL (TCO)",
                    color = CyberTeal,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ImpulseAmber.copy(0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = pfFormat.format(tcoTotal) + " /mês",
                        color = ImpulseAmber,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Manter o veículo custará quase tanto quanto as parcelas poupadas! Veja a divisão interna mensal:",
                color = SecondaryText,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Breakdown bills
            TcoItemRow(label = "Seguro Franquia Fipe Slim", valText = pfFormat.format(seguro))
            TcoItemRow(label = "IPVA Estimado Licenciamento", valText = pfFormat.format(ipva))
            TcoItemRow(label = "Manutenção Preventiva Pneus/Óleo", valText = pfFormat.format(preventiva))
            TcoItemRow(label = "Imprevistos amortizados/Depreciação", valText = pfFormat.format(imprevistos))
            TcoItemRow(label = "Combustível (Km Simulado)", valText = pfFormat.format(fuelMonthly), highlight = true)

            Spacer(modifier = Modifier.height(12.dp))

            // Sliders driven simulation
            Text(
                text = "Km estimado rodados por ano: ${yearlyKm.toInt()} Km",
                color = AppTextWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Slider(
                value = yearlyKm,
                onValueChange = onYearlyKmChange,
                valueRange = 2000f..25000f,
                steps = 23,
                colors = SliderDefaults.colors(
                    thumbColor = CyberTeal,
                    activeTrackColor = CyberTeal,
                    inactiveTrackColor = BorderColor
                )
            )
        }
    }
}

@Composable
fun TcoItemRow(label: String, valText: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = if (highlight) AppTextWhite else SecondaryText, fontSize = 11.sp)
        Text(text = valText, color = if (highlight) CyberTeal else AppTextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

fun computeTcoValue(yearlyKm: Float): Double {
    val fixed = 250.0 + 180.0 + 150.0 + 120.0
    val fuel = (yearlyKm / 12f / 10f) * 6.00
    return fixed + fuel
}

@Composable
fun CarKeepWarningCard(monthlyIncome: Double, tcoTotal: Double, pfFormat: NumberFormat) {
    // Current base expenses 2800 + TCO total. Let's compare if they can keep it.
    val leftBudget = monthlyIncome - 2800.0 - tcoTotal
    val isRisky = leftBudget < 300.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isRisky) ImpulseAmber.copy(0.4f) else CyberGreen.copy(0.4f), RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = if (isRisky) ImpulseAmber.copy(0.02f) else CyberGreen.copy(0.02f)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = if (isRisky) Icons.Default.Warning else Icons.Default.VerifiedUser,
                contentDescription = "Análise de TCO",
                tint = if (isRisky) ImpulseAmber else CyberGreen,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = if (isRisky) "ANÁLISE COMPORTAMENTAL DE MANUTENÇÃO" else "AUTONOMIA DE MANUTENÇÃO SAUDÁVEL",
                    color = if (isRisky) ImpulseAmber else CyberGreen,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (isRisky) {
                        "Aviso Crítico: O seu custo fixo PJ (R$2800) somado ao custo mensal real do carro (${pfFormat.format(tcoTotal)}) consome quase todo seu faturamento de R$4200! Sobra apenas ${pfFormat.format(leftBudget)} livre. Evite lifestyle inflation!"
                    } else {
                        "Você consegue manter esse veículo saudável. Sobram ${pfFormat.format(leftBudget)} livres de sobra mensal após cobrir os custos operacionais PJ e do carro!"
                    },
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
