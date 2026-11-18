package com.persondot.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Parâmetros do plano (uma única linha, id = 1). */
@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = 1,
    val income: Double = 0.0,            // renda mensal líquida
    val incomeDay: Int = 10,             // dia em que a renda cai
    val multiplier: Double = 200.0,      // regra dos 200 (200 = 6% real ao ano; 250 = mais conservador)
    val reserveMonths: Int = 6,          // meses de essenciais na reserva de emergência
    val realRate: Double = 0.06,         // rendimento real anual esperado
    val aporteLongo: Double = 1000.0,    // aporte mensal padrão (depois da reserva)
    val ipcaStart: String = "",          // mês (AAAA-MM) em que o IPCA+ começa
    /** Categorias dos gastos do dia a dia, separadas por "|". Editável em Ajustes. */
    @ColumnInfo(defaultValue = "'$DEFAULT_CATEGORIES'") val categories: String = DEFAULT_CATEGORIES,
    /** Nomes dos 3 grupos de custo (casa, trabalho, opcionais), separados por "|". Editável em Ajustes. */
    @ColumnInfo(defaultValue = "'$DEFAULT_GROUP_NAMES'") val groupNames: String = DEFAULT_GROUP_NAMES,
    /** Acompanhar o limite anual de faturamento do MEI? E qual é o limite (editável: a regra pode mudar). */
    @ColumnInfo(defaultValue = "1") val meiOn: Boolean = true,
    @ColumnInfo(defaultValue = "81000.0") val meiLimit: Double = 81000.0,
    /** Grupos de custo criados pela pessoa, além dos 3 padrão: "id~nome~ESS" (essencial) ou "id~nome~OPC" (opcional), separados por "|". */
    @ColumnInfo(defaultValue = "''") val customGroups: String = ""
)

const val DEFAULT_CATEGORIES = "Mercado extra|Alimentação fora|Lazer|Transporte|Beleza e saúde|Compras|Outros"
const val DEFAULT_GROUP_NAMES = "Casa · essenciais|Trabalho|Opcionais"

val Settings.categoryList: List<String> get() = categories.split('|').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("Outros") }

/** Um grupo de custo. essencial = entra na reserva de emergência; senão é opcional (pode ser cortado). */
data class GroupDef(val id: String, val name: String, val essential: Boolean, val custom: Boolean = false)

/** Os 3 grupos padrão (com os nomes que a pessoa escolheu) + os grupos que ela criou. */
val Settings.groups: List<GroupDef> get() = listOf(
    GroupDef(FixedCost.GRP_CASA, standardName(0), true), GroupDef(FixedCost.GRP_TRABALHO, standardName(1), true), GroupDef(FixedCost.GRP_OPCIONAL, standardName(2), false)
) + customGroups.split('|').mapNotNull { e ->
    val p = e.split('~')
    if (p.size >= 3 && p[0].isNotBlank()) GroupDef(p[0], p[1], p[2] == "ESS", custom = true) else null
}

private fun Settings.standardName(i: Int) = groupNames.split('|').getOrNull(i)?.trim()?.ifEmpty { null } ?: DEFAULT_GROUP_NAMES.split('|')[i]

fun List<GroupDef>.encodeCustom(): String = filter { it.custom }.joinToString("|") { "${it.id}~${it.name.replace("|", " ").replace("~", " ")}~${if (it.essential) "ESS" else "OPC"}" }

/** Nome do grupo de custo (padrão ou criado). */
fun Settings.groupLabel(grp: String): String = groups.firstOrNull { it.id == grp }?.name ?: standardName(2)
fun Settings.isEssential(grp: String) = groups.firstOrNull { it.id == grp }?.essential == true
fun Settings.isOptional(grp: String) = groups.firstOrNull { it.id == grp }?.essential == false
/** Contas "de casa": essenciais que não são de trabalho (base das contas riscadas). */
fun Settings.isHome(grp: String) = isEssential(grp) && grp != FixedCost.GRP_TRABALHO

/** Conta onde o dinheiro está. kind: RESERVA (liquidez diária) ou IPCA (balde das contas riscadas). */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val balance: Double,
    val kind: String = KIND_RESERVA,
    val note: String = ""
) {
    companion object {
        const val KIND_RESERVA = "RESERVA"
        const val KIND_IPCA = "IPCA"
    }
}

@Entity(tableName = "receivables")
data class Receivable(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val desc: String,
    val amount: Double,
    val received: Boolean = false
)

@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String = ""
)

/** Uma parcela/fatura de uma dívida. month = "AAAA-MM" ou null (sem data definida). */
@Entity(tableName = "debt_installments")
data class DebtInstallment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val month: String?,
    val amount: Double,
    val paid: Boolean = false,
    val includesSubs: Boolean = false   // a fatura deste mês já inclui as assinaturas do cartão
)

/**
 * Custo mensal PREVISTO. Todo mês ele aparece como previsão até você confirmar o pagamento.
 * grp: CASA (essencial), TRABALHO ou OPCIONAL. Vale de [startsAt] (inclusive) até [endsBefore] (exclusive); null = sem limite.
 */
@Entity(tableName = "fixed_costs")
data class FixedCost(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val grp: String = GRP_CASA,
    val onCard: Boolean = false,
    val startsAt: String? = null,
    val endsBefore: String? = null
) {
    companion object {
        const val GRP_CASA = "CASA"
        const val GRP_TRABALHO = "TRABALHO"
        const val GRP_OPCIONAL = "OPCIONAL"
    }
}

/**
 * Ajuste de UM mês de um custo previsto. Sem registro = previsto pelo valor padrão.
 * status: PREVISTO (só ajustou o valor), PAGO (confirmado) ou REMOVIDO (não existe neste mês).
 * [accountId] guarda de qual conta o pagamento saiu, para poder desfazer.
 */
@Entity(tableName = "month_entries", indices = [Index(value = ["fixedId", "month"], unique = true)])
data class MonthEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fixedId: Long,
    val month: String,
    val status: String,
    val amount: Double? = null,
    val accountId: Long? = null
) {
    companion object {
        const val PREVISTO = "PREVISTO"
        const val PAGO = "PAGO"
        const val REMOVIDO = "REMOVIDO"
    }
}

/** Gasto variável lançado no dia a dia (já aconteceu). date = "AAAA-MM-DD". */
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val desc: String,
    val amount: Double,
    val cat: String,
    val date: String
)

/** Aporte planejado para um mês específico (sobrescreve o aporte padrão). */
@Entity(tableName = "aportes")
data class Aporte(
    @PrimaryKey val month: String,
    val amount: Double
)

/** Zona de reflexão: compra desejada que espera o resfriamento antes de decidir. */
@Entity(tableName = "reflections")
data class Reflection(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val estimatedPrice: Double,
    val category: String,
    val isNecessity: Boolean,
    val helpsCarGoal: Boolean,           // "me aproxima das minhas metas?" (nome antigo mantido no banco)
    val willUseMoreThanOneYear: Boolean,
    val wouldBuyCash: Boolean,
    val emotionSource: String,
    val reflectionComment: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val timerDurationHours: Int = 24,
    val isApproved: Boolean = false,
    val isCancelled: Boolean = false,    // true = venceu o impulso
    val purchaseAwaitedTime: Long = 0L
)

/** Meta com valor-alvo. category: VIAGEM, ESTUDO ou OUTRA. */
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val targetValue: Double,
    val currentValue: Double,
    val category: String,
    val dueDate: Long = 0L,
    val note: String = ""
)


/** Faturamento recebido em um mês (para o limite do MEI). Mês sem registro usa a renda mensal como estimativa. */
@Entity(tableName = "revenues")
data class Revenue(@PrimaryKey val month: String, val amount: Double)

/** Foto do patrimônio em um mês (atualizada sozinha enquanto você usa o app). Alimenta os relatórios. */
@Entity(tableName = "snapshots")
data class Snapshot(
    @PrimaryKey val month: String,
    val inHand: Double,   // em contas de liquidez diária
    val ipca: Double,     // no balde IPCA+
    val owed: Double      // parcelas em aberto
)
