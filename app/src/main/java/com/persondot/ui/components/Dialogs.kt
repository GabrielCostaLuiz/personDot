@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.persondot.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.persondot.data.model.*
import com.persondot.domain.*
import com.persondot.ui.theme.*
import java.time.LocalDate
import java.util.Locale

// ---------- entrada/saída de números ----------
fun parseMoney(s: String): Double {
    val t = s.trim()
    val n = when {
        t.contains(',') -> t.replace(".", "").replace(',', '.')
        Regex("""\d{1,3}(\.\d{3})+""").matches(t) -> t.replace(".", "")     // 5.000 = cinco mil
        else -> t
    }
    return n.toDoubleOrNull() ?: 0.0
}
fun editMoney(v: Double): String = if (v == 0.0) "" else java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(v)

/**
 * Máscara de valor, do jeito natural de digitar: 5000 vira 5.000; para centavos, digite a vírgula (ou o ponto):
 * 5000,5 vira 5.000,5. Os pontos de milhar são colocados sozinhos.
 */
fun maskMoney(raw: String): String {
    val s = raw.filter { it.isDigit() || it == ',' || it == '.' }
    val commaIdx = if (s.contains(',')) s.lastIndexOf(',') else if (s.endsWith('.')) s.length - 1 else -1
    val intPart = (if (commaIdx >= 0) s.substring(0, commaIdx) else s).filter(Char::isDigit).trimStart('0').take(12)
    val decPart = if (commaIdx >= 0) s.substring(commaIdx + 1).filter(Char::isDigit).take(2) else null
    if (intPart.isEmpty() && decPart == null) return ""
    val grouped = if (intPart.isEmpty()) "0" else intPart.reversed().chunked(3).joinToString(".").reversed()
    return if (decPart != null) "$grouped,$decPart" else grouped
}
private val monthRe = Regex("""\d{4}-(0[1-9]|1[0-2])""")
private val dateRe = Regex("""\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])""")
fun validMonth(s: String) = monthRe.matches(s)

// ---------- blocos de formulário ----------
@Composable
fun FormDialog(
    title: String, onDismiss: () -> Unit, onConfirm: () -> Unit, confirmEnabled: Boolean = true, confirmText: String = "Salvar",
    onDelete: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit
) {
    AppSheet(title, onDismiss, actions = {
        if (onDelete != null) SoftButton("Excluir", onDelete, Modifier.weight(1f), danger = true)
        SoftButton("Cancelar", onDismiss, Modifier.weight(1f))
        PrimaryButton(confirmText, onConfirm, Modifier.weight(if (onDelete != null) 1.3f else 1.6f), enabled = confirmEnabled)
    }, content = content)
}

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text, placeholder: String? = null) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), colors = textFieldDarkColors(),
        placeholder = placeholder?.let { { Text(it) } }
    )
}

@Composable
fun MoneyField(label: String, value: String, onChange: (String) -> Unit) {
    // o cursor fica sempre no fim: os dígitos entram da direita para a esquerda
    OutlinedTextField(
        value = androidx.compose.ui.text.input.TextFieldValue(value, androidx.compose.ui.text.TextRange(value.length)),
        onValueChange = { onChange(maskMoney(it.text)) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = textFieldDarkColors(), placeholder = { Text("0,00") }
    )
}

@Composable
fun Choice(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column {
        LabelText(label)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) ->
                FilterChip(
                    selected = value == selected, onClick = { onSelect(value) }, label = { Text(text) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyberGreen, selectedLabelColor = Color.Black)
                )
            }
        }
    }
}

/** Escolha de mês entre o atual e os próximos 14. [allowNone] adiciona "sem data". */
@Composable
fun MonthChoice(label: String, selected: String, onSelect: (String) -> Unit, allowNone: Boolean = false) {
    val months = remember { listOf(monthKey(LocalDate.now())) + visibleMonths() }
    val opts = (if (allowNone) listOf("" to "sem data") else emptyList()) + months.map { it to mLabel(it) }
    Choice(label, opts, selected, onSelect)
}

@Composable
private fun AccountChoice(label: String, accounts: List<Account>, selected: String, onSelect: (String) -> Unit) {
    Choice(label, accounts.filter { it.kind != Account.KIND_IPCA }.map { it.id.toString() to it.name } + ("" to "Nenhuma"), selected, onSelect)
}

// ---------- diálogos de cada entidade ----------
@Composable
fun AccountDialog(initial: Account?, onSave: (Account) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var balance by remember { mutableStateOf(editMoney(initial?.balance ?: 0.0)) }
    var kind by remember { mutableStateOf(initial?.kind ?: Account.KIND_RESERVA) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    FormDialog(if (initial == null) "Nova conta" else "Editar conta", onDismiss, {
        onSave((initial ?: Account(name = "", balance = 0.0)).copy(name = name.trim(), balance = parseMoney(balance), kind = kind, note = note.trim())); onDismiss()
    }, confirmEnabled = name.isNotBlank(), onDelete = onDelete?.let { { it(); onDismiss() } }) {
        Field("Nome (ex.: Mercado Pago)", name, { name = it })
        MoneyField("Saldo (R$)", balance) { balance = it }
        Choice("Tipo", listOf(Account.KIND_RESERVA to "Reserva / liquidez diária", Account.KIND_IPCA to "Balde IPCA+"), kind) { kind = it }
        Field("Observação (ex.: 120% do CDI)", note, { note = it })
    }
}

@Composable
fun ReceivableDialog(
    initial: Receivable?, accounts: List<Account>, onSave: (Receivable) -> Unit, onReceive: (Receivable, Double, Long?) -> Unit,
    onDelete: (() -> Unit)?, onDismiss: () -> Unit
) {
    var desc by remember { mutableStateOf(initial?.desc ?: "") }
    var amount by remember { mutableStateOf(editMoney(initial?.amount ?: 0.0)) }
    var acc by remember { mutableStateOf(accounts.firstOrNull()?.id?.toString() ?: "") }
    val canReceive = initial != null && !initial.received
    FormDialog(if (initial == null) "Nova entrada a receber" else "Entrada", onDismiss, {
        onSave((initial ?: Receivable(desc = "", amount = 0.0)).copy(desc = desc.trim(), amount = parseMoney(amount))); onDismiss()
    }, confirmEnabled = desc.isNotBlank(), onDelete = onDelete?.let { { it(); onDismiss() } }) {
        Field("Descrição", desc, { desc = it })
        MoneyField("Valor (R$)", amount) { amount = it }
        if (canReceive) {
            AccountChoice("Chegou em qual conta?", accounts, acc) { acc = it }
            PrimaryButton("Marcar como recebida", { onReceive(initial!!, parseMoney(amount), acc.toLongOrNull()); onDismiss() }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ExpenseDialog(initial: Expense?, accounts: List<Account>, categories: List<String>, onSave: (Expense, Long?) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var desc by remember { mutableStateOf(initial?.desc ?: "") }
    var amount by remember { mutableStateOf(editMoney(initial?.amount ?: 0.0)) }
    var cat by remember { mutableStateOf(initial?.cat ?: "Lazer") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now().toString()) }
    var acc by remember { mutableStateOf("") }
    // gasto novo: já vem com a primeira conta marcada (o saldo dela é descontado); "Nenhuma" só registra
    androidx.compose.runtime.LaunchedEffect(accounts) { if (initial == null && acc.isEmpty()) acc = accounts.firstOrNull { it.kind != Account.KIND_IPCA }?.id?.toString() ?: "" }
    FormDialog(if (initial == null) "Novo gasto" else "Editar gasto", onDismiss, {
        onSave((initial ?: Expense(desc = "", amount = 0.0, cat = cat, date = date)).copy(desc = desc.trim(), amount = parseMoney(amount), cat = cat, date = date), acc.toLongOrNull()); onDismiss()
    }, confirmEnabled = desc.isNotBlank() && dateRe.matches(date), onDelete = onDelete?.let { { it(); onDismiss() } }) {
        Field("Descrição", desc, { desc = it })
        MoneyField("Valor (R$)", amount) { amount = it }
        Choice("Categoria", (categories + listOfNotNull(initial?.cat)).distinct().map { it to it }, cat) { cat = it }
        Field("Data (AAAA-MM-DD)", date, { date = it })
        if (initial == null) AccountChoice("Descontar de qual conta?", accounts, acc) { acc = it }
    }
}

@Composable
fun FixedDialog(initial: FixedCost?, defaultGroup: String, startMonth: String, settings: Settings, onSave: (FixedCost) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var amount by remember { mutableStateOf(editMoney(initial?.amount ?: 0.0)) }
    var grp by remember { mutableStateOf(initial?.grp ?: defaultGroup) }
    var onCard by remember { mutableStateOf(initial?.onCard ?: false) }
    var from by remember { mutableStateOf(initial?.startsAt ?: startMonth) }
    var until by remember { mutableStateOf(initial?.endsBefore ?: "") }
    FormDialog(if (initial == null) "Novo custo previsto" else "Editar custo previsto", onDismiss, {
        onSave((initial ?: FixedCost(name = "", amount = 0.0)).copy(name = name.trim(), amount = parseMoney(amount), grp = grp, onCard = onCard, startsAt = from.ifBlank { null }, endsBefore = until.ifBlank { null })); onDismiss()
    }, confirmEnabled = name.isNotBlank(), onDelete = onDelete?.let { { it(); onDismiss() } }) {
        Field("Nome", name, { name = it })
        MoneyField("Valor mensal (R$)", amount) { amount = it }
        Choice("Grupo", settings.groups.map { it.id to it.name }, grp) { grp = it }
        Choice("Cobrado no cartão?", listOf("n" to "Não", "s" to "Sim"), if (onCard) "s" else "n") { onCard = it == "s" }
        MonthChoice("Vale a partir de", from, { from = it }, allowNone = true)
        if (initial != null) MonthChoice("Cancelado a partir de (vazio = continua)", until, { until = it }, allowNone = true)
        if (initial != null) Text("Mudar o valor aqui muda todos os meses ainda em aberto. Para mudar só um mês, toque no custo dentro do mês.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Um custo previsto dentro de um mês: confirmar o pagamento, ajustar o valor só daquele mês,
 * tirar só daquele mês ou cancelar daqui pra frente.
 */
@Composable
fun ForecastDialog(
    f: Forecast, accounts: List<Account>,
    onConfirm: (Double, Long?) -> Unit, onAdjust: (Double) -> Unit, onRemove: () -> Unit, onRestore: () -> Unit,
    onUndo: () -> Unit, onCancelFrom: () -> Unit, onEditCost: () -> Unit, onNewAccount: () -> Unit, onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf(editMoney(f.amount)) }
    var acc by remember { mutableStateOf(accounts.firstOrNull { it.kind != Account.KIND_IPCA }?.id?.toString() ?: "") }
    val value = parseMoney(amount)
    AppSheet(
        "${f.cost.name} · ${mLabel(f.month)}", onDismiss,
        actions = {
            when (f.status) {
                ForecastStatus.PREVISTO -> PrimaryButton("Confirmar pagamento de ${brl(value)}", { onConfirm(value, acc.toLongOrNull()); onDismiss() }, Modifier.fillMaxWidth(), enabled = value > 0)
                ForecastStatus.PAGO -> PrimaryButton("Desfazer confirmação", { onUndo(); onDismiss() }, Modifier.fillMaxWidth())
                ForecastStatus.REMOVIDO -> PrimaryButton("Trazer de volta", { onRestore(); onDismiss() }, Modifier.fillMaxWidth())
            }
        }
    ) {
        when (f.status) {
            ForecastStatus.PREVISTO -> {
                Text("Previsto. Quando pagar, confirme aqui. Se o valor mudou, ajuste antes.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                MoneyField("Valor deste mês (R$)", amount) { amount = it }
                if (f.adjusted || value != f.cost.amount) Text("Valor padrão: ${brl(f.cost.amount)}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Choice("Descontar de qual conta?", accounts.filter { it.kind != Account.KIND_IPCA }.map { it.id.toString() to it.name } + ("" to "Nenhuma"), acc) { acc = it }
                TextButton(onClick = onNewAccount) { Text("+ criar uma conta", color = CyberGreen) }
                if (value != f.amount && value > 0) SoftButton("Só ajustar o valor deste mês", { onAdjust(value); onDismiss() }, Modifier.fillMaxWidth())
                SoftButton("Tirar só deste mês", { onRemove(); onDismiss() }, Modifier.fillMaxWidth())
                SoftButton("Cancelar daqui pra frente", { onCancelFrom(); onDismiss() }, Modifier.fillMaxWidth(), danger = true)
                TextButton(onClick = { onEditCost(); onDismiss() }) { Text("Editar o valor padrão", color = CyberGreen) }
            }
            ForecastStatus.PAGO -> {
                Text("Pago: ${brl(f.amount)}.", color = CyberGreen, fontWeight = FontWeight.Bold)
                Text("Desfazer devolve o valor à conta de onde saiu.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
            ForecastStatus.REMOVIDO -> Text("Removido só deste mês: não entra nas somas.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun NewDebtDialog(onCreate: (name: String, note: String, amount: Double, count: Int, start: String?) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("1") }
    var start by remember { mutableStateOf(addMonths(monthKey(LocalDate.now()), 1)) }
    var note by remember { mutableStateOf("") }
    FormDialog("Nova dívida / parcelamento", onDismiss, {
        onCreate(name.trim(), note.trim(), parseMoney(amount), count.toIntOrNull() ?: 1, start.ifBlank { null }); onDismiss()
    }, confirmEnabled = name.isNotBlank() && parseMoney(amount) > 0) {
        Field("Nome (ex.: Renner)", name, { name = it })
        MoneyField("Valor de cada parcela (R$)", amount) { amount = it }
        Field("Quantidade de parcelas", count, { count = it.filter(Char::isDigit) }, KeyboardType.Number)
        MonthChoice("Primeiro vencimento", start, { start = it }, allowNone = true)
        Field("Observação (ex.: sem juros)", note, { note = it })
    }
}

@Composable
fun InstallmentDialog(initial: DebtInstallment, onSave: (DebtInstallment) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(editMoney(initial.amount)) }
    var month by remember { mutableStateOf(initial.month ?: "") }
    var paid by remember { mutableStateOf(initial.paid) }
    var subs by remember { mutableStateOf(initial.includesSubs) }
    FormDialog("Parcela", onDismiss, { onSave(initial.copy(amount = parseMoney(amount), month = month.ifBlank { null }, paid = paid, includesSubs = subs)); onDismiss() },
        onDelete = { onDelete(); onDismiss() }) {
        MoneyField("Valor (R$)", amount) { amount = it }
        MonthChoice("Mês", month, { month = it }, allowNone = true)
        Choice("Situação (não mexe no saldo das contas)", listOf("n" to "Em aberto", "s" to "Paga"), if (paid) "s" else "n") { paid = it == "s" }
        Choice("A fatura já inclui as assinaturas?", listOf("n" to "Não", "s" to "Sim"), if (subs) "s" else "n") { subs = it == "s" }
    }
}

@Composable
fun AddInstallmentDialog(onAdd: (String?, Double) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var month by remember { mutableStateOf(addMonths(monthKey(LocalDate.now()), 1)) }
    FormDialog("Nova parcela", onDismiss, { onAdd(month.ifBlank { null }, parseMoney(amount)); onDismiss() }, confirmEnabled = parseMoney(amount) > 0) {
        MoneyField("Valor (R$)", amount) { amount = it }
        MonthChoice("Mês", month, { month = it }, allowNone = true)
    }
}

@Composable
fun PayNextDialog(label: String, amount: Double, accounts: List<Account>, onConfirm: (Long?) -> Unit, onDismiss: () -> Unit) {
    var acc by remember { mutableStateOf(accounts.firstOrNull()?.id?.toString() ?: "") }
    FormDialog("Pagar $label", onDismiss, { onConfirm(acc.toLongOrNull()); onDismiss() }, confirmText = "Pagar ${brl(amount)}") {
        AccountChoice("Descontar de qual conta?", accounts, acc) { acc = it }
    }
}

@Composable
fun AporteDialog(month: String, current: Double, onSave: (Double) -> Unit, onReset: (() -> Unit)? = null, onDismiss: () -> Unit) {
    var v by remember { mutableStateOf(editMoney(current)) }
    FormDialog("Aporte de ${mLabel(month)}", onDismiss, { onSave(parseMoney(v)); onDismiss() }) {
        MoneyField("Valor (R$)", v) { v = it }
        if (onReset != null) SoftButton("Voltar ao aporte padrão", { onReset(); onDismiss() }, Modifier.fillMaxWidth())
    }
}

@Composable
fun SettingsDialog(s: Settings, onSave: (Settings) -> Unit, onDismiss: () -> Unit) {
    var income by remember { mutableStateOf(editMoney(s.income)) }
    var day by remember { mutableStateOf(s.incomeDay.toString()) }
    var mult by remember { mutableStateOf(s.multiplier.toInt().toString()) }
    var rate by remember { mutableStateOf(editMoney(s.realRate * 100)) }
    var months by remember { mutableStateOf(s.reserveMonths.toString()) }
    var aporte by remember { mutableStateOf(editMoney(s.aporteLongo)) }
    var start by remember { mutableStateOf(s.ipcaStart) }
    FormDialog("Parâmetros do plano", onDismiss, {
        onSave(s.copy(
            income = parseMoney(income), incomeDay = day.toIntOrNull()?.coerceIn(1, 31) ?: 10, multiplier = (mult.toDoubleOrNull() ?: 200.0).coerceAtLeast(1.0),
            realRate = (parseMoney(rate).takeIf { it > 0 } ?: 6.0) / 100, reserveMonths = months.toIntOrNull()?.coerceAtLeast(1) ?: 6,
            aporteLongo = parseMoney(aporte), ipcaStart = start
        )); onDismiss()
    }) {
        MoneyField("Renda mensal líquida (R$)", income) { income = it }
        Field("Dia em que a renda cai", day, { day = it.filter(Char::isDigit) }, KeyboardType.Number)
        Field("Multiplicador (200 = 6% real · 250 = conservador)", mult, { mult = it.filter(Char::isDigit) }, KeyboardType.Number)
        MoneyField("Rendimento real esperado (% ao ano)", rate) { rate = it }
        Field("Meses de reserva de emergência", months, { months = it.filter(Char::isDigit) }, KeyboardType.Number)
        MoneyField("Aporte mensal padrão (R$)", aporte) { aporte = it }
        MonthChoice("Início do balde IPCA+", start, { start = it }, allowNone = true)
    }
}

@Composable
fun GoalDialog(initial: Goal?, onSave: (Goal) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var target by remember { mutableStateOf(editMoney(initial?.targetValue ?: 0.0)) }
    var current by remember { mutableStateOf(editMoney(initial?.currentValue ?: 0.0)) }
    var cat by remember { mutableStateOf(initial?.category ?: "OUTRA") }
    FormDialog(if (initial == null) "Nova meta" else "Editar meta", onDismiss, {
        onSave((initial ?: Goal(name = "", targetValue = 0.0, currentValue = 0.0, category = cat)).copy(name = name.trim(), targetValue = parseMoney(target), currentValue = parseMoney(current), category = cat)); onDismiss()
    }, confirmEnabled = name.isNotBlank() && parseMoney(target) > 0, onDelete = onDelete?.let { { it(); onDismiss() } }) {
        Field("Nome (ex.: Viagem, Curso)", name, { name = it })
        MoneyField("Valor da meta (R$)", target) { target = it }
        MoneyField("Já separado (R$)", current) { current = it }
        Choice("Tipo", listOf("VIAGEM" to "Viagem", "ESTUDO" to "Estudo", "OUTRA" to "Outra"), cat) { cat = it }
    }
}


/** Edita um único número (renda, aporte, meses...). [decimal] = aceita centavos. */
@Composable
fun NumberDialog(title: String, label: String, initial: Double, decimal: Boolean = true, hint: String? = null, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var v by remember { mutableStateOf(if (initial == 0.0) "" else if (decimal) editMoney(initial) else initial.toLong().toString()) }
    FormDialog(title, onDismiss, { onSave(parseMoney(v)); onDismiss() }) {
        if (hint != null) Text(hint, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        if (decimal) MoneyField(label, v) { v = it } else Field(label, v, { v = it.filter(Char::isDigit) }, KeyboardType.Number)
    }
}

/** Edita um texto (nome do Dot, de uma categoria, de um grupo...). */
@Composable
fun TextDialog(title: String, label: String, initial: String, onSave: (String) -> Unit, onDelete: (() -> Unit)? = null, onDismiss: () -> Unit) {
    var v by remember { mutableStateOf(initial) }
    FormDialog(title, onDismiss, { onSave(v.trim()); onDismiss() }, confirmEnabled = v.isNotBlank(), onDelete = onDelete?.let { { it(); onDismiss() } }) {
        Field(label, v, { v = it })
    }
}

/** Escolhe um mês (ex.: início do IPCA+). */
@Composable
fun MonthPickDialog(title: String, current: String, allowNone: Boolean, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var m by remember { mutableStateOf(current) }
    FormDialog(title, onDismiss, { onSave(m); onDismiss() }) { MonthChoice("Mês", m, { m = it }, allowNone = allowNone) }
}

/** Cria um grupo de custo novo: nome e se é essencial (entra na reserva) ou opcional (pode ser cortado). */
@Composable
fun NewGroupDialog(onCreate: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("ESS") }
    FormDialog("Novo grupo de custos", onDismiss, { onCreate(name.trim(), kind == "ESS"); onDismiss() }, confirmEnabled = name.isNotBlank()) {
        Field("Nome do grupo", name, { name = it })
        Choice("Tipo", listOf("ESS" to "Essencial (entra na reserva)", "OPC" to "Opcional (pode ser cortado)"), kind) { kind = it }
    }
}
