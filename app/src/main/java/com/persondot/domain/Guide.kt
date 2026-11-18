package com.persondot.domain

import com.persondot.data.model.FixedCost
import com.persondot.data.model.isHome
import com.persondot.data.model.isOptional
import com.persondot.data.model.Settings
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * O guia do Dot: olha para como você usa o app e para a sua situação e diz o que vale fazer.
 * São orientações educativas baseadas nas SUAS regras e números; não são recomendação financeira
 * personalizada, e as regras do MEI devem ser conferidas com a Receita ou um contador.
 */
enum class InsightKind { ALERTA, PJ, SUGESTAO, MARCO }

sealed interface GuideAction {
    /** Muda parâmetros do plano (campo → valor). O app mostra um botão "Aplicar". */
    data class Apply(val changes: Map<String, Double>, val label: String) : GuideAction
    data class Go(val dest: Dest) : GuideAction
}

data class Insight(
    val id: String, val kind: InsightKind, val level: Level,
    val title: String, val detail: String, val action: GuideAction? = null
)

/** Aplica ao plano as mudanças sugeridas pelo guia. */
fun Settings.applyChanges(changes: Map<String, Double>): Settings = changes.entries.fold(this) { s, (k, v) ->
    when (k) {
        "multiplier" -> s.copy(multiplier = v.coerceAtLeast(1.0))
        "realRate" -> s.copy(realRate = v.coerceAtLeast(0.001))
        "aporteLongo" -> s.copy(aporteLongo = v.coerceAtLeast(0.0))
        "reserveMonths" -> s.copy(reserveMonths = v.toInt().coerceAtLeast(1))
        "meiLimit" -> s.copy(meiLimit = v)
        "income" -> s.copy(income = v.coerceAtLeast(0.0))
        else -> s
    }
}

private val WEALTH_MARKS = listOf(
    10_000.0 to "Vale comparar onde cada parte está rendendo (conta remunerada, CDB com liquidez diária, Tesouro Selic): alguns produtos só pagam a taxa cheia até certo valor.",
    25_000.0 to "Com a reserva fechada, o excedente pode ir para prazos maiores (como o IPCA+), mantendo a reserva separada e líquida.",
    50_000.0 to "Considere diversificar além da renda fixa e comparar custos e impostos de cada investimento.",
    100_000.0 to "Vale revisar tudo uma vez por ano (taxas, prazos, riscos) e pensar na aposentadoria separada do balde das contas.",
    250_000.0 to "Nesse patamar, uma consulta com um profissional de planejamento financeiro regulamentado costuma compensar.",
    500_000.0 to "Revise a carteira inteira e a estratégia de longo prazo com um profissional de confiança."
)

fun FinanceData.insights(today: LocalDate = LocalDate.now()): List<Insight> {
    val out = mutableListOf<Insight>()
    val c = core(); val st = settings; val income = st.income
    val now = monthKey(today)

    // ---------- PJ / MEI ----------
    mei(today)?.let { m ->
        val over = m.projectedYear - m.limit
        if (over > 0) {
            val pct = over / m.limit
            out += Insight(
                "mei-estouro", InsightKind.PJ, if (pct > 0.20) Level.BAD else Level.WARN,
                "O MEI deve estourar em ${mLabel(m.crossMonth)}",
                "Se continuar recebendo ${brl0(m.avgMonthly)} por mês, você fecha o ano com ${brl0(m.projectedYear)}, ${brl0(over)} acima do limite de ${brl0(m.limit)}. " +
                    "Passar até 20% do limite gera imposto extra sobre o excesso; acima disso o desenquadramento vale desde janeiro. " +
                    "Converse com um contador e planeje a abertura de uma ME antes de ${mLabel(m.crossMonth)}."
            )
        } else if (m.usedPct >= 0.8) {
            out += Insight("mei-80", InsightKind.PJ, Level.WARN, "Você já usou ${(m.usedPct * 100).roundToInt()}% do limite do MEI",
                "Faturou ${brl0(m.ytd)} de ${brl0(m.limit)} neste ano. Acompanhe de perto os próximos meses.")
        } else if (m.projectedYear >= m.limit * 0.85) {
            out += Insight("mei-perto", InsightKind.PJ, Level.WARN, "Perto do limite do MEI no fim do ano",
                "No ritmo atual você fecha o ano com ${brl0(m.projectedYear)} de ${brl0(m.limit)}.")
        }
        if (m.estimatedMonths > 0 && m.projectedYear >= m.limit * 0.6) {
            out += Insight("mei-estimado", InsightKind.PJ, Level.OK, "Faturamento estimado",
                "${m.estimatedMonths} ${if (m.estimatedMonths == 1) "mês" else "meses"} deste ano sem faturamento registrado: usei a sua renda mensal como estimativa. Registre o valor real para o aviso ficar preciso.")
        }
    }
    if (st.meiOn) {
        val das = fixed.firstOrNull { Regex("\\bDAS\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
        val f = das?.let { d -> forecasts(now).firstOrNull { it.cost.id == d.id } }
        if (f != null && f.status == ForecastStatus.PREVISTO && f.counts) {
            val day = today.dayOfMonth
            if (day in 12..20) out += Insight("das", InsightKind.PJ, if (day >= 18) Level.WARN else Level.OK, "O DAS vence dia 20",
                "O DAS de ${mLabel(now)} ainda está como previsto. Confirme quando pagar para não esquecer.", GuideAction.Go(Dest.GASTOS))
            else if (day > 20) out += Insight("das-atraso", InsightKind.PJ, Level.BAD, "DAS de ${mLabel(now)} sem confirmação",
                "O vencimento é dia 20. Se já pagou, confirme em Gastos; se não, regularize para evitar multa e juros.", GuideAction.Go(Dest.GASTOS))
        }
        if (today.monthValue in 4..5) out += Insight("dasn", InsightKind.PJ, if (today.monthValue == 5) Level.WARN else Level.OK, "Declaração anual do MEI",
            "A declaração anual (DASN-SMEI) costuma vencer em 31 de maio. Confira no Portal do Empreendedor.")
    }

    // ---------- reserva e marcos ----------
    val reserveDone = c.reserveTarget > 0 && c.free >= c.reserveTarget
    if (!reserveDone && c.essentials > 0 && c.free >= c.essentials * 3)
        out += Insight("reserva-minima", InsightKind.MARCO, Level.OK, "Reserva mínima atingida", "Você já cobre 3 meses de essenciais. Faltam ${brl0(c.reserveTarget - c.free)} para a reserva completa.")
    if (reserveDone) {
        out += Insight("reserva-completa", InsightKind.MARCO, Level.OK, "Reserva completa", "Pode começar o balde IPCA+ com o próximo aporte.", GuideAction.Go(Dest.FUTURO))
        if (st.reserveMonths < 9) out += Insight("reserva-pj", InsightKind.PJ, Level.OK, "Considere ampliar a reserva",
            "Como PJ você não tem 13º, férias nem seguro-desemprego, e muitos PJs miram 9 a 12 meses de reserva (você usa ${st.reserveMonths}).",
            GuideAction.Apply(mapOf("reserveMonths" to 9.0), "Usar 9 meses"))
    }
    val saved = c.inHand + c.ipca
    WEALTH_MARKS.lastOrNull { saved >= it.first }?.let { (v, text) ->
        out += Insight("marco-${v.toInt()}", InsightKind.MARCO, Level.OK, "Você passou de ${brl0(v)} guardados", text)
    }
    WEALTH_MARKS.firstOrNull { saved < it.first }?.let { (v, _) ->
        val eta = etaForGap(v - saved, 0.0, today)
        out += Insight("proximo-marco", InsightKind.MARCO, Level.OK, "Próximo marco: ${brl0(v)}", "Faltam ${brl0(v - saved)}${eta?.let { ". No aporte atual você chega em ${mLabel(it)}" } ?: ""}.")
    }

    // ---------- o plano: multiplicador, rendimento e aporte ----------
    val implied = (12 / st.realRate / 10).roundToInt() * 10.0
    if (abs(st.multiplier - 12 / st.realRate) > 15) {
        out += Insight("mult-coerente", InsightKind.SUGESTAO, Level.WARN, "Multiplicador e rendimento não batem",
            "Com ${"%.1f".format(java.util.Locale.forLanguageTag("pt-BR"), st.realRate * 100)}% reais ao ano, o multiplicador coerente é ×${implied.toInt()}; hoje você usa ×${st.multiplier.toInt()}.",
            GuideAction.Apply(mapOf("multiplier" to implied), "Usar ×${implied.toInt()}"))
    } else if (st.multiplier <= 200 && st.realRate >= 0.06) {
        out += Insight("mult-margem", InsightKind.SUGESTAO, Level.OK, "Dá para planejar com mais margem",
            "6% reais ao ano depende do cenário. Com ×250 (4,8%) a meta das contas sobe 25%, mas o plano aguenta melhor juros mais baixos.",
            GuideAction.Apply(mapOf("multiplier" to 250.0, "realRate" to 0.048), "Usar ×250"))
    }
    if (income > 0) {
        val rate = st.aporteLongo / income
        if (rate < 0.10) {
            val target = (income * 0.15 / 50).roundToInt() * 50.0
            val w = whatIf(target - st.aporteLongo, false, 0.0, today)
            out += Insight("aporte-baixo", InsightKind.SUGESTAO, Level.WARN, "Você guarda ${(rate * 100).roundToInt()}% da renda",
                "O ponto de partida comum é guardar de 10% a 20%. Com ${brl0(target)} por mês${if (w.reserveSooner > 0) " a reserva fecha ${w.reserveSooner} ${if (w.reserveSooner == 1) "mês" else "meses"} antes" else ""}.",
                GuideAction.Apply(mapOf("aporteLongo" to target), "Guardar ${brl0(target)}"))
        }
    }
    (1..6).map { addMonths(now, it) }.firstOrNull { monthPlan(it).sobra < 0 }?.let { red ->
        val p = monthPlan(red)
        val custom = aportes[red] != null
        val lowered = max(0.0, st.aporteLongo + p.sobra)
        out += Insight("mes-vermelho", InsightKind.ALERTA, Level.BAD, "${mLabel(red)} fecha no vermelho",
            "Faltam ${brl0(-p.sobra)} depois do aporte de ${brl0(p.aporte)}. " + if (!custom) "Baixar o aporte padrão para ${brl0(lowered)} resolve esse mês, mas atrasa o plano." else "Ajuste o aporte só desse mês em Ajustes → Aportes por mês.",
            if (!custom) GuideAction.Apply(mapOf("aporteLongo" to lowered), "Baixar para ${brl0(lowered)}") else GuideAction.Go(Dest.GASTOS))
    }

    // ---------- gastos ----------
    if (income > 0 && c.essentials / income > 0.6) {
        val big = fixed.filter { settings.isHome(it.grp) }.maxByOrNull { it.amount }
        out += Insight("essenciais-altos", InsightKind.ALERTA, Level.WARN, "Essenciais consomem ${(c.essentials / income * 100).roundToInt()}% da renda",
            "Acima de 60% sobra pouco para guardar." + (big?.let { " O maior é ${it.name} (${brl0(it.amount)})." } ?: "") + " Vale revisar esse custo ou aumentar a renda.", GuideAction.Go(Dest.GASTOS))
    }
    val optional = fixed.filter { settings.isOptional(it.grp) }.sumOf { it.amount }
    if (income > 0 && optional / income > 0.12) {
        val cut = cutOptional(cutRefMonth(today))
        val w = whatIf(0.0, true, 0.0, today)
        out += Insight("opcionais-altos", InsightKind.SUGESTAO, Level.WARN, "Opcionais pesam ${(optional / income * 100).roundToInt()}% da renda",
            "Os maiores são ${cut.items.take(2).joinToString(" e ") { it.cost.name }}. Cortar tudo liberaria ${brl0(cut.monthly)} por mês" +
                (if (w.reserveSooner > 0) " e adiantaria a reserva em ${w.reserveSooner} ${if (w.reserveSooner == 1) "mês" else "meses"}." else "."), GuideAction.Go(Dest.GASTOS))
    }
    val plan = monthPlan(now)
    if (plan.spent > 0 && plan.restante < 0)
        out += Insight("lazer-estourado", InsightKind.ALERTA, Level.WARN, "Você passou do que sobrava para lazer",
            "Em ${mLabel(now)} já saíram ${brl0(plan.spent)} no dia a dia e a sobra era ${brl0(plan.sobra)}.", GuideAction.Go(Dest.GASTOS))
    if (income > 0 && c.owed > income * 1.5)
        out += Insight("parcelas-altas", InsightKind.ALERTA, Level.WARN, "Parcelas somam ${"%.1f".format(java.util.Locale.forLanguageTag("pt-BR"), c.owed / income)} meses de renda",
            "Evite novas parcelas até o calendário de alívio folgar.", GuideAction.Go(Dest.GASTOS))

    val levelOrder = mapOf(Level.BAD to 0, Level.WARN to 1, Level.OK to 2)
    return out.sortedWith(compareBy({ levelOrder.getValue(it.level) }, { it.kind.ordinal }))
}
