package com.persondot.domain

import java.time.LocalDate

/** Situação do faturamento anual frente ao limite do MEI. O limite é um dado seu (a regra pode mudar). */
data class MeiStatus(
    val limit: Double,
    val ytd: Double,                       // faturamento do ano até o mês atual
    val projectedYear: Double,             // fechamento do ano se o ritmo continuar
    val avgMonthly: Double,
    val crossMonth: String?,               // mês em que o acumulado passa do limite, se passar
    val usedPct: Double,
    val monthly: List<Pair<String, Double>>,
    val estimatedMonths: Int               // meses sem registro real (usei a renda como estimativa)
)

/** Faturamento de um mês: o registrado, ou a renda mensal como estimativa. */
fun FinanceData.revenueOf(m: String): Double = revenues.firstOrNull { it.month == m }?.amount ?: settings.income

fun FinanceData.mei(today: LocalDate = LocalDate.now()): MeiStatus? {
    if (!settings.meiOn || settings.meiLimit <= 0 || (settings.income <= 0 && revenues.isEmpty())) return null
    val year = today.year
    val months = (1..12).map { "%04d-%02d".format(year, it) }
    val current = monthKey(today)
    val monthly = months.map { it to revenueOf(it) }
    val ytd = monthly.filter { it.first <= current }.sumOf { it.second }
    val projected = monthly.sumOf { it.second }
    var cum = 0.0
    var cross: String? = null
    for ((m, v) in monthly) { cum += v; if (cross == null && cum > settings.meiLimit) cross = m }
    val estimated = monthly.count { (m, _) -> m <= current && revenues.none { it.month == m } }
    val elapsed = months.indexOf(current) + 1
    return MeiStatus(settings.meiLimit, ytd, projected, ytd / elapsed, cross, ytd / settings.meiLimit, monthly, estimated)
}
