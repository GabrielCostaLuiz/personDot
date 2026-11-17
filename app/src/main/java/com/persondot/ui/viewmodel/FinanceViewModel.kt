package com.persondot.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.persondot.data.Backup
import com.persondot.data.local.AppDatabase
import com.persondot.data.model.*
import com.persondot.data.repository.FinanceRepository
import com.persondot.domain.FinanceData
import com.persondot.domain.Forecast
import com.persondot.domain.addMonths
import com.persondot.domain.monthKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = FinanceRepository(AppDatabase.get(application).financeDao())

    val data: StateFlow<FinanceData> = repo.data.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinanceData())

    private fun run(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    /** Soma [delta] ao saldo da conta [accountId] (usado ao pagar/receber). */
    private suspend fun adjust(accountId: Long?, delta: Double) {
        val acc = data.value.accounts.firstOrNull { it.id == accountId } ?: return
        repo.save(acc.copy(balance = acc.balance + delta))
    }

    // ---- parâmetros e contas ----
    fun saveSettings(s: Settings) = run { repo.save(s.copy(id = 1)) }
    fun saveAccount(a: Account) = run { repo.save(a) }
    fun deleteAccount(a: Account) = run { repo.delete(a) }

    // ---- entradas a receber ----
    fun saveReceivable(r: Receivable) = run { repo.save(r) }
    fun deleteReceivable(r: Receivable) = run { repo.delete(r) }
    /** Marca como recebida e soma o valor na conta escolhida. */
    fun receive(r: Receivable, amount: Double, accountId: Long?) = run {
        repo.save(r.copy(amount = amount, received = true)); adjust(accountId, amount)
    }

    // ---- dívidas ----
    fun createDebt(name: String, note: String, amount: Double, count: Int, startMonth: String?) = run {
        repo.createDebt(name, note, amount, count, startMonth, ::addMonths)
    }
    fun deleteDebt(id: Long) = run { repo.deleteDebt(id) }
    fun saveInstallment(i: DebtInstallment) = run { repo.save(i) }
    fun deleteInstallment(i: DebtInstallment) = run { repo.delete(i) }
    fun addInstallment(debtId: Long, month: String?, amount: Double) = run { repo.save(DebtInstallment(debtId = debtId, month = month, amount = amount)) }
    /** Confirma o pagamento de uma parcela e desconta da conta escolhida. */
    fun payInstallment(i: DebtInstallment, accountId: Long?) = run { repo.save(i.copy(paid = true)); adjust(accountId, -i.amount) }
    /** Paga a próxima parcela em aberto da dívida. */
    fun payNext(debtId: Long, accountId: Long?) = run {
        val d = data.value.debts.firstOrNull { it.debt.id == debtId } ?: return@run
        val next = d.installments.filter { !it.paid }.minByOrNull { it.month ?: "9999" } ?: return@run
        repo.save(next.copy(paid = true)); adjust(accountId, -next.amount)
    }

    // ---- custos previstos ----
    fun saveFixed(f: FixedCost) = run { repo.save(f) }
    fun deleteFixed(f: FixedCost) = run { repo.delete(f) }

    /** Confirma o pagamento do custo naquele mês (valor pode ter mudado) e desconta da conta. */
    fun confirmForecast(f: Forecast, amount: Double, accountId: Long?) = run {
        repo.setEntry(f.cost.id, f.month, MonthEntry.PAGO, amount, accountId); adjust(accountId, -amount)
    }
    /** Desfaz a confirmação, devolvendo o valor à conta de onde saiu. */
    fun undoForecast(f: Forecast) = run {
        val e = f.entry ?: return@run
        if (e.status == MonthEntry.PAGO) adjust(e.accountId, e.amount ?: f.cost.amount)
        val amt = e.amount
        if (amt != null && amt != f.cost.amount) repo.setEntry(f.cost.id, f.month, MonthEntry.PREVISTO, amt, null) else repo.clearEntry(f.cost.id, f.month)
    }
    /** Ajusta o valor só daquele mês (continua previsto). */
    fun adjustForecast(f: Forecast, amount: Double) = run {
        if (amount == f.cost.amount) repo.clearEntry(f.cost.id, f.month) else repo.setEntry(f.cost.id, f.month, MonthEntry.PREVISTO, amount, null)
    }
    /** Tira o custo só daquele mês. */
    fun removeForecast(f: Forecast) = run { repo.setEntry(f.cost.id, f.month, MonthEntry.REMOVIDO, null, null) }
    /** Traz de volta um custo removido (ou limpa o ajuste). */
    fun restoreForecast(f: Forecast) = run { repo.clearEntry(f.cost.id, f.month) }
    /** Cancela o custo daquele mês em diante (ex.: cancelou a assinatura). Meses anteriores ficam como estavam. */
    fun cancelFrom(f: Forecast) = run { repo.save(f.cost.copy(endsBefore = f.month)) }

    // ---- gastos variáveis e aportes ----
    fun saveExpense(e: Expense, debitAccountId: Long? = null) = run {
        val isNew = e.id == 0L
        repo.save(e); if (isNew) adjust(debitAccountId, -e.amount)
    }
    fun deleteExpense(e: Expense) = run { repo.delete(e) }
    fun saveAporte(month: String, amount: Double) = run { repo.save(Aporte(month, amount)) }
    /** Volta o mês ao aporte padrão. */
    fun clearAporte(month: String) = run { repo.clearAporte(month) }

    // ---- metas ----
    fun saveGoal(g: Goal) = run { repo.save(g) }
    fun deleteGoal(g: Goal) = run { repo.delete(g) }

    // ---- zona de reflexão ----
    fun addReflection(r: Reflection) = run { repo.save(r) }
    fun deleteReflection(r: Reflection) = run { repo.delete(r) }
    /** Venceu o impulso: cancela a compra. */
    fun cancelReflection(r: Reflection) = run { repo.save(r.copy(isApproved = false, isCancelled = true, purchaseAwaitedTime = 24L)) }
    /** Decidiu comprar: à vista vira gasto lançado (e sai da primeira conta); no cartão vira dívida em 6x. */
    fun approveReflection(r: Reflection) = run {
        repo.save(r.copy(isApproved = true, isCancelled = false, purchaseAwaitedTime = 24L))
        val now = monthKey(LocalDate.now())
        if (r.wouldBuyCash) {
            repo.save(Expense(desc = r.name, amount = r.estimatedPrice, cat = "Compras", date = LocalDate.now().toString()))
            adjust(data.value.accounts.firstOrNull { it.kind != Account.KIND_IPCA }?.id, -r.estimatedPrice)
        } else {
            repo.createDebt("${r.name} (refletido)", "6x no cartão", r.estimatedPrice / 6.0, 6, addMonths(now, 1), ::addMonths)
        }
    }

    // ---- backup ----
    fun exportJson(): String = Backup.toJson(data.value)
    fun importJson(text: String, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { repo.replaceAll(Backup.fromJson(text)) }
            onDone(result)
        }
    }
    fun resetAll() = run { repo.clearAll() }
}
