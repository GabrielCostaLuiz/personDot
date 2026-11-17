package com.persondot.domain

import com.persondot.data.model.*
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong

/* Regras do PersonDot. Tudo aqui é puro Kotlin (sem Android) para poder ser testado. */

data class DebtWithInstallments(val debt: Debt, val installments: List<DebtInstallment>) {
    val remaining get() = installments.filter { !it.paid }.sumOf { it.amount }
    val total get() = installments.sumOf { it.amount }
}

data class FinanceData(
    val settings: Settings = Settings(),
    val accounts: List<Account> = emptyList(),
    val receivables: List<Receivable> = emptyList(),
    val debts: List<DebtWithInstallments> = emptyList(),
    val fixed: List<FixedCost> = emptyList(),
    val monthEntries: List<MonthEntry> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val aportes: Map<String, Double> = emptyMap(),
    val reflections: List<Reflection> = emptyList(),
    val goals: List<Goal> = emptyList()
) {
    val isEmpty get() = accounts.isEmpty() && debts.isEmpty() && fixed.isEmpty()
}

/** Para onde um passo do "norte" leva. */
enum class Dest { PAINEL, GASTOS, DOT, DECIDIR, FUTURO }

// ---------- formatação e meses ----------
private val ptBr = Locale.Builder().setLanguage("pt").setRegion("BR").build()
fun brl(v: Double): String = NumberFormat.getCurrencyInstance(ptBr).format(v).replace(' ', ' ')
fun brl0(v: Double): String = NumberFormat.getCurrencyInstance(ptBr).apply { maximumFractionDigits = 0; minimumFractionDigits = 0 }.format(v.roundToLong()).replace(' ', ' ')

private val MESES = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
fun monthKey(d: LocalDate): String = YearMonth.from(d).toString()
fun addMonths(key: String, n: Int): String = YearMonth.parse(key).plusMonths(n.toLong()).toString()
fun monthsBetween(a: String, b: String): Int = ChronoUnit.MONTHS.between(YearMonth.parse(a), YearMonth.parse(b)).toInt()
fun mLabel(key: String?): String {
    if (key.isNullOrBlank()) return "sem data"
    val ym = YearMonth.parse(key)
    return "${MESES[ym.monthValue - 1]}/${(ym.year % 100).toString().padStart(2, '0')}"
}
/** Próximos 14 meses a partir do mês seguinte. */
fun visibleMonths(today: LocalDate = LocalDate.now()): List<String> {
    val first = addMonths(monthKey(today), 1)
    return List(14) { addMonths(first, it) }
}

// ---------- previsões ----------
enum class ForecastStatus { PREVISTO, PAGO, REMOVIDO }

/** Um custo previsto em um mês, já com ajustes e situação (previsto, pago ou removido). */
data class Forecast(
    val cost: FixedCost, val month: String, val amount: Double,
    val status: ForecastStatus, val entry: MonthEntry?, val inInvoice: Boolean
) {
    val adjusted get() = abs(amount - cost.amount) > 0.004
    /** Entra nas somas do mês? Removido ou já dentro da fatura do cartão não entra. */
    val counts get() = status != ForecastStatus.REMOVIDO && !inInvoice
}

fun FixedCost.activeIn(m: String): Boolean = (startsAt == null || m >= startsAt) && (endsBefore == null || m < endsBefore)

fun FinanceData.subsIncluded(m: String): Boolean = debts.any { d -> d.installments.any { it.month == m && it.includesSubs } }

fun FinanceData.forecasts(m: String): List<Forecast> {
    val inv = subsIncluded(m)
    return fixed.filter { it.activeIn(m) }.map { f ->
        val e = monthEntries.firstOrNull { it.fixedId == f.id && it.month == m }
        val st = when (e?.status) { MonthEntry.PAGO -> ForecastStatus.PAGO; MonthEntry.REMOVIDO -> ForecastStatus.REMOVIDO; else -> ForecastStatus.PREVISTO }
        Forecast(f, m, e?.amount ?: f.amount, st, e, inv && f.onCard)
    }
}

// ---------- números centrais ----------
data class Core(
    val ipca: Double, val inHand: Double, val toReceive: Double, val owed: Double,
    val essentials: Double, val reserveTarget: Double, val freeNow: Double, val free: Double
)

fun FinanceData.core(): Core {
    val liquid = accounts.filter { it.kind != Account.KIND_IPCA }
    val ipca = accounts.filter { it.kind == Account.KIND_IPCA }.sumOf { it.balance }
    val inHand = liquid.sumOf { it.balance }
    val toReceive = receivables.filter { !it.received }.sumOf { it.amount }
    val owed = debts.sumOf { it.remaining }
    val essentials = fixed.filter { it.grp == FixedCost.GRP_CASA || it.grp == FixedCost.GRP_TRABALHO }.sumOf { it.amount }
    val reserveTarget = essentials * settings.reserveMonths
    val freeNow = inHand - owed
    return Core(ipca, inHand, toReceive, owed, essentials, reserveTarget, freeNow, freeNow + toReceive)
}

// ---------- plano de um mês ----------
data class InstLine(val debtName: String, val inst: DebtInstallment)

data class MonthPlan(
    val month: String, val forecasts: List<Forecast>, val inst: List<InstLine>, val subsIncluded: Boolean,
    val income: Double, val casa: Double, val trabalho: Double, val opcional: Double,
    val parcelas: Double, val parcelasPagas: Double, val pagoFixos: Double,
    val aporte: Double, val spent: Double, val sobra: Double
) {
    val fixedTotal get() = casa + trabalho + opcional
    /** Tudo que está previsto para sair no mês (fixos + parcelas). */
    val previsto get() = fixedTotal + parcelas
    /** Parte do previsto que você já confirmou como paga. */
    val pago get() = pagoFixos + parcelasPagas
    val aPagar get() = previsto - pago
    /** Sobra depois dos gastos variáveis já lançados. */
    val restante get() = sobra - spent
}

fun FinanceData.monthPlan(m: String): MonthPlan {
    val fc = forecasts(m)
    val counted = fc.filter { it.counts }
    fun by(g: String) = counted.filter { it.cost.grp == g }.sumOf { it.amount }
    val inst = debts.flatMap { d -> d.installments.filter { it.month == m }.map { InstLine(d.debt.name, it) } }
    val casa = by(FixedCost.GRP_CASA); val trabalho = by(FixedCost.GRP_TRABALHO); val opcional = by(FixedCost.GRP_OPCIONAL)
    val parcelas = inst.sumOf { it.inst.amount }
    val parcelasPagas = inst.filter { it.inst.paid }.sumOf { it.inst.amount }
    val pagoFixos = counted.filter { it.status == ForecastStatus.PAGO }.sumOf { it.amount }
    val aporte = aportes[m] ?: settings.aporteLongo
    val spent = expenses.filter { it.date.startsWith(m) }.sumOf { it.amount }
    val sobra = settings.income - casa - trabalho - opcional - parcelas - aporte
    return MonthPlan(m, fc, inst, subsIncluded(m), settings.income, casa, trabalho, opcional, parcelas, parcelasPagas, pagoFixos, aporte, spent, sobra)
}

/** O que ainda falta confirmar em um mês: previsões em aberto e parcelas não pagas. */
data class DueItem(val label: String, val amount: Double, val forecast: Forecast?, val inst: InstLine?)

fun FinanceData.dueIn(m: String): List<DueItem> {
    val plan = monthPlan(m)
    val f = plan.forecasts.filter { it.counts && it.status == ForecastStatus.PREVISTO }.map { DueItem(it.cost.name, it.amount, it, null) }
    val i = plan.inst.filter { !it.inst.paid }.map { DueItem(it.debtName, it.inst.amount, null, it) }
    return (i + f).sortedByDescending { it.amount }
}

// ---------- corte de opcionais ----------
data class Cut(val items: List<Forecast>) {
    val monthly get() = items.sumOf { it.amount }
    val yearly get() = monthly * 12
}

/** Mês de referência para falar de corte: o primeiro dos próximos meses cuja fatura NÃO já embute as assinaturas. */
fun FinanceData.cutRefMonth(today: LocalDate = LocalDate.now()): String {
    val next = addMonths(monthKey(today), 1)
    return (0..11).map { addMonths(next, it) }.firstOrNull { !subsIncluded(it) } ?: next
}

/** O que sairia se você cortasse todos os opcionais ainda não pagos do mês [m]. */
fun FinanceData.cutOptional(m: String): Cut =
    Cut(forecasts(m).filter { it.cost.grp == FixedCost.GRP_OPCIONAL && it.counts && it.status == ForecastStatus.PREVISTO }.sortedByDescending { it.amount })

// ---------- evolução: reserva, contas riscadas, marcos ----------
enum class Level { OK, WARN, BAD }

/** Meses de aporte (do plano) até acumular [gap]; devolve o mês em que fecha, ou null se passar de 10 anos. */
private fun FinanceData.etaForGap(gap: Double, extraMonthly: Double, today: LocalDate): String? {
    val now = monthKey(today)
    if (gap <= 0) return now
    var cum = 0.0
    var m = addMonths(now, 1)
    repeat(120) {
        cum += (aportes[m] ?: settings.aporteLongo) + extraMonthly
        if (cum >= gap) return m
        m = addMonths(m, 1)
    }
    return null
}

fun FinanceData.reserveEta(extraMonthly: Double = 0.0, today: LocalDate = LocalDate.now()): String? =
    etaForGap(core().let { it.reserveTarget - it.free }, extraMonthly, today)

/** Meses até acumular [target] partindo de [bal], aportando [a] por mês a taxa mensal [rm]. */
fun monthsTo(target: Double, bal: Double, a: Double, rm: Double): Double {
    if (bal >= target) return 0.0
    if (a <= 0) return Double.POSITIVE_INFINITY
    val k = a / rm
    return ln((target + k) / (bal + k)) / ln(1 + rm)
}

data class Riscada(val cost: FixedCost, val target: Double, val cum: Double, val done: Boolean, val eta: String?, val pct: Double)

/** Contas da casa da menor para a maior. O IPCA+ só começa depois que a reserva fecha. */
fun FinanceData.riscadas(today: LocalDate = LocalDate.now(), extraMonthly: Double = 0.0): List<Riscada> {
    val rm = settings.realRate / 12
    val ipca = accounts.filter { it.kind == Account.KIND_IPCA }.sumOf { it.balance }
    val now = monthKey(today)
    val afterReserve = reserveEta(extraMonthly, today)?.let { addMonths(maxOf(it, now), 1) }
    val start = listOfNotNull(settings.ipcaStart.takeIf { it.isNotBlank() }, afterReserve).maxOrNull() ?: addMonths(now, 1)
    var cum = 0.0
    return fixed.filter { it.grp == FixedCost.GRP_CASA }.sortedBy { it.amount }.map { f ->
        val target = f.amount * settings.multiplier
        cum += target
        val done = ipca >= cum
        val n = monthsTo(cum, ipca, settings.aporteLongo + extraMonthly, rm)
        val eta = if (done || !n.isFinite()) null else addMonths(start, ceil(n).toInt())
        Riscada(f, target, cum, done, eta, minOf(100.0, ipca / cum * 100))
    }
}

data class Milestone(val title: String, val detail: String, val month: String?, val done: Boolean)

/** Linha do tempo de conquistas, na ordem em que acontecem. */
fun FinanceData.milestones(today: LocalDate = LocalDate.now()): List<Milestone> {
    val c = core()
    val list = mutableListOf<Milestone>()
    val gap3 = c.essentials * 3 - c.free
    list += Milestone("Reserva mínima (3 meses)", "${brl0(c.essentials * 3)} em liquidez diária", etaForGap(gap3, 0.0, today), gap3 <= 0)
    val resEta = reserveEta(0.0, today)
    list += Milestone("Reserva completa (${settings.reserveMonths} meses)", "${brl0(c.reserveTarget)} guardados", resEta, c.reserveTarget > 0 && c.free >= c.reserveTarget)
    val lastDue = debts.flatMap { it.installments }.filter { !it.paid && it.month != null }.maxOfOrNull { it.month!! }
    list += Milestone("Primeiro mês sem parcelas", "o orçamento folga", lastDue?.let { addMonths(it, 1) }, lastDue == null && c.owed == 0.0)
    riscadas(today).forEach { r ->
        list += Milestone("Conta riscada: ${r.cost.name}", "${brl0(r.target)} acumulados no IPCA+ pagam ${brl0(r.cost.amount)}/mês", r.eta, r.done)
    }
    return list
}

/** "E se...?": quanto antecipa a reserva e a primeira conta riscada com mais aporte, corte de opcionais ou aumento de renda. */
data class WhatIfResult(
    val extraMonthly: Double, val reserveEta: String?, val reserveSooner: Int,
    val firstBillEta: String?, val firstBillSooner: Int
)

fun FinanceData.whatIf(extraAporte: Double, cutOpcionais: Boolean, raise: Double, today: LocalDate = LocalDate.now()): WhatIfResult {
    val cut = if (cutOpcionais) cutOptional(cutRefMonth(today)).monthly else 0.0
    val extra = extraAporte + cut + raise * 0.5   // regra: metade de todo aumento vai para o aporte
    val baseRes = reserveEta(0.0, today); val newRes = reserveEta(extra, today)
    val baseBill = riscadas(today).firstOrNull { !it.done }?.eta; val newBill = riscadas(today, extra).firstOrNull { !it.done }?.eta
    fun sooner(a: String?, b: String?) = if (a != null && b != null) maxOf(0, monthsBetween(b, a)) else 0
    return WhatIfResult(extra, newRes, sooner(baseRes, newRes), newBill, sooner(baseBill, newBill))
}

// ---------- saúde financeira ----------
data class HealthItem(val name: String, val value: String, val status: Level)
data class Health(val items: List<HealthItem>, val score: Int)

fun FinanceData.health(today: LocalDate = LocalDate.now()): Health {
    val c = core(); val r = riscadas(today)
    val income = settings.income
    val aporte = settings.aporteLongo
    fun high(v: Double, ok: Double, warn: Double) = if (v >= ok) Level.OK else if (v >= warn) Level.WARN else Level.BAD
    fun low(v: Double, ok: Double, warn: Double) = if (v <= ok) Level.OK else if (v <= warn) Level.WARN else Level.BAD
    val cash = c.inHand + c.toReceive
    val items = listOf(
        HealthItem("Reserva de emergência", if (c.reserveTarget > 0) "${(c.free / c.reserveTarget * 100).roundToLong()}% da meta" else "—",
            if (c.reserveTarget > 0) high(c.free / c.reserveTarget, 1.0, .5) else Level.WARN),
        HealthItem("Dinheiro já comprometido", if (cash > 0) "${(c.owed / cash * 100).roundToLong()}% do que você tem" else "—",
            if (cash > 0) low(c.owed / cash, .5, .9) else Level.BAD),
        HealthItem("Taxa de poupança", if (income > 0) "${(aporte / income * 100).roundToLong()}% da renda" else "—",
            if (income > 0) high(aporte / income, .2, .1) else Level.WARN),
        HealthItem("Essenciais sobre a renda", if (income > 0) "${(c.essentials / income * 100).roundToLong()}% da renda" else "—",
            if (income > 0) low(c.essentials / income, .4, .6) else Level.WARN),
        HealthItem("Contas riscadas", "${r.count { it.done }} de ${r.size}",
            if (r.isNotEmpty() && r.all { it.done }) Level.OK else if (r.any { it.done }) Level.WARN else Level.BAD)
    )
    val pts = mapOf(Level.OK to 100, Level.WARN to 60, Level.BAD to 20)
    return Health(items, (items.sumOf { pts.getValue(it.status) } / items.size.toDouble()).roundToLong().toInt())
}

// ---------- o "norte": próximos passos ----------
data class Step(val title: String, val detail: String, val dest: Dest)

fun FinanceData.norte(today: LocalDate = LocalDate.now()): List<Step> {
    val c = core()
    val now = monthKey(today)
    val steps = mutableListOf<Step>()

    val due = dueIn(now)
    if (due.isNotEmpty()) steps += Step(
        "Confirme os pagamentos de ${mLabel(now)}",
        "${due.size} ${if (due.size == 1) "item" else "itens"} previstos, ${brl0(due.sumOf { it.amount })} no total. Confirme o que já pagou, ajuste o valor ou remova o que cancelou.",
        Dest.GASTOS
    )
    if (c.freeNow < 0) steps += Step("Segure novos gastos", "O que você tem em conta não cobre o que já está comprometido. Evite parcelas novas até a próxima entrada.", Dest.GASTOS)

    val red = (1..3).map { addMonths(now, it) }.map { monthPlan(it) }.firstOrNull { it.sobra < 0 }
    if (red != null) steps += Step("${mLabel(red.month)} fecha no vermelho", "Faltam ${brl0(-red.sobra)} depois do aporte. Reduza o aporte desse mês ou cubra com o que vai receber.", Dest.GASTOS)

    val gap = c.reserveTarget - c.free
    if (c.reserveTarget > 0 && gap > 0) {
        val eta = reserveEta(0.0, today)
        steps += Step(
            "Complete a reserva de emergência",
            "Faltam ${brl0(gap)}. No aporte planejado ela fecha ${eta?.let { "em ${mLabel(it)}" } ?: "depois de 10 anos"}. Deixe em liquidez diária (conta remunerada, CDB com liquidez diária ou Tesouro Selic).",
            Dest.FUTURO
        )
        val cut = cutOptional(cutRefMonth(today))
        if (cut.monthly > 0) {
            val w = whatIf(0.0, true, 0.0, today)
            if (w.reserveSooner >= 1) steps += Step(
                "Corte opcionais para antecipar",
                "Cortar ${brl0(cut.monthly)}/mês de opcionais adianta a reserva em ${w.reserveSooner} ${if (w.reserveSooner == 1) "mês" else "meses"}.",
                Dest.GASTOS
            )
        }
    } else if (c.reserveTarget > 0) {
        steps += Step("Comece o balde IPCA+", "Reserva completa. O próximo aporte vai para o IPCA+ (vencimento mais curto oscila menos). Mantenha fora da conta de emergência.", Dest.FUTURO)
    }
    if (steps.isEmpty()) steps += Step("Tudo em dia", "Nada pendente agora. Que tal simular um objetivo no \"E se...?\"", Dest.FUTURO)
    return steps.take(4)
}

fun FinanceData.nextStep(today: LocalDate = LocalDate.now()): String = norte(today).first().let { "${it.title}. ${it.detail}" }

// ---------- simulador de compra ----------
enum class PayMode { VISTA, SEM_JUROS, COM_JUROS }

data class PurchaseInput(
    val desc: String = "", val valor: Double = 0.0, val mode: PayMode = PayMode.VISTA,
    val parcelas: Int = 1, val jurosMensalPct: Double = 0.0, val recorrente: Double = 0.0, val desejo: Boolean = true
)
data class PurchaseResult(
    val level: Level, val verdict: String, val lines: List<Pair<Level, String>>,
    val parcela: Double, val total: Double, val freeAfter: Double
)

fun FinanceData.evalPurchase(p: PurchaseInput, today: LocalDate = LocalDate.now()): PurchaseResult {
    val c = core()
    val v = p.valor; val rec = p.recorrente
    val n = maxOf(1, p.parcelas); val jur = p.jurosMensalPct / 100
    var parcela = v; var total = v
    when (p.mode) {
        PayMode.SEM_JUROS -> parcela = v / n
        PayMode.COM_JUROS -> { parcela = if (jur > 0) v * jur / (1 - (1 + jur).pow(-n)) else v / n; total = parcela * n }
        PayMode.VISTA -> {}
    }
    val span = if (p.mode == PayMode.VISTA) 0 else n
    var minSobra = Double.POSITIVE_INFINITY; var negMonths = 0
    visibleMonths(today).forEachIndexed { i, m ->
        val extra = (if (i < span) parcela else 0.0) + rec
        if (extra > 0) {
            val after = monthPlan(m).sobra - extra
            minSobra = minOf(minSobra, after); if (after < 0) negMonths++
        }
    }
    val freeAfter = c.free - total
    val rm = settings.realRate / 12
    val t = fixed.filter { it.grp == FixedCost.GRP_CASA }.sumOf { it.amount } * settings.multiplier
    var delay: Double? = null
    if (rec > 0) {
        val a = monthsTo(t, c.ipca, settings.aporteLongo, rm); val b = monthsTo(t + rec * settings.multiplier, c.ipca, settings.aporteLongo, rm)
        if (a.isFinite() && b.isFinite()) delay = b - a
    }
    val grow = v * (1 + settings.realRate).pow(10)
    val lines = mutableListOf<Pair<Level, String>>()
    var level = Level.OK
    fun bump(l: Level) { if (l == Level.BAD || (l == Level.WARN && level == Level.OK)) level = l }

    if (freeAfter < 0) { lines += Level.BAD to "Seu livre de verdade ficaria negativo (${brl(freeAfter)})."; bump(Level.BAD) }
    else if (c.reserveTarget > 0 && freeAfter < c.reserveTarget) {
        val gap = c.reserveTarget - freeAfter
        val months = if (settings.aporteLongo > 0) ceil(gap / settings.aporteLongo).toInt().toString() else "?"
        lines += Level.WARN to "Seu livre cai para ${brl0(freeAfter)}, abaixo da meta da reserva (${brl0(c.reserveTarget)}). A reserva demoraria ~$months meses a mais para fechar, no aporte atual."
        bump(Level.WARN)
    } else lines += Level.OK to "Seu livre continuaria em ${brl0(freeAfter)}, sem tocar na meta da reserva."

    if (span > 0 || rec > 0) {
        if (negMonths > 0) { lines += Level.BAD to "$negMonths mês(es) ficariam no vermelho (pior mês: ${brl(minSobra)} de sobra)."; bump(Level.BAD) }
        else if (minSobra.isFinite()) lines += Level.OK to "Todos os meses seguem com sobra positiva (menor sobra: ${brl0(minSobra)})."
    }
    if (p.mode == PayMode.COM_JUROS && jur > 0) { lines += Level.BAD to "Com juros você paga ${brl(total)} no total, ${brl(total - v)} só de juros."; bump(Level.BAD) }
    if (rec > 0) {
        val d = delay?.let { " (~${it.roundToLong()} meses a mais no aporte atual)" } ?: ""
        lines += Level.WARN to "Custo recorrente de ${brl0(rec)}/mês exige ${brl0(rec * settings.multiplier)} a mais na meta de independência$d."
        if (p.desejo) bump(Level.WARN)
    }
    if (p.desejo && v > 0)
        lines += Level.WARN to "Se esses ${brl0(v)} fossem investidos por 10 anos a ${"%.1f".format(ptBr, settings.realRate * 100)}% reais, virariam ~${brl0(grow)} em dinheiro de hoje."
    val verdict = when (level) { Level.OK -> "Cabe no seu orçamento"; Level.WARN -> "Cabe, mas com custo — pense antes"; Level.BAD -> "Não recomendo agora" }
    return PurchaseResult(level, verdict, lines, parcela, total, freeAfter)
}
