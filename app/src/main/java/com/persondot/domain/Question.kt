package com.persondot.domain

import com.persondot.data.model.FixedCost
import com.persondot.data.model.categoryList
import com.persondot.data.model.groupLabel
import com.persondot.data.model.groups
import com.persondot.data.model.isOptional
import java.time.LocalDate

/**
 * Perguntas em BLOCOS. Em vez de digitar, você vai escolhendo: o tema, depois as variações
 * (valor, à vista ou parcelado, juros, mês, qual custo...). Cada bloco escolhido vira uma pecinha da pergunta
 * e a próxima escolha depende das anteriores. Quando está completa, o Dot responde (ou pede um "sim" se for mudar algo).
 */
enum class Topic { COMO_ESTA, NORTE, GUIA, COMPRA, CORTAR, E_SE, REGISTRAR, CADASTRAR, MUDAR, REFLEXAO }
enum class AmountField { VALUE, INTEREST_PCT, MONTHLY_COST, INSTALLMENTS, NAME }

val EMOTIONS = listOf("Ansiedade/Dopamina", "Tédio", "Recompensa imediata", "Influência externa", "Necessidade real")

/** O que os blocos escolhidos significam juntos. */
data class Draft(
    val topic: Topic? = null, val sub: String? = null, val month: String? = null,
    val amount: Double? = null,
    val paymentChosen: Boolean = false, val installments: Int = 1,
    val interestChosen: Boolean = false, val interestPct: Double = 0.0,
    val essential: Boolean = false, val monthlyCost: Double? = null,
    val costId: Long? = null, val costName: String? = null, val category: String? = null,
    val name: String? = null, val group: String? = null, val cardChosen: Boolean = false, val onCard: Boolean = false,
    val emotion: String? = null, val refId: Int? = null, val expenseId: Long? = null, val receivableId: Long? = null,
    val debtId: Long? = null, val decision: String? = null, val pickedLabel: String? = null,
    val accountId: Long? = null, val accountChosen: Boolean = false, val kind: String? = null
)

sealed interface Block {
    val label: String
    fun apply(d: Draft): Draft
}

data class TopicBlock(val topic: Topic) : Block {
    override val label get() = when (topic) {
        Topic.COMO_ESTA -> "Como está…"; Topic.NORTE -> "O que faço agora?"; Topic.GUIA -> "O que você me recomenda?"
        Topic.COMPRA -> "Posso comprar…"; Topic.CORTAR -> "O que posso cortar?"; Topic.E_SE -> "E se eu…"
        Topic.REGISTRAR -> "Registrar…"; Topic.CADASTRAR -> "Cadastrar…"; Topic.MUDAR -> "Mudar ou excluir…"
        Topic.REFLEXAO -> "Segurar uma compra (24h)…"
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
        AmountField.NAME -> ""
    }
    override fun apply(d: Draft) = when (field) {
        AmountField.VALUE -> d.copy(amount = value)
        AmountField.INTEREST_PCT -> d.copy(interestChosen = true, interestPct = value)
        AmountField.MONTHLY_COST -> d.copy(monthlyCost = value)
        AmountField.INSTALLMENTS -> d.copy(paymentChosen = true, installments = value.toInt().coerceAtLeast(1))
        AmountField.NAME -> d
    }
}
data class NameBlock(val text: String) : Block { override val label get() = "“$text”"; override fun apply(d: Draft) = d.copy(name = text) }
object EssentialBlock : Block {
    override val label = "é essencial"
    override fun apply(d: Draft) = d.copy(essential = true)
}
data class CostBlock(val id: Long, val name: String) : Block { override val label get() = name; override fun apply(d: Draft) = d.copy(costId = id, costName = name) }
data class CategoryBlock(val name: String) : Block { override val label get() = name; override fun apply(d: Draft) = d.copy(category = name) }
data class GroupBlock(val grp: String, override val label: String) : Block { override fun apply(d: Draft) = d.copy(group = grp) }
data class CardBlock(val on: Boolean) : Block { override val label get() = if (on) "no cartão" else "fora do cartão"; override fun apply(d: Draft) = d.copy(cardChosen = true, onCard = on) }
/** A conta de onde o dinheiro sai (ou onde entra). id = null: só registrar, sem mexer em saldo. */
data class AccountBlock(val id: Long?, override val label: String) : Block { override fun apply(d: Draft) = d.copy(accountId = id, accountChosen = true) }
data class KindBlock(val kind: String, override val label: String) : Block { override fun apply(d: Draft) = d.copy(kind = kind) }
data class EmotionBlock(val emotion: String) : Block { override val label get() = emotion; override fun apply(d: Draft) = d.copy(emotion = emotion) }
data class DecisionBlock(val kind: String, override val label: String) : Block { override fun apply(d: Draft) = d.copy(decision = kind) }
/** Um item escolhido de uma lista: kind = debt, expense, receivable ou reflection. */
data class PickBlock(val kind: String, val id: Long, override val label: String) : Block {
    override fun apply(d: Draft) = when (kind) {
        "debt" -> d.copy(debtId = id, pickedLabel = label)
        "expense" -> d.copy(expenseId = id, pickedLabel = label)
        "receivable" -> d.copy(receivableId = id, pickedLabel = label)
        "account" -> d.copy(accountId = id, pickedLabel = label)
        else -> d.copy(refId = id.toInt(), pickedLabel = label)
    }
}

fun List<Block>.toDraft(): Draft = fold(Draft()) { d, b -> b.apply(d) }

/** Um campo que o passo pede: número (valor, parcelas, juros...) ou texto (nome). */
data class AmountInput(val field: AmountField, val label: String, val hint: String) { val isText get() = field == AmountField.NAME }
data class Extra(val label: String, val block: Block? = null, val input: AmountInput? = null)

/** O que a tela mostra agora: a pergunta do passo, as opções (blocos) e/ou um campo. */
data class QStep(
    val prompt: String,
    val options: List<Block> = emptyList(),
    val input: AmountInput? = null,
    val complete: Boolean = false,
    val extras: List<Extra> = emptyList()
)

/** Horas que faltam para a reflexão cumprir o tempo de espera (0 = já pode decidir). */
fun hoursLeft(timestamp: Long, waitHours: Int, nowMs: Long = System.currentTimeMillis()): Int {
    val end = timestamp + waitHours * 3_600_000L
    return if (end <= nowMs) 0 else kotlin.math.ceil((end - nowMs) / 3_600_000.0).toInt()
}

/** Dado o que já foi escolhido, qual é o próximo passo da pergunta. */
fun nextStep(d: Draft, data: FinanceData, today: LocalDate = LocalDate.now()): QStep {
    val now = monthKey(today)
    val st = data.settings
    fun costs(list: List<FixedCost>) = list.map { CostBlock(it.id, it.name) }
    // com mais de uma conta, o Dot pergunta de qual conta mexer (nada de conta fixa)
    val liquid = data.accounts.filter { it.kind != com.persondot.data.model.Account.KIND_IPCA }
    fun accountStep(): QStep? =
        if (!d.accountChosen && liquid.size > 1)
            QStep("Em qual conta?", options = liquid.map { AccountBlock(it.id, it.name) } + AccountBlock(null, "nenhuma (só registrar)"))
        else null
    fun accounts() = data.accounts.map { PickBlock("account", it.id, "${it.name} · ${brl0(it.balance)}") }
    return when (d.topic) {
        null -> QStep("O que você quer saber ou fazer?", options = Topic.entries.map { TopicBlock(it) })
        Topic.NORTE, Topic.GUIA, Topic.CORTAR -> QStep("Pronto para perguntar.", complete = true)

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
            d.sub == null -> QStep("Como está…", options = listOf(
                SubBlock("saude", "minha saúde"), SubBlock("livre", "meu livre de verdade"), SubBlock("mes", "o mês"), SubBlock("parcelas", "as parcelas"),
                SubBlock("reserva", "a reserva"), SubBlock("riscadas", "as contas riscadas")) + (if (st.meiOn) listOf(SubBlock("mei", "meu MEI")) else emptyList()))
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
            d.sub == null -> QStep("Registrar…", options = listOf(
                SubBlock("pagou", "que paguei uma conta"), SubBlock("pagouparcela", "que paguei uma parcela"), SubBlock("gastou", "um gasto"),
                SubBlock("recebi", "que recebi (faturamento)"), SubBlock("cancelou", "que cancelei algo")))
            d.sub == "pagou" && d.costId == null -> {
                val due = data.dueIn(now).mapNotNull { it.forecast }.map { CostBlock(it.cost.id, it.cost.name) }
                if (due.isEmpty()) QStep("Não há contas previstas em aberto neste mês.") else QStep("Qual conta você pagou?", options = due.take(14))
            }
            d.sub == "pagouparcela" && d.debtId == null -> {
                val list = data.debts.filter { it.remaining > 0 }.map { PickBlock("debt", it.debt.id, it.debt.name) }
                if (list.isEmpty()) QStep("Não há parcelas em aberto.") else QStep("De qual dívida?", options = list)
            }
            d.sub == "cancelou" && d.costId == null -> {
                val list = costs(data.fixed.filter { it.activeIn(addMonths(now, 1)) }.sortedBy { if (st.isOptional(it.grp)) 0 else 1 })
                if (list.isEmpty()) QStep("Não há custos cadastrados.") else QStep("O que você cancelou?", options = list.take(16))
            }
            d.sub == "gastou" && d.amount == null -> QStep("Quanto você gastou?", input = AmountInput(AmountField.VALUE, "Valor (R$)", "ex.: 45"))
            d.sub == "gastou" && d.category == null -> QStep("Em quê?", options = st.categoryList.map { CategoryBlock(it) })
            d.sub == "recebi" && d.amount == null -> QStep("Quanto você recebeu?", input = AmountInput(AmountField.VALUE, "Valor (R$)", "ex.: 5000"))
            d.sub in setOf("pagou", "pagouparcela", "gastou") && accountStep() != null -> accountStep()!!
            else -> QStep("Pronto para registrar.", complete = true)
        }

        Topic.CADASTRAR -> when {
            d.sub == null -> QStep("Cadastrar…", options = listOf(
                SubBlock("custo", "um custo previsto"), SubBlock("receber", "algo a receber"), SubBlock("conta", "uma conta"),
                SubBlock("categoria", "uma categoria de gasto"), SubBlock("aporte", "o aporte de um mês")))
            d.sub == "aporte" && d.month == null -> QStep("De qual mês?", options = (0..11).map { MonthBlock(addMonths(now, it), mLabel(addMonths(now, it))) })
            d.sub == "aporte" && d.amount == null -> QStep("Quanto guardar nesse mês?", input = AmountInput(AmountField.VALUE, "Valor (R$)", "ex.: 800"))
            d.sub == "aporte" -> QStep("Pronto para cadastrar.", complete = true)
            d.name == null -> QStep(if (d.sub == "categoria") "Qual o nome da categoria?" else "Qual o nome?", input = AmountInput(AmountField.NAME, "Nome", if (d.sub == "conta") "ex.: Nubank" else "ex.: Spotify"))
            d.sub == "categoria" -> QStep("Pronto para cadastrar.", complete = true)
            d.amount == null -> QStep(if (d.sub == "conta") "Qual o saldo de hoje?" else "Qual o valor?", input = AmountInput(AmountField.VALUE, if (d.sub == "conta") "Saldo (R$)" else "Valor (R$)", "ex.: 21,90"))
            d.sub == "conta" && d.kind == null -> QStep("Que tipo de conta?", options = listOf(KindBlock("RESERVA", "dinheiro e reserva (liquidez diária)"), KindBlock("IPCA", "balde do IPCA+")))
            d.sub == "custo" && d.group == null -> QStep("De qual grupo?", options = st.groups.map { GroupBlock(it.id, it.name) })
            d.sub == "custo" && !d.cardChosen -> QStep("É cobrado no cartão?", options = listOf(CardBlock(true), CardBlock(false)))
            else -> QStep("Pronto para cadastrar.", complete = true)
        }

        Topic.MUDAR -> when {
            d.sub == null -> QStep("Mudar ou excluir…", options = listOf(
                SubBlock("ajustar", "o valor de um custo"), SubBlock("tirarmes", "um custo só deste mês"), SubBlock("excluir", "um custo"),
                SubBlock("apagargasto", "um gasto lançado"), SubBlock("receber", "uma entrada: marcar como recebida"),
                SubBlock("excluirentrada", "uma entrada a receber (excluir)"), SubBlock("saldo", "o saldo de uma conta"), SubBlock("excluirconta", "uma conta (excluir)"),
                SubBlock("removercat", "uma categoria (remover)"), SubBlock("p_income", "minha renda"), SubBlock("p_aporte", "o aporte mensal padrão"),
                SubBlock("p_reserve", "os meses da reserva")))
            d.sub == "ajustar" && d.costId == null -> QStep("Qual custo?", options = costs(data.fixed.filter { it.activeIn(now) }).take(18))
            d.sub == "ajustar" && d.amount == null -> QStep("Qual o novo valor mensal?", input = AmountInput(AmountField.VALUE, "Valor (R$)", "ex.: 650"))
            d.sub == "tirarmes" && d.costId == null -> {
                val list = data.forecasts(now).filter { it.counts && it.status != ForecastStatus.PAGO }.map { CostBlock(it.cost.id, it.cost.name) }
                if (list.isEmpty()) QStep("Não há custos em aberto neste mês.") else QStep("Qual custo sai só de ${mLabel(now)}?", options = list.take(18))
            }
            d.sub == "excluir" && d.costId == null -> QStep("Qual custo excluir?", options = costs(data.fixed).take(18))
            d.sub == "apagargasto" && d.expenseId == null -> {
                val list = data.expenses.take(8).map { PickBlock("expense", it.id, "${it.desc} · ${brl0(it.amount)}") }
                if (list.isEmpty()) QStep("Não há gastos lançados.") else QStep("Qual gasto apagar?", options = list)
            }
            d.sub == "receber" && d.receivableId == null -> {
                val list = data.receivables.filter { !it.received }.map { PickBlock("receivable", it.id, "${it.desc} · ${brl0(it.amount)}") }
                if (list.isEmpty()) QStep("Não há entradas a receber.") else QStep("Qual entrada chegou?", options = list)
            }
            d.sub == "receber" && accountStep() != null -> accountStep()!!
            d.sub == "excluirentrada" && d.receivableId == null -> {
                val list = data.receivables.map { PickBlock("receivable", it.id, "${it.desc} · ${brl0(it.amount)}") }
                if (list.isEmpty()) QStep("Não há entradas cadastradas.") else QStep("Qual entrada excluir?", options = list)
            }
            d.sub == "saldo" && d.accountId == null -> if (data.accounts.isEmpty()) QStep("Não há contas cadastradas.") else QStep("De qual conta?", options = accounts())
            d.sub == "saldo" && d.amount == null -> QStep("Qual o saldo atual?", input = AmountInput(AmountField.VALUE, "Saldo (R$)", "ex.: 3200"))
            d.sub == "excluirconta" && d.accountId == null -> if (data.accounts.isEmpty()) QStep("Não há contas cadastradas.") else QStep("Qual conta excluir?", options = accounts())
            d.sub == "removercat" && d.category == null -> QStep("Qual categoria remover?", options = st.categoryList.map { CategoryBlock(it) })
            d.sub == "p_income" && d.amount == null -> QStep("Qual a nova renda mensal?", input = AmountInput(AmountField.VALUE, "Renda (R$)", "ex.: 5000"))
            d.sub == "p_aporte" && d.amount == null -> QStep("Quanto guardar por mês (padrão)?", input = AmountInput(AmountField.VALUE, "Aporte (R$)", "ex.: 1500"))
            d.sub == "p_reserve" && d.amount == null -> QStep("Quantos meses de reserva?", input = AmountInput(AmountField.VALUE, "Meses", "ex.: 6"))
            else -> QStep("Pronto.", complete = true)
        }

        Topic.REFLEXAO -> when {
            d.sub == null -> QStep("Segurar uma compra…", options = listOf(SubBlock("nova", "uma nova"), SubBlock("ver", "as que estou segurando")))
            d.sub == "nova" && d.name == null -> QStep("O que você quer comprar?", input = AmountInput(AmountField.NAME, "Nome do item", "ex.: Fone novo"))
            d.sub == "nova" && d.amount == null -> QStep("Quanto custa?", input = AmountInput(AmountField.VALUE, "Valor (R$)", "ex.: 800"))
            d.sub == "nova" && d.emotion == null -> QStep("O que você está sentindo?", options = EMOTIONS.map { EmotionBlock(it) })
            d.sub == "nova" && !d.cardChosen -> QStep("Pagaria como?", options = listOf(CardBlock(false), CardBlock(true)))
            d.sub == "ver" && d.refId == null -> {
                val list = data.reflections.filter { !it.isApproved && !it.isCancelled }.map {
                    val h = hoursLeft(it.timestamp, it.timerDurationHours)
                    PickBlock("reflection", it.id.toLong(), "${it.name} · ${brl0(it.estimatedPrice)} · ${if (h > 0) "faltam ${h}h" else "pode decidir"}")
                }
                if (list.isEmpty()) QStep("Você não está segurando nenhuma compra.") else QStep("Qual delas?", options = list)
            }
            d.sub == "ver" && d.decision == null -> QStep("E então?", options = listOf(DecisionBlock("comprar", "vou comprar"), DecisionBlock("desistir", "desisti")))
            else -> QStep("Pronto.", complete = true)
        }
    }
}

/** A pergunta escrita em português, como aparece na conversa. */
fun Draft.sentence(): String = when (topic) {
    null -> ""
    Topic.NORTE -> "O que eu faço agora?"
    Topic.GUIA -> "O que você me recomenda?"
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
        "saude" -> "Como está minha saúde financeira?"
        "livre" -> "Como está meu livre de verdade?"
        "mes" -> "Como está o mês de ${mLabel(month)}?"
        "parcelas" -> "Como estão minhas parcelas?"
        "reserva" -> "Como está minha reserva?"
        "mei" -> "Como está meu MEI?"
        else -> "Como estão minhas contas riscadas?"
    }
    Topic.E_SE -> when (sub) {
        "guardar" -> "E se eu guardar ${brl0(amount ?: 0.0)} a mais por mês?"
        "aumento" -> "E se eu tiver um aumento de ${brl0(amount ?: 0.0)}?"
        else -> "E se eu cortar os opcionais?"
    }
    Topic.REGISTRAR -> when (sub) {
        "pagou" -> "Paguei ${costName ?: "a conta"}."
        "pagouparcela" -> "Paguei a parcela de ${pickedLabel ?: "uma dívida"}."
        "recebi" -> "Recebi ${brl(amount ?: 0.0)}."
        "gastou" -> "Gastei ${brl(amount ?: 0.0)} em ${category ?: "algo"}."
        else -> "Cancelei ${costName ?: "o custo"}."
    }
    Topic.CADASTRAR -> when (sub) {
        "custo" -> "Cadastrar o custo “${name ?: ""}” de ${brl(amount ?: 0.0)} por mês."
        "conta" -> "Cadastrar a conta “${name ?: ""}” com saldo de ${brl(amount ?: 0.0)}."
        "categoria" -> "Cadastrar a categoria “${name ?: ""}”."
        "aporte" -> "Guardar ${brl(amount ?: 0.0)} em ${mLabel(month)}."
        else -> "Cadastrar a receber “${name ?: ""}” de ${brl(amount ?: 0.0)}."
    }
    Topic.MUDAR -> when (sub) {
        "ajustar" -> "Mudar o valor de ${costName ?: "um custo"} para ${brl(amount ?: 0.0)}."
        "tirarmes" -> "Tirar ${costName ?: "um custo"} só deste mês."
        "excluir" -> "Excluir ${costName ?: "um custo"}."
        "apagargasto" -> "Apagar o gasto ${pickedLabel ?: ""}."
        "excluirentrada" -> "Excluir a entrada ${pickedLabel ?: ""}."
        "saldo" -> "Mudar o saldo de ${pickedLabel ?: "uma conta"} para ${brl(amount ?: 0.0)}."
        "excluirconta" -> "Excluir a conta ${pickedLabel ?: ""}."
        "removercat" -> "Remover a categoria ${category ?: ""}."
        "p_income" -> "Mudar minha renda para ${brl(amount ?: 0.0)}."
        "p_aporte" -> "Mudar o aporte mensal padrão para ${brl(amount ?: 0.0)}."
        "p_reserve" -> "Mudar a reserva para ${(amount ?: 0.0).toInt()} meses."
        else -> "Marcar ${pickedLabel ?: "a entrada"} como recebida."
    }
    Topic.REFLEXAO -> when (sub) {
        "nova" -> "Quero segurar a compra de “${name ?: ""}” (${brl0(amount ?: 0.0)}) por 24h."
        else -> "Sobre ${pickedLabel ?: "a compra"}: ${if (decision == "comprar") "vou comprar" else "desisti"}."
    }
}
