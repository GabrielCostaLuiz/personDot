package com.persondot.domain

import com.persondot.data.model.Account
import com.persondot.data.model.groupLabel
import java.time.LocalDate

/**
 * O "cérebro" do Dot. Recebe a pergunta montada em blocos ([Draft]) e chama a ferramenta certa.
 * Não usa IA nem internet. Cada resposta existe em dois tamanhos:
 *  - direto: a conclusão em uma ou duas frases (o que já está nas telas fica nas telas);
 *  - detalhado: os números e o porquê.
 * Tudo que MUDA dados (confirmar, cadastrar, excluir, lançar, aplicar sugestão) sempre pede um "sim" antes.
 */
sealed interface DotAction {
    data class ConfirmForecast(val fixedId: Long, val month: String, val name: String, val amount: Double, val accountId: Long? = null) : DotAction
    data class CancelFrom(val fixedId: Long, val month: String, val name: String) : DotAction
    data class AddExpense(val desc: String, val amount: Double, val cat: String, val accountId: Long? = null) : DotAction
    data class AddFixed(val name: String, val amount: Double, val group: String, val onCard: Boolean, val startMonth: String) : DotAction
    data class SetFixedAmount(val fixedId: Long, val name: String, val amount: Double) : DotAction
    data class DeleteFixed(val fixedId: Long, val name: String) : DotAction
    data class RemoveFromMonth(val fixedId: Long, val month: String, val name: String) : DotAction
    data class DeleteExpense(val expenseId: Long, val label: String) : DotAction
    data class AddReceivable(val desc: String, val amount: Double) : DotAction
    data class ReceiveReceivable(val receivableId: Long, val label: String, val accountId: Long? = null) : DotAction
    data class PayNextInstallment(val debtId: Long, val name: String, val amount: Double, val accountId: Long? = null) : DotAction
    data class AddRevenue(val month: String, val amount: Double) : DotAction
    data class StartReflection(val name: String, val price: Double, val emotion: String, val cash: Boolean) : DotAction
    data class DecideReflection(val id: Int, val name: String, val buy: Boolean) : DotAction
    data class AddAccount(val name: String, val balance: Double, val kind: String) : DotAction
    data class SetBalance(val accountId: Long, val name: String, val balance: Double) : DotAction
    data class DeleteAccount(val accountId: Long, val name: String) : DotAction
    data class DeleteReceivable(val receivableId: Long, val label: String) : DotAction
    data class SetAporte(val month: String, val amount: Double) : DotAction
    data class AddCategory(val name: String) : DotAction
    data class RemoveCategory(val name: String) : DotAction
    data class ApplyChanges(val changes: Map<String, Double>, val label: String) : DotAction
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

    fun greeting() = BrainReply("Oi! Escolha os blocos aqui embaixo para perguntar ou pedir algo. Eu respondo com os seus números e, se for para mudar alguma coisa, peço um sim antes.")

    private fun bullets(items: List<String>) = items.joinToString("\n") { "• $it" }
    private fun s(n: Int, one: String, many: String) = if (n == 1) one else many
    private fun FinanceData.liquidAccount() = accounts.firstOrNull { it.kind != Account.KIND_IPCA }
    private fun lvl(score: Int) = if (score >= 75) Level.OK else if (score >= 50) Level.WARN else Level.BAD
    private fun levelOf(l: Level) = l

    fun answer(d: Draft, data: FinanceData, detailed: Boolean = false, today: LocalDate = LocalDate.now()): BrainReply {
        val now = monthKey(today)
        val next = addMonths(now, 1)
        val acc = if (d.accountChosen) data.accounts.firstOrNull { it.id == d.accountId } else data.liquidAccount()
        val from = acc?.let { " Eu mexo na conta ${it.name}." } ?: ""
        return when (d.topic) {
            null -> BrainReply("Escolha um tema para começar.")

            Topic.NORTE -> {
                val steps = data.norte(today)
                BrainReply(
                    if (detailed) "Seu norte agora:\n" + steps.mapIndexed { i, st -> "${i + 1}. ${st.title}: ${st.detail}" }.joinToString("\n")
                    else "${steps.first().title}. ${steps.first().detail}",
                    tool = "norte", level = Level.OK, hasMore = !detailed && steps.size > 1
                )
            }

            // ---------- o guia ----------
            Topic.GUIA -> {
                val list = data.insights(today)
                if (list.isEmpty()) return BrainReply("Por enquanto não tenho nada a recomendar: está tudo em ordem.", tool = "guia", level = Level.OK)
                val top = list.first()
                val act = (top.action as? GuideAction.Apply)
                if (detailed) BrainReply(
                    "O que eu vejo na sua situação:\n" + bullets(list.take(7).map { "${it.title}: ${it.detail}" }),
                    tool = "guia", level = top.level
                ) else BrainReply(
                    "${top.title}. ${top.detail}" + (act?.let { "\n\nQuer que eu aplique (${it.label})?" } ?: ""),
                    tool = "guia", level = top.level, hasMore = list.size > 1,
                    pending = act?.let { DotAction.ApplyChanges(it.changes, it.label) }
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
                "saude" -> {
                    val h = data.health(today)
                    if (!h.hasData) return BrainReply("Ainda não tenho dados para medir a sua saúde financeira. Cadastre a renda, as contas e os custos e eu calculo.", tool = "saude_financeira", level = Level.WARN)
                    val weak = h.items.minByOrNull { mapOf(Level.BAD to 0, Level.WARN to 1, Level.OK to 2).getValue(it.status) }
                    BrainReply(
                        if (detailed) "Sua saúde financeira está em ${h.score}/100:\n" + bullets(h.items.map { "${it.name}: ${it.value}" })
                        else "Sua saúde financeira está em ${h.score}/100. O ponto mais fraco é ${weak?.name?.lowercase()} (${weak?.value}).",
                        tool = "saude_financeira", level = lvl(h.score), hasMore = !detailed
                    )
                }
                "livre" -> {
                    val c = data.core(); val p = data.monthPlan(next)
                    BrainReply(
                        if (detailed) "Seu livre de verdade é ${brl(c.free)} (em conta ${brl0(c.inHand)} + a receber ${brl0(c.toReceive)} − parcelas ${brl0(c.owed)}).\nEm ${mLabel(next)}, depois das previsões e do aporte, sobram ${brl(p.restante)} para lazer."
                        else "Seu livre de verdade é ${brl(c.free)}. Em ${mLabel(next)} sobram ${brl(p.restante)} para lazer.",
                        tool = "livre_de_verdade", level = if (c.free < 0) Level.BAD else Level.OK, hasMore = !detailed
                    )
                }
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
                "mei" -> {
                    val m = data.mei(today) ?: return BrainReply("O acompanhamento do MEI está desligado. Ative em Ajustes → PJ e MEI.", tool = "mei")
                    val over = m.projectedYear - m.limit
                    BrainReply(
                        if (detailed) "Faturamento do MEI em ${today.year}:\n" + bullets(listOf(
                            "Até agora: ${brl0(m.ytd)} de ${brl0(m.limit)} (${(m.usedPct * 100).toInt()}%)",
                            "Média mensal: ${brl0(m.avgMonthly)}",
                            "Fecho do ano no ritmo atual: ${brl0(m.projectedYear)}",
                            if (over > 0) "Passa do limite em ${mLabel(m.crossMonth)} (${brl0(over)} acima)" else "Dentro do limite, com folga de ${brl0(-over)}") +
                            (if (m.estimatedMonths > 0) listOf("${m.estimatedMonths} meses estimados pela renda; registre o real") else emptyList()))
                        else if (over > 0) "Você passa do limite do MEI em ${mLabel(m.crossMonth)} no ritmo atual (${brl0(over)} acima). Planeje a ME."
                        else "Faturou ${brl0(m.ytd)} de ${brl0(m.limit)} (${(m.usedPct * 100).toInt()}%). No ritmo atual fecha o ano com folga de ${brl0(-over)}.",
                        tool = "mei", level = if (over > 0) Level.WARN else Level.OK, hasMore = !detailed
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

            // ---------- registrar ----------
            Topic.REGISTRAR -> when (d.sub) {
                "pagou" -> {
                    val f = data.forecasts(now).firstOrNull { it.cost.id == d.costId } ?: return BrainReply("Não achei essa conta neste mês.")
                    if (f.status == ForecastStatus.PAGO) return BrainReply("${f.cost.name} de ${mLabel(now)} já está confirmado como pago.")
                    BrainReply("Confirmo ${f.cost.name} de ${mLabel(now)} por ${brl(f.amount)}?$from", tool = "confirmar_pagamento", pending = DotAction.ConfirmForecast(f.cost.id, now, f.cost.name, f.amount, acc?.id))
                }
                "pagouparcela" -> {
                    val debt = data.debts.firstOrNull { it.debt.id == d.debtId } ?: return BrainReply("Não achei essa dívida.")
                    val ni = debt.installments.filter { !it.paid }.minByOrNull { it.month ?: "9999" } ?: return BrainReply("Essa dívida não tem parcelas em aberto.")
                    BrainReply("Confirmo a parcela de ${debt.debt.name} (${mLabel(ni.month)}, ${brl(ni.amount)})?$from", tool = "pagar_parcela", pending = DotAction.PayNextInstallment(debt.debt.id, debt.debt.name, ni.amount, acc?.id))
                }
                "cancelou" -> {
                    val cost = data.fixed.firstOrNull { it.id == d.costId } ?: return BrainReply("Não achei esse custo.")
                    BrainReply(
                        "Cancelo ${cost.name} a partir de ${mLabel(next)}? Isso economiza ${brl(cost.amount)} por mês (${brl0(cost.amount * 12)} por ano). Meses anteriores ficam como estão.",
                        tool = "cancelar_custo", pending = DotAction.CancelFrom(cost.id, next, cost.name)
                    )
                }
                "recebi" -> {
                    val cur = data.revenues.firstOrNull { it.month == now }?.amount
                    BrainReply(
                        "Somo ${brl(d.amount ?: 0.0)} ao faturamento de ${mLabel(now)}?" + (cur?.let { " Hoje está registrado ${brl(it)}." } ?: " Ainda não há registro neste mês (eu estimava pela sua renda)."),
                        tool = "registrar_faturamento", pending = DotAction.AddRevenue(now, d.amount ?: 0.0)
                    )
                }
                else -> BrainReply(
                    "Lanço ${brl(d.amount ?: 0.0)} em ${d.category}?$from",
                    tool = "lançar_gasto", pending = DotAction.AddExpense(d.category ?: "Gasto", d.amount ?: 0.0, d.category ?: "Outros", acc?.id)
                )
            }

            // ---------- cadastrar ----------
            Topic.CADASTRAR -> when (d.sub) {
                "custo" -> BrainReply(
                    "Cadastro “${d.name}” de ${brl(d.amount ?: 0.0)} por mês em ${data.settings.groupLabel(d.group ?: "OPCIONAL")}, ${if (d.onCard) "no cartão" else "fora do cartão"}, valendo a partir de ${mLabel(now)}?",
                    tool = "cadastrar_custo", pending = DotAction.AddFixed(d.name ?: "Custo", d.amount ?: 0.0, d.group ?: "OPCIONAL", d.onCard, now)
                )
                "conta" -> BrainReply("Cadastro a conta “${d.name}” com saldo de ${brl(d.amount ?: 0.0)} (${if (d.kind == "IPCA") "balde do IPCA+" else "dinheiro e reserva"})?", tool = "cadastrar_conta", pending = DotAction.AddAccount(d.name ?: "Conta", d.amount ?: 0.0, d.kind ?: "RESERVA"))
                "categoria" -> BrainReply("Adiciono a categoria “${d.name}” aos gastos do dia a dia?", tool = "cadastrar_categoria", pending = DotAction.AddCategory(d.name ?: "Outros"))
                "aporte" -> BrainReply("Defino o aporte de ${mLabel(d.month)} em ${brl(d.amount ?: 0.0)}? Os outros meses continuam no padrão.", tool = "definir_aporte", pending = DotAction.SetAporte(d.month ?: now, d.amount ?: 0.0))
                else -> BrainReply("Cadastro “${d.name}” de ${brl(d.amount ?: 0.0)} como algo a receber?", tool = "cadastrar_entrada", pending = DotAction.AddReceivable(d.name ?: "Entrada", d.amount ?: 0.0))
            }

            // ---------- mudar ou excluir ----------
            Topic.MUDAR -> when (d.sub) {
                "ajustar" -> {
                    val cost = data.fixed.firstOrNull { it.id == d.costId } ?: return BrainReply("Não achei esse custo.")
                    BrainReply("Mudo o valor padrão de ${cost.name} de ${brl(cost.amount)} para ${brl(d.amount ?: 0.0)}? Vale para os meses ainda em aberto.", tool = "ajustar_custo", pending = DotAction.SetFixedAmount(cost.id, cost.name, d.amount ?: 0.0))
                }
                "tirarmes" -> {
                    val cost = data.fixed.firstOrNull { it.id == d.costId } ?: return BrainReply("Não achei esse custo.")
                    BrainReply("Tiro ${cost.name} só de ${mLabel(now)}? Nos outros meses ele continua.", tool = "tirar_do_mes", pending = DotAction.RemoveFromMonth(cost.id, now, cost.name))
                }
                "excluir" -> {
                    val cost = data.fixed.firstOrNull { it.id == d.costId } ?: return BrainReply("Não achei esse custo.")
                    BrainReply("Excluo ${cost.name} de vez? Isso apaga o custo e os ajustes dele em todos os meses.", tool = "excluir_custo", pending = DotAction.DeleteFixed(cost.id, cost.name))
                }
                "apagargasto" -> BrainReply("Apago o gasto ${d.pickedLabel}? O saldo da conta não é devolvido.", tool = "apagar_gasto", pending = DotAction.DeleteExpense(d.expenseId ?: 0, d.pickedLabel ?: ""))
                "excluirentrada" -> BrainReply("Excluo a entrada ${d.pickedLabel}?", tool = "excluir_entrada", pending = DotAction.DeleteReceivable(d.receivableId ?: 0, d.pickedLabel ?: ""))
                "saldo" -> {
                    val a = data.accounts.firstOrNull { it.id == d.accountId } ?: return BrainReply("Não achei essa conta.")
                    BrainReply("Mudo o saldo de ${a.name} de ${brl(a.balance)} para ${brl(d.amount ?: 0.0)}?", tool = "ajustar_saldo", pending = DotAction.SetBalance(a.id, a.name, d.amount ?: 0.0))
                }
                "excluirconta" -> {
                    val a = data.accounts.firstOrNull { it.id == d.accountId } ?: return BrainReply("Não achei essa conta.")
                    BrainReply("Excluo a conta ${a.name}? O saldo dela (${brl(a.balance)}) deixa de contar no app.", tool = "excluir_conta", pending = DotAction.DeleteAccount(a.id, a.name))
                }
                "removercat" -> BrainReply("Removo a categoria ${d.category}? Gastos já lançados nela continuam.", tool = "remover_categoria", pending = DotAction.RemoveCategory(d.category ?: ""))
                "p_income" -> BrainReply("Mudo a renda mensal de ${brl(data.settings.income)} para ${brl(d.amount ?: 0.0)}?", tool = "ajustar_plano", pending = DotAction.ApplyChanges(mapOf("income" to (d.amount ?: 0.0)), "renda de ${brl(d.amount ?: 0.0)}"))
                "p_aporte" -> BrainReply("Mudo o aporte mensal padrão de ${brl(data.settings.aporteLongo)} para ${brl(d.amount ?: 0.0)}?", tool = "ajustar_plano", pending = DotAction.ApplyChanges(mapOf("aporteLongo" to (d.amount ?: 0.0)), "aporte padrão de ${brl(d.amount ?: 0.0)}"))
                "p_reserve" -> BrainReply("Mudo a reserva de ${data.settings.reserveMonths} para ${(d.amount ?: 0.0).toInt()} meses de essenciais?", tool = "ajustar_plano", pending = DotAction.ApplyChanges(mapOf("reserveMonths" to (d.amount ?: 6.0)), "reserva de ${(d.amount ?: 0.0).toInt()} meses"))
                else -> BrainReply("Marco ${d.pickedLabel} como recebida?$from", tool = "receber_entrada", pending = DotAction.ReceiveReceivable(d.receivableId ?: 0, d.pickedLabel ?: "", acc?.id))
            }

            // ---------- reflexão 24h ----------
            Topic.REFLEXAO -> when (d.sub) {
                "nova" -> BrainReply(
                    "Seguro “${d.name}” (${brl(d.amount ?: 0.0)}) por 24 horas? Depois eu pergunto se você ainda quer, e se desistir conta como vitória.",
                    tool = "reflexao_24h", pending = DotAction.StartReflection(d.name ?: "Compra", d.amount ?: 0.0, d.emotion ?: "", !d.onCard)
                )
                else -> {
                    val r = data.reflections.firstOrNull { it.id == d.refId } ?: return BrainReply("Não achei essa reflexão.")
                    val h = hoursLeft(r.timestamp, r.timerDurationHours)
                    val buy = d.decision == "comprar"
                    BrainReply(
                        if (buy) (if (h > 0) "Ainda faltam ${h}h do tempo de espera. " else "") + "Confirmo que você vai comprar ${r.name} (${brl(r.estimatedPrice)})?"
                        else "Confirmo que você desistiu de ${r.name}? Você economiza ${brl(r.estimatedPrice)} e ganha uma vitória.",
                        tool = "reflexao_24h", pending = DotAction.DecideReflection(r.id, r.name, buy)
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
            is DotAction.AddFixed -> "Cadastrado: ${pending.name}, ${brl(pending.amount)} por mês."
            is DotAction.SetFixedAmount -> "${pending.name} agora é ${brl(pending.amount)} por mês."
            is DotAction.DeleteFixed -> "${pending.name} foi excluído."
            is DotAction.RemoveFromMonth -> "${pending.name} saiu de ${mLabel(pending.month)}."
            is DotAction.DeleteExpense -> "Gasto apagado."
            is DotAction.AddReceivable -> "Anotado: ${pending.desc} a receber."
            is DotAction.ReceiveReceivable -> "Entrada marcada como recebida."
            is DotAction.PayNextInstallment -> "Parcela de ${pending.name} paga."
            is DotAction.AddRevenue -> "Faturamento de ${mLabel(pending.month)} atualizado."
            is DotAction.StartReflection -> "Segurando “${pending.name}” por 24h. Eu aviso quando o tempo passar."
            is DotAction.DecideReflection -> if (pending.buy) "Registrei a compra de ${pending.name}." else "Que vitória! Você desistiu de ${pending.name}."
            is DotAction.AddAccount -> "Conta ${pending.name} cadastrada."
            is DotAction.SetBalance -> "${pending.name} agora está com ${brl(pending.balance)}."
            is DotAction.DeleteAccount -> "A conta ${pending.name} foi excluída."
            is DotAction.DeleteReceivable -> "Entrada excluída."
            is DotAction.SetAporte -> "Aporte de ${mLabel(pending.month)}: ${brl(pending.amount)}."
            is DotAction.AddCategory -> "Categoria ${pending.name} adicionada."
            is DotAction.RemoveCategory -> "Categoria ${pending.name} removida."
            is DotAction.ApplyChanges -> "Aplicado: ${pending.label}."
        },
        tool = "executar_ação", run = pending, level = Level.OK
    )

    fun decline() = BrainReply("Tudo bem, não mexi em nada.")
}
