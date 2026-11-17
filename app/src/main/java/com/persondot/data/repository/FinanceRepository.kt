package com.persondot.data.repository

import com.persondot.data.local.FinanceDao
import com.persondot.data.model.*
import com.persondot.domain.DebtWithInstallments
import com.persondot.domain.FinanceData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class FinanceRepository(private val dao: FinanceDao) {

    private data class P1(val s: Settings?, val a: List<Account>, val r: List<Receivable>, val d: List<Debt>, val i: List<DebtInstallment>)
    private data class P2(val f: List<FixedCost>, val p: List<Planned>, val e: List<Expense>, val ap: List<Aporte>, val rf: List<Reflection>)

    private val part1 = combine(dao.settings(), dao.accounts(), dao.receivables(), dao.debts(), dao.installments(), ::P1)
    private val part2 = combine(dao.fixedCosts(), dao.planned(), dao.expenses(), dao.aportes(), dao.reflections(), ::P2)

    /** Tudo o que o app mostra, num único objeto reativo. */
    val data: Flow<FinanceData> = combine(part1, part2, dao.goals()) { p1, p2, goals ->
        val byDebt = p1.i.groupBy { it.debtId }
        FinanceData(
            settings = p1.s ?: Settings(), accounts = p1.a, receivables = p1.r,
            debts = p1.d.map { DebtWithInstallments(it, byDebt[it.id].orEmpty()) },
            fixed = p2.f, planned = p2.p, expenses = p2.e, aportes = p2.ap.associate { it.month to it.amount },
            reflections = p2.rf, goals = goals
        )
    }

    suspend fun save(s: Settings) = dao.upsert(s)
    suspend fun save(a: Account) = dao.upsert(a)
    suspend fun save(r: Receivable) = dao.upsert(r)
    suspend fun save(i: DebtInstallment) = dao.upsert(i)
    suspend fun save(f: FixedCost) = dao.upsert(f)
    suspend fun save(p: Planned) = dao.upsert(p)
    suspend fun save(e: Expense) = dao.upsert(e)
    suspend fun save(a: Aporte) = dao.upsert(a)
    suspend fun save(r: Reflection) = dao.upsert(r)
    suspend fun save(g: Goal) = dao.upsert(g)
    suspend fun saveDebt(d: Debt): Long = dao.upsert(d)

    suspend fun delete(a: Account) = dao.delete(a)
    suspend fun delete(r: Receivable) = dao.delete(r)
    suspend fun delete(i: DebtInstallment) = dao.delete(i)
    suspend fun delete(f: FixedCost) = dao.delete(f)
    suspend fun delete(p: Planned) = dao.delete(p)
    suspend fun delete(e: Expense) = dao.delete(e)
    suspend fun delete(r: Reflection) = dao.delete(r)
    suspend fun delete(g: Goal) = dao.delete(g)
    suspend fun deleteDebt(id: Long) = dao.deleteDebt(id)

    /** Cria uma dívida com [count] parcelas iguais a partir de [startMonth] (ou sem data). */
    suspend fun createDebt(name: String, note: String, amount: Double, count: Int, startMonth: String?, monthAdder: (String, Int) -> String) {
        val id = dao.upsert(Debt(name = name, note = note))
        repeat(maxOf(1, count)) { i ->
            dao.upsert(DebtInstallment(debtId = id, month = startMonth?.let { monthAdder(it, i) }, amount = amount))
        }
    }

    /** Substitui TODOS os dados pelos do backup (importação). */
    suspend fun replaceAll(d: FinanceData) {
        dao.clearAll()
        dao.upsert(d.settings.copy(id = 1))
        d.accounts.forEach { dao.upsert(it.copy(id = 0)) }
        d.receivables.forEach { dao.upsert(it.copy(id = 0)) }
        d.debts.forEach { dw ->
            val id = dao.upsert(dw.debt.copy(id = 0))
            dw.installments.forEach { dao.upsert(it.copy(id = 0, debtId = id)) }
        }
        d.fixed.forEach { dao.upsert(it.copy(id = 0)) }
        d.planned.forEach { dao.upsert(it.copy(id = 0)) }
        d.expenses.forEach { dao.upsert(it.copy(id = 0)) }
        d.aportes.forEach { (m, v) -> dao.upsert(Aporte(m, v)) }
        d.reflections.forEach { dao.upsert(it.copy(id = 0)) }
        d.goals.forEach { dao.upsert(it.copy(id = 0)) }
    }

    suspend fun clearAll() = dao.clearAll()
}
