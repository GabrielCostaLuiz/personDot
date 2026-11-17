package com.persondot.domain

import com.persondot.data.model.FixedCost
import com.persondot.data.model.categoryList
import java.time.LocalDate

/**
 * Perguntas em BLOCOS. Em vez de digitar, você vai escolhendo: o tema, depois as variações
 * (valor, à vista ou parcelado, juros, mês...). Cada bloco escolhido vira uma pecinha da pergunta
 * e a próxima escolha depende das anteriores. Quando a pergunta está completa, o Dot responde.
 */
enum class Topic { SAUDE, NORTE, LIVRE, COMPRA, COMO_ESTA, CORTAR, E_SE, REGISTRAR }
enum class AmountField { VALUE, INTEREST_PCT, MONTHLY_COST, INSTALLMENTS }

/** O que os blocos escolhidos significam juntos. */
data class Draft(
    val topic: Topic? = null, val sub: String? = null, val month: String? = null,
    val amount: Double? = null,
    val paymentChosen: Boolean = false, val installments: Int = 1,
    val interestChosen: Boolean = false, val interestPct: Double = 0.0,
    val essential: Boolean = false, val monthlyCost: Double? = null,
    val costId: Long? = null, val costName: String? = null, val category: String? = null
)

sealed interface Block {
    val label: String
    fun apply(d: Draft): Draft
}

data class TopicBlock(val topic: Topic) : Block {
    override val label get() = when (topic) {
        Topic.SAUDE -> "Como estou?"; Topic.NORTE -> "O que faço agora?"; Topic.LIVRE -> "Quanto posso gastar?"
        Topic.COMPRA -> "Posso comprar…"; Topic.COMO_ESTA -> "Como está…"; Topic.CORTAR -> "O que posso cortar?"
        Topic.E_SE -> "E se eu…"; Topic.REGISTRAR -> "Registrar…"
    }
    override fun apply(d: Draft) = d.copy(topic = topic)
}
data class SubBlock(val id: String, override val label: String) : Block { override fun apply(d: Draft) = d.copy(sub = id) }
data class MonthBlock(val month: String, override val label: String) : Block { override fun apply(d: Draft) = d.copy(month = month) }
data class PayBlock(val installments: Int) : Block {
    override val label get() = if (installments <= 1) "à vista" else "em ${installments}x"
    override fun apply(d: Draft) = d.copy(paymentChosen = true, installments = installments)
}
data class InterestBlock(val pct: Double) : Block {
    override val label get() = if (pct <= 0.0) "sem juros" else "com juros de ${"%.1f".format(java.util.Locale.forLanguageTag("pt-BR"), pct)}% ao mês"
    override fun apply(d: Draft) = d.copy(interestChosen = true, interestPct = pct)
}
data class AmountBlock(val field: AmountField, val value: Double) : Block {
    override val label get() = when (field) {
        AmountField.VALUE -> brl0(value)
        AmountField.INTEREST_PCT -> "juros de ${"%.1f".format(java.util.Locale.forLanguageTag("pt-BR"), value)}% ao mês"
        AmountField.MONTHLY_COST -> "custo de ${brl0(value)} por mês"
        AmountField.INSTALLMENTS -> "em ${value.toInt()}x"
    }
    override fun apply(d: Draft) = when (field) {
        AmountField.VALUE -> d.copy(amount = value)
        AmountField.INTEREST_PCT -> d.copy(interestChosen = true, interestPct = value)
        AmountField.MONTHLY_COST -> d.copy(monthlyCost = value)
        AmountField.INSTALLMENTS -> d.copy(paymentChosen = true, installments = value.toInt().coerceAtLeast(1))
    }
}
object EssentialBlock : Block {
    override val label = "é essencial"
    override fun apply(d: Draft) = d.copy(essential = true)
}
data class CostBlock(val id: Long, val name: String) : Block { override val label get() = name; override fun apply(d: Draft) = d.copy(costId = id, costName = name) }
data class CategoryBlock(val name: String) : Block { override val label get() = name; override fun apply(d: Draft) = d.copy(category = name) }

fun List<Block>.toDraft(): Draft = fold(Draft()) { d, b -> b.apply(d) }

/** Um campo de número que o passo pede (valor, parcelas, juros...). */
data class AmountInput(val field: AmountField, val label: String, val hint: String)
data class Extra(val label: String, val block: Block? = null, val input: AmountInput? = null)

/** O que a tela mostra agora: a pergunta do passo, as opções (blocos) e/ou um campo de número. */
data class QStep(
    val prompt: String,
    val options: List<Block> = emptyList(),
    val input: AmountInput? = null,
    val complete: Boolean = false,
    val extras: List<Extra> = emptyList()
)

/** Dado o que já foi escolhido, qual é o próximo passo da pergunta. */
fun nextStep(d: Draft, data: FinanceData, today: LocalDate = LocalDate.now()): QStep {
    val now = monthKey(today)
    return when (d.topic) {
        null -> QStep("O que você quer saber?", options = Topic.entries.map { TopicBlock(it) })
        Topic.SAUDE, Topic.NORTE, Topic.LIVRE, Topic.CORTAR -> QStep("Pronto para perguntar.", complete = true)

        Topic.COMPRA -> when {
            d.amount == null -> QStep("Quanto custa?", input = AmountInput(AmountField.VALUE, "Valor da compra (R$)", "ex.: 2000"))
            !d.paymentChosen -> QStep(
                "Como vai pagar?", options = listOf(1, 3, 6, 10, 12).map { PayBlock(it) },
                input = AmountInput(AmountField.INSTALLMENTS, "Outro número de parcelas", "ex.: 8")
            )
            d.installments > 1 && !d.interestChosen -> QStep(
                "Tem juros?", options = listOf(InterestBlock(0.0)),
                input = AmountInput(AmountField.INTEREST_PCT, "Juros ao mês (%)", "ex.: 2,5")
            )
            else -> QStep("Pronto. Quer acrescentar algo à pergunta?", complete = true, extras = buildList {
                if (!d.essential) add(Extra("É essencial", block = EssentialBlock))
                if (d.monthlyCost == null) add(Extra("Vem com custo mensal…", input = AmountInput(AmountField.MONTHLY_COST, "Custo por mês (R$)", "ex.: 400")))
            })
        }

        Topic.COMO_ESTA -> when {
            d.sub == null -> QStep("Como está…", options = listOf(SubBlock("mes", "o mês"), SubBlock("parcelas", "as parcelas"), SubBlock("reserva", "a reserva"), SubBlock("riscadas", "as contas riscadas")))
            d.sub == "mes" && d.month == null -> QStep("De qual mês?", options = listOf(MonthBlock(now, "deste mês")) + (1..4).map { MonthBlock(addMonths(now, it), "de ${mLabel(addMonths(now, it))}") })
            else -> QStep("Pronto para perguntar.", complete = true)
        }

        Topic.E_SE -> when {
            d.sub == null -> QStep("E se eu…", options = listOf(SubBlock("guardar", "guardar a mais"), SubBlock("aumento", "tiver um aumento"), SubBlock("cortar", "cortar os opcionais")))
            (d.sub == "guardar" || d.sub == "aumento") && d.amount == null ->
                QStep(if (d.sub == "guardar") "Quanto a mais por mês?" else "De quanto é o aumento?", input = AmountInput(AmountField.VALUE, "Valor (R$)", "ex.: 500"))
            else -> QStep("Pronto para perguntar.", complete = true)
        }

        Topic.REGISTRAR -> when {
            d.sub == null -> QStep("Registrar…", options = listOf(SubBlock("pagou", "que paguei uma conta"), SubBlock("gastou", "um gasto"), SubBlock("cancelou", "que cancelei algo")))
            d.sub == "pagou" && d.costId == null -> {
                val due = data.dueIn(now).mapNotNull { it.forecast }.map { CostBlock(it.cost.id, it.cost.name) }
                if (due.isEmpty()) QStep("Não há contas previstas em aberto neste mês.") else QStep("Qual conta você pagou?", options = due.take(14))
            }
            d.sub == "cancelou" && d.costId == null -> {
                val ref = addMonths(now, 1)
                val list = data.fixed.filter { it.activeIn(ref) }.sortedBy { if (it.grp == FixedCost.GRP_OPCIONAL) 0 else 1 }.map { CostBlock(it.id, it.name) }
                if (list.isEmpty()) QStep("Não há custos cadastrados.") else QStep("O que você cancelou?", options = list.take(16))
            }
            d.sub == "gastou" && d.amount == null -> QStep("Quanto você gastou?", input = AmountInput(AmountField.VALUE, "Valor (R$)", "ex.: 45"))
            d.sub == "gastou" && d.category == null -> QStep("Em quê?", options = data.settings.categoryList.map { CategoryBlock(it) })
            else -> QStep("Pronto para registrar.", complete = true)
        }
    }
}

/** A pergunta escrita em português, como aparece na conversa. */
fun Draft.sentence(): String = when (topic) {
    null -> ""
    Topic.SAUDE -> "Como estou?"
    Topic.NORTE -> "O que eu faço agora?"
    Topic.LIVRE -> "Quanto posso gastar?"
    Topic.CORTAR -> "O que posso cortar?"
    Topic.COMPRA -> buildString {
        append("Posso comprar algo de ${brl0(amount ?: 0.0)} ")
        append(if (installments <= 1) "à vista" else "em ${installments}x")
        if (installments > 1) append(if (interestPct > 0) " com juros de ${interestPct}% ao mês" else " sem juros")
        if (essential) append(", sendo essencial")
        monthlyCost?.let { append(", e vem com custo de ${brl0(it)} por mês") }
        append("?")
    }
    Topic.COMO_ESTA -> when (sub) {
        "mes" -> "Como está o mês de ${mLabel(month)}?"
        "parcelas" -> "Como estão minhas parcelas?"
        "reserva" -> "Como está minha reserva?"
        else -> "Como estão minhas contas riscadas?"
    }
    Topic.E_SE -> when (sub) {
        "guardar" -> "E se eu guardar ${brl0(amount ?: 0.0)} a mais por mês?"
        "aumento" -> "E se eu tiver um aumento de ${brl0(amount ?: 0.0)}?"
        else -> "E se eu cortar os opcionais?"
    }
    Topic.REGISTRAR -> when (sub) {
        "pagou" -> "Paguei ${costName ?: "a conta"}."
        "gastou" -> "Gastei ${brl(amount ?: 0.0)} em ${category ?: "algo"}."
        else -> "Cancelei ${costName ?: "o custo"}."
    }
}
