package com.persondot.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.persondot.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    // ---- leitura (Flows) ----
    @Query("SELECT * FROM settings WHERE id = 1") fun settings(): Flow<Settings?>
    @Query("SELECT * FROM accounts ORDER BY id") fun accounts(): Flow<List<Account>>
    @Query("SELECT * FROM receivables ORDER BY id") fun receivables(): Flow<List<Receivable>>
    @Query("SELECT * FROM debts ORDER BY id") fun debts(): Flow<List<Debt>>
    @Query("SELECT * FROM debt_installments ORDER BY month, id") fun installments(): Flow<List<DebtInstallment>>
    @Query("SELECT * FROM fixed_costs ORDER BY amount DESC, id") fun fixedCosts(): Flow<List<FixedCost>>
    @Query("SELECT * FROM month_entries") fun monthEntries(): Flow<List<MonthEntry>>
    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC") fun expenses(): Flow<List<Expense>>
    @Query("SELECT * FROM aportes") fun aportes(): Flow<List<Aporte>>
    @Query("SELECT * FROM reflections ORDER BY timestamp DESC") fun reflections(): Flow<List<Reflection>>
    @Query("SELECT * FROM goals ORDER BY id") fun goals(): Flow<List<Goal>>

    @Query("SELECT * FROM month_entries WHERE fixedId = :fixedId AND month = :month LIMIT 1")
    suspend fun entryOf(fixedId: Long, month: String): MonthEntry?

    // ---- escrita ----
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(s: Settings)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(a: Account): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(r: Receivable): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(d: Debt): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(i: DebtInstallment): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(f: FixedCost): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(m: MonthEntry): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: Expense): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(a: Aporte)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(r: Reflection): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(g: Goal): Long

    @Delete suspend fun delete(a: Account)
    @Delete suspend fun delete(r: Receivable)
    @Delete suspend fun delete(i: DebtInstallment)
    @Delete suspend fun delete(f: FixedCost)
    @Delete suspend fun delete(m: MonthEntry)
    @Delete suspend fun delete(e: Expense)
    @Delete suspend fun delete(r: Reflection)
    @Delete suspend fun delete(g: Goal)

    @Query("DELETE FROM debt_installments WHERE debtId = :debtId") suspend fun deleteInstallmentsOf(debtId: Long)
    @Query("DELETE FROM debts WHERE id = :debtId") suspend fun deleteDebtRow(debtId: Long)
    @Query("DELETE FROM month_entries WHERE fixedId = :fixedId") suspend fun deleteEntriesOf(fixedId: Long)
    @Query("DELETE FROM aportes WHERE month = :month") suspend fun deleteAporte(month: String)

    @Transaction
    suspend fun deleteDebt(debtId: Long) { deleteInstallmentsOf(debtId); deleteDebtRow(debtId) }

    @Transaction
    suspend fun deleteFixedWithEntries(f: FixedCost) { deleteEntriesOf(f.id); delete(f) }

    // ---- limpeza total (usada por importar/apagar) ----
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM receivables") suspend fun clearReceivables()
    @Query("DELETE FROM debts") suspend fun clearDebts()
    @Query("DELETE FROM debt_installments") suspend fun clearInstallments()
    @Query("DELETE FROM fixed_costs") suspend fun clearFixed()
    @Query("DELETE FROM month_entries") suspend fun clearEntries()
    @Query("DELETE FROM expenses") suspend fun clearExpenses()
    @Query("DELETE FROM aportes") suspend fun clearAportes()
    @Query("DELETE FROM reflections") suspend fun clearReflections()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM settings") suspend fun clearSettings()

    @Transaction
    suspend fun clearAll() {
        clearAccounts(); clearReceivables(); clearInstallments(); clearDebts(); clearEntries(); clearFixed()
        clearExpenses(); clearAportes(); clearReflections(); clearGoals(); clearSettings()
    }
}
