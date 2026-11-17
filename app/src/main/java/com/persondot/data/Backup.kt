package com.persondot.data

import com.persondot.data.model.*
import com.persondot.domain.DebtWithInstallments
import com.persondot.domain.FinanceData
import org.json.JSONArray
import org.json.JSONObject

/** Backup em JSON: o mesmo arquivo serve para exportar, importar e levar seus dados para outro aparelho. */
object Backup {

    private fun JSONObject.str(k: String, def: String = "") = if (has(k) && !isNull(k)) getString(k) else def
    private fun JSONObject.dbl(k: String, def: Double = 0.0) = if (has(k) && !isNull(k)) getDouble(k) else def
    private fun JSONObject.int(k: String, def: Int = 0) = if (has(k) && !isNull(k)) getInt(k) else def
    private fun JSONObject.bool(k: String, def: Boolean = false) = if (has(k) && !isNull(k)) getBoolean(k) else def
    private fun JSONObject.strOrNull(k: String) = if (has(k) && !isNull(k)) getString(k).ifBlank { null } else null
    private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }

    /** Lê o JSON. Lança IllegalArgumentException se não parecer um backup do app. */
    fun fromJson(text: String): FinanceData {
        val o = try { JSONObject(text) } catch (e: Exception) { throw IllegalArgumentException("Arquivo inválido", e) }
        require(o.has("accounts") && o.has("debts")) { "Arquivo inválido" }

        val s = o.optJSONObject("settings") ?: JSONObject()
        val settings = Settings(
            income = s.dbl("income"), incomeDay = s.int("incomeDay", 10), multiplier = s.dbl("multiplier", 200.0),
            reserveMonths = s.int("reserveMonths", 6), realRate = s.dbl("realRate", 0.06),
            aporteLongo = s.dbl("aporteLongo", 1000.0), ipcaStart = s.str("ipcaStart")
        )
        val accounts = o.optJSONArray("accounts").objs().map {
            Account(name = it.str("name"), balance = it.dbl("balance"), kind = it.str("kind", "RESERVA").uppercase(), note = it.str("note"))
        }
        val receivables = o.optJSONArray("receivables").objs().map { Receivable(desc = it.str("desc"), amount = it.dbl("amount"), received = it.bool("received")) }
        val debts = o.optJSONArray("debts").objs().map { d ->
            DebtWithInstallments(
                Debt(name = d.str("name"), note = d.str("note")),
                d.optJSONArray("installments").objs().map { i ->
                    DebtInstallment(debtId = 0, month = i.strOrNull("month"), amount = i.dbl("amount"), paid = i.bool("paid"), includesSubs = i.bool("includesSubs"))
                }
            )
        }
        val fixed = o.optJSONArray("fixed").objs().map {
            FixedCost(name = it.str("name"), amount = it.dbl("amount"), grp = it.str("group", "CASA").uppercase(), onCard = it.bool("card"))
        }
        val planned = o.optJSONArray("planned").objs().map {
            Planned(desc = it.str("desc"), amount = it.dbl("amount"), month = it.str("month"), note = it.str("note"), paid = it.bool("paid"))
        }
        val expenses = o.optJSONArray("expenses").objs().map { Expense(desc = it.str("desc"), amount = it.dbl("amount"), cat = it.str("cat", "Outros"), date = it.str("date")) }
        val aportes = o.optJSONObject("aportes")?.let { a -> a.keys().asSequence().associateWith { a.getDouble(it) } } ?: emptyMap()
        val reflections = o.optJSONArray("reflections").objs().map {
            Reflection(
                name = it.str("name"), estimatedPrice = it.dbl("estimatedPrice"), category = it.str("category"), isNecessity = it.bool("isNecessity"),
                helpsCarGoal = it.bool("helpsCarGoal"), willUseMoreThanOneYear = it.bool("willUseMoreThanOneYear"), wouldBuyCash = it.bool("wouldBuyCash"),
                emotionSource = it.str("emotionSource"), reflectionComment = it.str("reflectionComment"), timestamp = it.optLong("timestamp", 0L),
                timerDurationHours = it.int("timerDurationHours", 24), isApproved = it.bool("isApproved"), isCancelled = it.bool("isCancelled"),
                purchaseAwaitedTime = it.optLong("purchaseAwaitedTime", 0L)
            )
        }
        val goals = o.optJSONArray("goals").objs().map {
            Goal(name = it.str("name"), targetValue = it.dbl("targetValue"), currentValue = it.dbl("currentValue"), category = it.str("category", "OUTRA"),
                dueDate = it.optLong("dueDate", 0L), note = it.str("note"))
        }
        return FinanceData(settings, accounts, receivables, debts, fixed, planned, expenses, aportes, reflections, goals)
    }

    fun toJson(d: FinanceData): String {
        val o = JSONObject()
        o.put("v", 1)
        o.put("settings", JSONObject().apply {
            put("income", d.settings.income); put("incomeDay", d.settings.incomeDay); put("multiplier", d.settings.multiplier)
            put("reserveMonths", d.settings.reserveMonths); put("realRate", d.settings.realRate)
            put("aporteLongo", d.settings.aporteLongo); put("ipcaStart", d.settings.ipcaStart)
        })
        o.put("accounts", JSONArray(d.accounts.map { JSONObject().put("name", it.name).put("balance", it.balance).put("kind", it.kind).put("note", it.note) }))
        o.put("receivables", JSONArray(d.receivables.map { JSONObject().put("desc", it.desc).put("amount", it.amount).put("received", it.received) }))
        o.put("debts", JSONArray(d.debts.map { dw ->
            JSONObject().put("name", dw.debt.name).put("note", dw.debt.note).put("installments", JSONArray(dw.installments.map {
                JSONObject().put("month", it.month ?: JSONObject.NULL).put("amount", it.amount).put("paid", it.paid).put("includesSubs", it.includesSubs)
            }))
        }))
        o.put("fixed", JSONArray(d.fixed.map { JSONObject().put("name", it.name).put("amount", it.amount).put("group", it.grp).put("card", it.onCard) }))
        o.put("planned", JSONArray(d.planned.map { JSONObject().put("desc", it.desc).put("amount", it.amount).put("month", it.month).put("note", it.note).put("paid", it.paid) }))
        o.put("expenses", JSONArray(d.expenses.map { JSONObject().put("desc", it.desc).put("amount", it.amount).put("cat", it.cat).put("date", it.date) }))
        o.put("aportes", JSONObject().apply { d.aportes.forEach { (k, v) -> put(k, v) } })
        o.put("reflections", JSONArray(d.reflections.map {
            JSONObject().put("name", it.name).put("estimatedPrice", it.estimatedPrice).put("category", it.category).put("isNecessity", it.isNecessity)
                .put("helpsCarGoal", it.helpsCarGoal).put("willUseMoreThanOneYear", it.willUseMoreThanOneYear).put("wouldBuyCash", it.wouldBuyCash)
                .put("emotionSource", it.emotionSource).put("reflectionComment", it.reflectionComment).put("timestamp", it.timestamp)
                .put("timerDurationHours", it.timerDurationHours).put("isApproved", it.isApproved).put("isCancelled", it.isCancelled)
                .put("purchaseAwaitedTime", it.purchaseAwaitedTime)
        }))
        o.put("goals", JSONArray(d.goals.map {
            JSONObject().put("name", it.name).put("targetValue", it.targetValue).put("currentValue", it.currentValue).put("category", it.category)
                .put("dueDate", it.dueDate).put("note", it.note)
        }))
        return o.toString(2)
    }
}
