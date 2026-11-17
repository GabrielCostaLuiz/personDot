package com.persondot.domain

import com.persondot.data.model.Account
import java.time.LocalDate

/**
 * O "cérebro" do Dot. Recebe a pergunta montada em blocos ([Draft]) e chama a ferramenta certa.
 * Não usa IA nem internet. Cada resposta existe em dois tamanhos:
 *  - direto: a conclusão em uma ou duas frases (o que já está nas telas fica nas telas);
 *  - detalhado: os números e o porquê.
 * Ações que mudam dados (confirmar pagamento, cancelar custo, lançar gasto) sempre pedem um "sim" antes.
 */
sealed interface DotAction {
    data class ConfirmForecast(val fixedId: Long, val month: String, val name: String, val amount: Double) : DotAction
    data class CancelFrom(val fixedId: Long, val month: String, val name: String) : DotAction
    data class AddExpense(val desc: String, val amount: Double, val cat: String) : DotAction
}

data class BrainReply(
    val text: String,
    val tool: String? = null,               // nome da ferramenta usada (mostrado no chat)
    val pending: DotAction? = null,         // aguardando "sim" ou "não"
    val run: DotAction? = null,             // ação confirmada: o app deve executar
    val level: Level? = null,               // humor do Dot depois da resposta
    val hasMore: Boolean = false            // existe uma versão mais detalhada
)

object DotBrain {

    fun greeting() = BrainReply("Oi! Monte a sua pergunta aqui embaixo, escolhendo os blocos. Eu respondo com os seus números.")

    private fun bullets(items: List<String>) = items.joinToString("\n") { "• $it" }
    private fun s(n: Int, one: String, many: String) = if (n == 1) one else many
    private fun FinanceData.liquidAccount() = accounts.firstOrNull { it.kind != Account.KIND_IPCA }
    private fun lvl(score: Int) = if (score >= 75) Level.OK else if (score >= 50) Level.WARN else Level.BAD

    fun answer(d: Draft, data: FinanceData, detailed: Boolean = false, today: LocalDate = LocalDate.now()): BrainReply {
        val now = monthKey(today)
        val next = addMonths(now, 1)
        return when (d.topic) {
            null -> BrainReply("Escolha um tema para começar.")

            Topic.SAUDE -> {
                val h = data.health(today)
                val weak = h.items.minByOrNull { mapOf(Level.BAD to 0, Level.WARN to 1, Level.OK to 2).getValue(it.status) }
                BrainReply(
                    if (detailed) "Sua saúde financeira está em ${h.score}/100:\n" + bullets(h.items.map { "${it.name}: ${it.value}" })
                    else "Sua saúde financeira está em ${h.score}/100. O ponto mais fraco é ${weak?.name?.lowercase()} (${weak?.value}).",
                    tool = "saude_financeira", level = lvl(h.score), hasMore = !detailed
                )
            }

            Topic.NORTE -> {
                val steps = data.norte(today)
                BrainReply(
                    if (detailed) "Seu norte agora:\n" + steps.mapIndexed { i, st -> "${i + 1}. ${st.title}: ${st.detail}" }.joinToString("\n")
                    else "${steps.first().title}. ${steps.first().detail}",
                    tool = "norte", level = Level.OK, hasMore = !detailed && steps.size > 1
                )
            }

            Topic.LIVRE -> {
                val c = data.core(); val p = data.monthPlan(next)
                BrainReply(
                    if (detailed) "Seu livre de verdade é ${brl(c.free)} (em conta ${brl0(c.inHand)} + a receber ${brl0(c.toReceive)} − parcelas ${brl0(c.owed)}).\nEm ${mLabel(next)}, depois das previsões e do aporte, sobram ${brl(p.restante)} para lazer."
                    else "Seu livre de verdade é ${brl(c.free)}. Em ${mLabel(next)} sobram ${brl(p.restante)} para lazer.",
                    tool = "livre_de_verdade", level = if (c.free < 0) Level.BAD else Level.OK, hasMore = !detailed
                )
            }

            Topic.CORTAR -> {
                val ref = data.cutRefMonth(today)
                val cut = data.cutOptional(ref)
                if (cut.items.isEmpty()) return BrainReply("Não vejo opcionais em aberto. Você já está enxuto!", tool = "corte_de_opcionais", level = Level.OK)
                val w = data.whatIf(0.0, true, 0.0, today)
                val sooner = if (w.reserveSooner > 0) "A reserva fecharia ${w.reserveSooner} ${s(w.reserveSooner, "mês", "meses")} antes." else ""
                BrainReply(
                    if (detailed) "Cortando todos os opcionais (valores de ${mLabel(ref)}), sai:\n" + bullets(cut.items.map { "${it.cost.name}: ${brl(it.amount)}" }) +
                        "\nTotal: ${brl(cut.monthly)} por mês, ${brl0(cut.yearly)} por ano. $sooner".trim()
                    else "Cortando os ${cut.items.size} opcionais você libera ${brl0(cut.monthly)} por mês (${brl0(cut.yearly)} por ano). O maior é ${cut.items.first().cost.name} (${brl0(cut.items.first().amount)}). $sooner".trim(),
                    tool = "corte_de_opcionais", level = Level.OK, hasMore = !detailed
                )
            }

            Topic.COMPRA -> {
                val n = d.installments
                val mode = when { n <= 1 -> PayMode.VISTA; d.interestPct > 0 -> PayMode.COM_JUROS; else -> PayMode.SEM_JUROS }
                val value = d.amount ?: 0.0
                val r = data.evalPurchase(PurchaseInput(valor = value, mode = mode, parcelas = n, jurosMensalPct = d.interestPct, recorrente = d.monthlyCost ?: 0.0, desejo = !d.essential), today)
                val head = "${r.verdict}. (${brl(value)}${if (mode == PayMode.VISTA) " à vista" else " em ${n}x de ${brl(r.parcela)}"})"
                val worst = r.lines.firstOrNull { it.first == Level.BAD } ?: r.lines.firstOrNull { it.first == Level.WARN } ?: r.lines.first()
                BrainReply(
                    if (detailed) head + "\n" + bullets(r.lines.map { it.second }) else head + "\n" + worst.second,
                    tool = "simular_compra", level = r.level, hasMore = !detailed && r.lines.size > 1
                )
            }

            Topic.COMO_ESTA -> when (d.sub) {
                "mes" -> {
                    val m = d.month ?: now
                    val p = data.monthPlan(m)
                    BrainReply(
                        if (detailed) "${mLabel(m)}:\n" + bullets(listOf(
                            "Previsto: ${brl(p.previsto)} (fixos ${brl0(p.fixedTotal)} + parcelas ${brl0(p.parcelas)})",
                            "Já pago: ${brl(p.pago)} · falta pagar ${brl(p.aPagar)}",
                            "Aporte planejado: ${brl(p.aporte)}",
                            "Sobra para lazer: ${brl(p.restante)}"))
                        else "${mLabel(m)}: previsto ${brl0(p.previsto)}, falta pagar ${brl0(p.aPagar)}. Sobram ${brl0(p.restante)} para lazer.",
                        tool = "resumo_do_mes", level = if (p.sobra < 0) Level.BAD else Level.OK, hasMore = !detailed
                    )
                }
                "parcelas" -> {
                    val c = data.core()
                    if (c.owed == 0.0) return BrainReply("Você não tem parcelas em aberto. Que alívio!", tool = "dividas", level = Level.OK)
                    val last = data.debts.flatMap { it.installments }.filter { !it.paid && it.month != null }.maxOfOrNull { it.month!! }
                    val nextDue = data.debts.flatMap { dw -> dw.installments.filter { !it.paid && it.month != null }.map { dw.debt.name to it } }.minByOrNull { it.second.month!! }
                    BrainReply(
                        if (detailed) "Você tem ${brl(c.owed)} em parcelas:\n" + bullets(data.debts.filter { it.remaining > 0 }.map { dw ->
                            val ni = dw.installments.filter { !it.paid }.minByOrNull { it.month ?: "9999" }
                            "${dw.debt.name}: faltam ${brl(dw.remaining)}${ni?.let { ", próxima em ${mLabel(it.month)} (${brl(it.amount)})" } ?: ""}"
                        }) + (last?.let { "\nA última vence em ${mLabel(it)}; depois disso o orçamento folga." } ?: "")
                        else "Faltam ${brl0(c.owed)} em parcelas." + (nextDue?.let { " A próxima é ${it.first}, em ${mLabel(it.second.month)}." } ?: "") + (last?.let { " A última vence em ${mLabel(it)}." } ?: ""),
                        tool = "dividas", level = Level.WARN, hasMore = !detailed
                    )
                }
                "reserva" -> {
                    val c = data.core(); val gap = c.reserveTarget - c.free
                    if (gap <= 0) return BrainReply("Sua reserva está completa: ${brl0(c.free)} para uma meta de ${brl0(c.reserveTarget)}. Já pode começar o balde IPCA+.", tool = "reserva", level = Level.OK)
                    val eta = mLabel(data.reserveEta(0.0, today))
                    BrainReply(
                        if (detailed) "Sua reserva está em ${brl0(c.free)} de ${brl0(c.reserveTarget)} (${(c.free / c.reserveTarget * 100).toInt()}%). Faltam ${brl0(gap)}; no aporte planejado fecha em $eta. Mantenha em liquidez diária (conta remunerada, CDB com liquidez diária ou Tesouro Selic): o principal não oscila."
                        else "Reserva em ${(c.free / c.reserveTarget * 100).toInt()}% da meta; faltam ${brl0(gap)}. Fecha em $eta no aporte atual.",
                        tool = "reserva", level = Level.WARN, hasMore = !detailed
                    )
                }
                else -> {
                    val r = data.riscadas(today); val c = data.core()
                    val nextBill = r.firstOrNull { !it.done }
                    BrainReply(
                        if (detailed) "Contas riscadas (valor × ${data.settings.multiplier.toInt()}), da menor para a maior:\n" +
                            bullets(r.map { "${it.cost.name}: ${if (it.done) "riscada" else "meta ${brl0(it.cum)} acumulados, previsão ${mLabel(it.eta)}"}" }) +
                            "\nHoje o balde IPCA+ tem ${brl0(c.ipca)}." + (if (c.free < c.reserveTarget) " Primeiro complete a reserva; o IPCA+ começa depois." else "")
                        else "${r.count { it.done }} de ${r.size} contas riscadas." + (nextBill?.let { " A próxima é ${it.cost.name}, prevista para ${mLabel(it.eta)}." } ?: " Todas riscadas!"),
                        tool = "contas_riscadas", level = Level.OK, hasMore = !detailed
                    )
                }
            }

            Topic.E_SE -> {
                val v = d.amount ?: 0.0
                val w = when (d.sub) {
                    "guardar" -> data.whatIf(v, false, 0.0, today)
                    "aumento" -> data.whatIf(0.0, false, v, today)
                    else -> data.whatIf(0.0, true, 0.0, today)
                }
                val what = when (d.sub) { "guardar" -> "Guardando ${brl0(v)} a mais por mês"; "aumento" -> "Com um aumento de ${brl0(v)} (metade vai para o aporte)"; else -> "Cortando os opcionais" }
                val bill = data.riscadas(today, w.extraMonthly).firstOrNull { !it.done }
                val soonerText = if (w.reserveSooner > 0) ", ${w.reserveSooner} ${s(w.reserveSooner, "mês", "meses")} antes" else ""
                BrainReply(
                    if (detailed) bullets(buildList {
                        add("$what: ${brl0(w.extraMonthly)} por mês a mais no aporte.")
                        add("Reserva completa em ${mLabel(w.reserveEta)}$soonerText.")
                        bill?.let { add("Primeira conta riscada (${it.cost.name}) em ${mLabel(w.firstBillEta)}${if (w.firstBillSooner > 0) ", ${w.firstBillSooner} meses antes" else ""}.") }
                    })
                    else "$what, a reserva fecha em ${mLabel(w.reserveEta)}$soonerText.",
                    tool = "e_se", level = Level.OK, hasMore = !detailed
                )
            }

            Topic.REGISTRAR -> {
                val acc = data.liquidAccount()
                val from = acc?.let { " Eu desconto da conta ${it.name}." } ?: ""
                when (d.sub) {
                    "pagou" -> {
                        val f = data.forecasts(now).firstOrNull { it.cost.id == d.costId } ?: return BrainReply("Não achei essa conta neste mês.")
                        if (f.status == ForecastStatus.PAGO) return BrainReply("${f.cost.name} de ${mLabel(now)} já está confirmado como pago.")
                        BrainReply("Confirmo ${f.cost.name} de ${mLabel(now)} por ${brl(f.amount)}?$from", tool = "confirmar_pagamento", pending = DotAction.ConfirmForecast(f.cost.id, now, f.cost.name, f.amount))
                    }
                    "cancelou" -> {
                        val cost = data.fixed.firstOrNull { it.id == d.costId } ?: return BrainReply("Não achei esse custo.")
                        BrainReply(
                            "Cancelo ${cost.name} a partir de ${mLabel(next)}? Isso economiza ${brl(cost.amount)} por mês (${brl0(cost.amount * 12)} por ano). Meses anteriores ficam como estão.",
                            tool = "cancelar_custo", pending = DotAction.CancelFrom(cost.id, next, cost.name)
                        )
                    }
                    else -> BrainReply(
                        "Lanço ${brl(d.amount ?: 0.0)} em ${d.category}?$from",
                        tool = "lançar_gasto", pending = DotAction.AddExpense(d.category ?: "Gasto", d.amount ?: 0.0, d.category ?: "Outros")
                    )
                }
            }
        }
    }

    /** O "sim": devolve a ação para o app executar. */
    fun confirm(pending: DotAction): BrainReply = BrainReply(
        when (pending) {
            is DotAction.ConfirmForecast -> "Pronto! Confirmei ${pending.name} de ${mLabel(pending.month)} por ${brl(pending.amount)}."
            is DotAction.CancelFrom -> "Feito. ${pending.name} não aparece mais de ${mLabel(pending.month)} em diante."
            is DotAction.AddExpense -> "Lançado: ${brl(pending.amount)} em ${pending.cat}."
        },
        tool = "executar_ação", run = pending, level = Level.OK
    )

    fun decline() = BrainReply("Tudo bem, não mexi em nada.")
}
