package com.persondot.data.model

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
    val ipcaStart: String = ""           // mês (AAAA-MM) em que o IPCA+ começa
)

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
