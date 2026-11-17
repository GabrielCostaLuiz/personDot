package com.persondot.domain

import com.persondot.data.model.Account
import com.persondot.data.model.FixedCost
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

/**
 * O "cérebro" do chat do Dot. Não usa IA nem internet: entende frases em português por padrões e chama
 * as mesmas ferramentas que o app já tem (livre de verdade, simulador, previsões, corte, evolução...).
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
    val quick: List<String> = emptyList(),  // respostas sugeridas
    val pending: DotAction? = null,         // aguardando "sim" ou "não"
    val run: DotAction? = null,             // ação confirmada: o app deve executar
    val level: Level? = null                // humor do Dot depois da resposta
)

object DotBrain {

    // ---------- texto ----------
    fun norm(s: String): String = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").trim()

    private val yesWords = setOf("sim", "s", "isso", "pode", "confirmo", "confirma", "ok", "claro", "bora", "fechado", "manda", "yes", "pode sim", "pode ser")
    private val noWords = setOf("nao", "n", "deixa", "esquece", "nope", "deixa pra la", "melhor nao", "cancela isso")

    private fun has(t: String, pattern: String) = Regex(pattern).containsMatchIn(t)

    private val installmentRe = Regex("""(\d+)\s*(?:x|vezes|parcelas?)\b""")
    private val percentRe = Regex("""\d+(?:[.,]\d+)?\s*%""")
    private val moneyRe = Regex("""(?:r\$\s*)?(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{1,2}))?\s*(mil|k)?\b""")

    /** Valores em reais citados no texto (ignora "10x" e porcentagens). */
    fun amounts(t: String): List<Double> {
        val clean = t.replace(installmentRe, " ").replace(percentRe, " ")
        return moneyRe.findAll(clean).mapNotNull { m ->
            val int = m.groupValues[1].replace(".", "").toDoubleOrNull() ?: return@mapNotNull null
            val frac = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { it.padEnd(2, '0').toDouble() / 100 } ?: 0.0
            val mult = if (m.groupValues[3].isNotEmpty()) 1000.0 else 1.0
            (int + frac) * mult
        }.toList()
    }

    private fun installments(t: String): Int? = installmentRe.find(t)?.groupValues?.get(1)?.toIntOrNull()

    private val monthNames = listOf("janeiro", "fevereiro", "marco", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")

    private fun monthFrom(t: String, today: LocalDate): String? {
        val now = monthKey(today)
        if (has(t, "(este|esse|neste|nesse) mes")) return now
        if (has(t, "(proximo|que vem|seguinte)")) return addMonths(now, 1)
        val idx = monthNames.indexOfFirst { t.contains(it) }
        if (idx >= 0) return (0..14).map { addMonths(now, it) }.firstOrNull { it.endsWith("-" + (idx + 1).toString().padStart(2, '0')) }
        return null
    }

    /** Custo fixo citado no texto (por palavra inteira do nome). */
    private fun FinanceData.findCost(t: String): FixedCost? = fixed.firstOrNull { f ->
        norm(f.name).replace(Regex("\\(.*?\\)"), " ").split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }.any { tok -> Regex("\\b$tok\\b").containsMatchIn(t) }
    }

    private fun categoryFor(t: String): String = when {
        has(t, "mercado|feira|supermercado|padaria") -> "Mercado extra"
        has(t, "uber|99|gasolina|combustivel|onibus|metro|estacionamento|taxi") -> "Transporte"
        has(t, "lanche|ifood|restaurante|pizza|hamburguer|cafe|almoco|jantar|bar\\b") -> "Alimentação fora"
        has(t, "cinema|show|festa|viagem|jogo|streaming|lazer|passeio") -> "Lazer"
        has(t, "barbeiro|cabelo|farmacia|remedio|medico|dentista|salao") -> "Beleza e saúde"
        has(t, "carro|oficina|pneu|seguro") -> "Carro"
        has(t, "roupa|tenis|camisa|fone|celular|presente") -> "Compras"
        else -> "Outros"
    }

    private fun bullets(items: List<String>) = items.joinToString("\n") { "• $it" }
    private fun FinanceData.liquidAccount() = accounts.firstOrNull { it.kind != Account.KIND_IPCA }

    // ---------- resposta ----------
    fun reply(input: String, data: FinanceData, pending: DotAction? = null, today: LocalDate = LocalDate.now()): BrainReply {
        val t = norm(input)
        val now = monthKey(today)
        val next = addMonths(now, 1)

        // 0) confirmação de uma ação pendente
        if (pending != null) {
            if (t in yesWords || t.startsWith("sim ")) return BrainReply(
                when (pending) {
                    is DotAction.ConfirmForecast -> "Pronto! Confirmei ${pending.name} de ${mLabel(pending.month)} por ${brl(pending.amount)}."
                    is DotAction.CancelFrom -> "Feito. ${pending.name} não aparece mais de ${mLabel(pending.month)} em diante."
                    is DotAction.AddExpense -> "Lançado: ${brl(pending.amount)} em ${pending.cat}."
                },
                tool = "executar_ação", run = pending, level = Level.OK, quick = data.chatSuggestions().take(3)
            )
            if (t in noWords) return BrainReply("Tudo bem, não mexi em nada.", quick = data.chatSuggestions().take(3))
            // qualquer outra frase descarta a pendência e segue como pergunta nova
        }

        // 1) papo básico
        if (has(t, "^(oi|ola|e ai|opa|hey|fala|bom dia|boa tarde|boa noite)\\b")) {
            val h = data.health()
            return BrainReply(
                "Oi! Eu sou o Dot. Sua saúde financeira está em ${h.score}/100 e seu livre de verdade em ${brl(data.core().free)}. Quer que eu diga o que fazer agora?",
                quick = listOf("O que eu faço agora?", "Como estou?", "Quanto posso gastar?")
            )
        }
        if (has(t, "obrigad|valeu|thanks|show de bola")) return BrainReply("De nada! Estou aqui quando precisar.", quick = data.chatSuggestions().take(3))
        if (has(t, "ajuda|o que voce (faz|sabe)|comandos|como funciona|menu")) return BrainReply(
            "Eu cuido destas coisas com você:\n" + bullets(listOf(
                "\"Como estou?\" mostra sua saúde financeira",
                "\"Posso comprar um fone de 800 em 4x?\" simula a compra",
                "\"Paguei a luz\" confirma o pagamento do mês",
                "\"Cancelei a Disney\" tira o custo daqui pra frente",
                "\"Gastei 45 no lanche\" lança um gasto",
                "\"O que posso cortar?\" mostra o que sai dos opcionais",
                "\"E se eu guardar 300 a mais?\" mostra quanto antecipa",
                "Reserva, parcelas, contas riscadas e o próximo passo"
            )),
            quick = data.chatSuggestions().take(3)
        )

        // 2) ações que mudam dados (sempre com confirmação)
        val cost = data.findCost(t)
        if (cost != null && has(t, "paguei|pago|quitei|confirm")) {
            val f = data.forecasts(now).firstOrNull { it.cost.id == cost.id }
            if (f == null) return BrainReply("${cost.name} não está previsto para ${mLabel(now)}.")
            if (f.status == ForecastStatus.PAGO) return BrainReply("${cost.name} de ${mLabel(now)} já está confirmado como pago.")
            val amount = amounts(t).firstOrNull() ?: f.amount
            val acc = data.liquidAccount()
            return BrainReply(
                "Confirmo ${cost.name} de ${mLabel(now)} por ${brl(amount)}?" + (acc?.let { " Eu desconto da conta ${it.name}." } ?: ""),
                tool = "confirmar_pagamento", quick = listOf("Sim", "Não"),
                pending = DotAction.ConfirmForecast(cost.id, now, cost.name, amount)
            )
        }
        if (cost != null && has(t, "cancel|tirar|remover|cortar|parei")) {
            return BrainReply(
                "Cancelo ${cost.name} a partir de ${mLabel(next)}? Isso economiza ${brl(cost.amount)} por mês (${brl0(cost.amount * 12)} por ano). Meses anteriores ficam como estão.",
                tool = "cancelar_custo", quick = listOf("Sim", "Não"),
                pending = DotAction.CancelFrom(cost.id, next, cost.name)
            )
        }

        // 3) simulador de compra
        val values = amounts(t)
        if (has(t, "vale a pena|comprar|compro\b|compraria|posso (pagar|parcelar)|devo (pagar|parcelar)")) {
            if (values.isEmpty()) return BrainReply(
                "Quanto custa? Me diga o valor, por exemplo: \"posso comprar um fone de 800 em 4x?\"",
                quick = listOf("Posso comprar algo de 1500?")
            )
            val n = installments(t) ?: 1
            val mode = when {
                has(t, "com juros") -> PayMode.COM_JUROS
                n > 1 -> PayMode.SEM_JUROS
                else -> PayMode.VISTA
            }
            val juros = Regex("""(\d+(?:[.,]\d+)?)\s*%""").find(t)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
            val rec = if (values.size > 1 && has(t, "por mes|mensal|ao mes")) values[1] else 0.0
            val r = data.evalPurchase(PurchaseInput(valor = values[0], mode = mode, parcelas = n, jurosMensalPct = juros, recorrente = rec, desejo = !has(t, "preciso|essencial|necessari")), today)
            val head = "${r.verdict}. (${brl(values[0])}${if (mode == PayMode.VISTA) " à vista" else " em ${n}x de ${brl(r.parcela)}"})"
            return BrainReply(head + "\n" + bullets(r.lines.map { it.second }.take(4)), tool = "simular_compra", level = r.level,
                quick = listOf("E se for em 10x?", "O que posso cortar?", "O que eu faço agora?"))
        }

        // 4) lançar gasto
        if (values.isNotEmpty() && has(t, "gastei|gasto de|comprei|paguei|saiu|lance|lancar|anota")) {
            val desc = Regex("""(?:no|na|em|de|com|pro|pra|para)\s+([a-z0-9 ]{3,30})$""").find(t)?.groupValues?.get(1)?.trim()
                ?.replaceFirstChar { it.uppercase() } ?: "Gasto"
            val cat = categoryFor(t)
            val acc = data.liquidAccount()
            return BrainReply(
                "Lanço ${brl(values[0])} em $cat ($desc)?" + (acc?.let { " Eu desconto da conta ${it.name}." } ?: ""),
                tool = "lançar_gasto", quick = listOf("Sim", "Não"), pending = DotAction.AddExpense(desc, values[0], cat)
            )
        }

        // 5) "e se...?"
        if (values.isNotEmpty() && has(t, "e se|se eu (guardar|aportar|investir|ganhar)|aumento|aumentar")) {
            val raise = has(t, "aumento|salario|renda|ganhar")
            val w = data.whatIf(if (raise) 0.0 else values[0], false, if (raise) values[0] else 0.0, today)
            val lines = buildList {
                add("${if (raise) "Aumento de ${brl0(values[0])} (metade vai para o aporte)" else "Mais ${brl0(values[0])} por mês"}: ${brl0(w.extraMonthly)}/mês a mais no aporte.")
                add("Reserva completa em ${mLabel(w.reserveEta)}${if (w.reserveSooner > 0) ", ${w.reserveSooner} ${if (w.reserveSooner == 1) "mês" else "meses"} antes" else ""}.")
                data.riscadas(today, w.extraMonthly).firstOrNull { !it.done }?.let { add("Primeira conta riscada (${it.cost.name}) em ${mLabel(w.firstBillEta)}${if (w.firstBillSooner > 0) ", ${w.firstBillSooner} meses antes" else ""}.") }
            }
            return BrainReply(bullets(lines), tool = "e_se", level = Level.OK, quick = listOf("O que posso cortar?", "O que eu faço agora?"))
        }

        // 6) consultas
        if (has(t, "cortar|corte|economizar|economia|opcionais|assinaturas|superfluo")) {
            val ref = data.cutRefMonth(today)
            val cut = data.cutOptional(ref)
            if (cut.items.isEmpty()) return BrainReply("Não vejo opcionais em aberto para ${mLabel(ref)}. Você já está enxuto!", tool = "corte_de_opcionais", level = Level.OK)
            val w = data.whatIf(0.0, true, 0.0, today)
            return BrainReply(
                "Se cortar todos os opcionais (valores de ${mLabel(ref)}), sai:\n" + bullets(cut.items.map { "${it.cost.name}: ${brl(it.amount)}" }) +
                    "\nTotal: ${brl(cut.monthly)} por mês, ${brl0(cut.yearly)} por ano." +
                    (if (w.reserveSooner > 0) " A reserva fecharia ${w.reserveSooner} ${if (w.reserveSooner == 1) "mês" else "meses"} antes." else "") +
                    "\nNa aba Gastos você liga a simulação e cancela o que quiser.",
                tool = "corte_de_opcionais", level = Level.OK, quick = listOf("Cancelei a Disney", "O que eu faço agora?")
            )
        }
        if (has(t, "mes|janeiro|fevereiro|marco|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro") && has(t, "como|resumo|previsto|fecha|sobra|gastos|conta|vai ser|esta")) {
            val m = monthFrom(t, today) ?: if (has(t, "mes")) now else null
            if (m != null) {
                val p = data.monthPlan(m)
                return BrainReply(
                    "${mLabel(m)}:\n" + bullets(listOf(
                        "Previsto: ${brl(p.previsto)} (fixos ${brl0(p.fixedTotal)} + parcelas ${brl0(p.parcelas)})",
                        "Já pago: ${brl(p.pago)} · falta pagar ${brl(p.aPagar)}",
                        "Aporte planejado: ${brl(p.aporte)}",
                        "Sobra para lazer: ${brl(p.restante)}"
                    )),
                    tool = "resumo_do_mes", level = if (p.sobra < 0) Level.BAD else Level.OK, quick = listOf("Como estão minhas parcelas?", "O que posso cortar?")
                )
            }
        }
        if (has(t, "parcela|divida|devo|fatura|cartao|renner|nubank|vence")) {
            val c = data.core()
            if (c.owed == 0.0) return BrainReply("Você não tem parcelas em aberto. Que alívio!", tool = "dividas", level = Level.OK)
            val lines = data.debts.filter { it.remaining > 0 }.map { d ->
                val nextI = d.installments.filter { !it.paid }.minByOrNull { it.month ?: "9999" }
                "${d.debt.name}: faltam ${brl(d.remaining)}${nextI?.let { ", próxima em ${mLabel(it.month)} (${brl(it.amount)})" } ?: ""}"
            }
            val last = data.debts.flatMap { it.installments }.filter { !it.paid && it.month != null }.maxOfOrNull { it.month!! }
            return BrainReply(
                "Você tem ${brl(c.owed)} em parcelas:\n" + bullets(lines) + (last?.let { "\nA última vence em ${mLabel(it)}; depois disso o orçamento folga." } ?: ""),
                tool = "dividas", level = Level.WARN, quick = listOf("Como estou?", "O que eu faço agora?")
            )
        }
        if (has(t, "reserva|emergencia")) {
            val c = data.core()
            val gap = c.reserveTarget - c.free
            return if (gap <= 0) BrainReply("Sua reserva está completa: ${brl0(c.free)} para uma meta de ${brl0(c.reserveTarget)}. Já pode começar o balde IPCA+.", tool = "reserva", level = Level.OK)
            else BrainReply(
                "Sua reserva está em ${brl0(c.free)} de ${brl0(c.reserveTarget)} (${(c.free / c.reserveTarget * 100).toInt()}%). Faltam ${brl0(gap)}; no aporte planejado fecha em ${mLabel(data.reserveEta(0.0, today))}. Mantenha em liquidez diária.",
                tool = "reserva", level = Level.WARN, quick = listOf("E se eu guardar 500 a mais?", "O que posso cortar?")
            )
        }
        if (has(t, "riscad|independen|ipca|investir|investimento|rendimento|regra dos 200")) {
            val r = data.riscadas(today)
            val c = data.core()
            return BrainReply(
                "Contas riscadas (valor × ${data.settings.multiplier.toInt()}), da menor para a maior:\n" +
                    bullets(r.map { "${it.cost.name}: ${if (it.done) "riscada" else "meta ${brl0(it.cum)} acumulados, previsão ${mLabel(it.eta)}"}" }) +
                    "\nHoje o balde IPCA+ tem ${brl0(c.ipca)}." + (if (c.free < c.reserveTarget) " Primeiro complete a reserva; o IPCA+ começa depois." else ""),
                tool = "contas_riscadas", level = Level.OK, quick = listOf("E se eu guardar 300 a mais?", "Como está minha reserva?")
            )
        }
        if (has(t, "dica|o que (eu )?faco|proximo passo|norte|por onde|comecar|prioridade")) {
            val steps = data.norte(today)
            return BrainReply(
                "Seu norte agora:\n" + steps.mapIndexed { i, s -> "${i + 1}. ${s.title}: ${s.detail}" }.joinToString("\n"),
                tool = "norte", level = Level.OK, quick = data.chatSuggestions().take(3)
            )
        }
        if (has(t, "saude|nota|como estou|situacao|visao geral|resumo geral|overview")) {
            val h = data.health()
            return BrainReply(
                "Sua saúde financeira está em ${h.score}/100:\n" + bullets(h.items.map { "${it.name}: ${it.value}" }),
                tool = "saude_financeira",
                level = if (h.score >= 75) Level.OK else if (h.score >= 50) Level.WARN else Level.BAD,
                quick = listOf("O que eu faço agora?", "O que posso cortar?")
            )
        }
        if (has(t, "livre|quanto tenho|saldo|posso gastar|sobra|dinheiro|gastar")) {
            val c = data.core(); val p = data.monthPlan(next)
            return BrainReply(
                "Seu livre de verdade é ${brl(c.free)} (em conta ${brl0(c.inHand)} + a receber ${brl0(c.toReceive)} − parcelas ${brl0(c.owed)}).\n" +
                    "Em ${mLabel(next)}, depois das previsões e do aporte, sobram ${brl(p.restante)} para lazer.",
                tool = "livre_de_verdade", level = if (c.free < 0) Level.BAD else Level.OK, quick = listOf("Posso comprar algo de 1500?", "Como estou?")
            )
        }

        return BrainReply(
            "Ainda não entendi essa. Eu sei falar de saúde financeira, compras, parcelas, reserva, contas riscadas e do seu mês. Tente uma destas:",
            quick = data.chatSuggestions().take(4)
        )
    }
}
