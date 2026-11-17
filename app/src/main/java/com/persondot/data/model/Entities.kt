package com.persondot.data.model

import androidx.room.Entity
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

/** Custo fixo mensal. grp: CASA (essencial), TRABALHO ou OPCIONAL. */
@Entity(tableName = "fixed_costs")
data class FixedCost(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val grp: String = GRP_CASA,
    val onCard: Boolean = false
) {
    companion object {
        const val GRP_CASA = "CASA"
        const val GRP_TRABALHO = "TRABALHO"
        const val GRP_OPCIONAL = "OPCIONAL"
    }
}

/** Gasto único já previsto (ex.: procedimento, presente). */
@Entity(tableName = "planned")
data class Planned(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val desc: String,
    val amount: Double,
    val month: String,
    val note: String = "",
    val paid: Boolean = false
)

/** Gasto variável lançado no dia a dia. date = "AAAA-MM-DD". */
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
    val helpsCarGoal: Boolean,
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

/** Meta com valor-alvo (ex.: carro à vista). category: CARRO, VIAGEM, OUTRA. */
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
