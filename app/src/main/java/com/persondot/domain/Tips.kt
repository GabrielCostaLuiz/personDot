package com.persondot.domain

import java.time.LocalDate

/** Telas em que o Dot aparece com dicas (a barra fina do topo). */
enum class TipScreen { GASTOS, RELATORIOS, FUTURO }

/** Dicas do Dot por tela, calculadas dos seus dados. A primeira é a mais importante agora. */
fun FinanceData.tipsFor(screen: TipScreen, today: LocalDate = LocalDate.now()): List<String> {
    val now = monthKey(today)
    val c = core()
    return when (screen) {
        TipScreen.GASTOS -> buildList {
            val due = dueIn(now)
            if (due.isNotEmpty()) add("Tem ${due.size} ${if (due.size == 1) "item" else "itens"} de ${mLabel(now)} para confirmar. Toque em um deles para confirmar, ajustar o valor ou remover.")
            add("Tudo aqui começa como previsão. Se cancelou uma assinatura, toque nela e use \"Cancelar daqui pra frente\".")
            val cut = cutOptional(cutRefMonth(today))
            if (cut.monthly > 0) add("Cortar os opcionais liberaria ${brl0(cut.monthly)} por mês (${brl0(cut.yearly)} por ano). Ligue a simulação para ver o que sai.")
            val lastDue = debts.flatMap { it.installments }.filter { !it.paid && it.month != null }.maxOfOrNull { it.month!! }
            if (c.owed > 0 && lastDue != null) add("Suas parcelas somam ${brl0(c.owed)} e a última vence em ${mLabel(lastDue)}. O calendário de alívio mostra quando o mês folga.")
        }
        TipScreen.RELATORIOS -> buildList {
            add("A curva do patrimônio mostra a evolução de verdade. Eu guardo uma foto sua todo mês.")
            add("Toque em um mês do MEI para registrar o faturamento real e deixar o aviso de limite preciso.")
            add("Compare previsto e pago: quando a diferença some, o mês fechou como planejado.")
            if (c.reserveTarget > 0 && c.free < c.reserveTarget) add("Sua reserva ainda não fechou. Compras grandes agora a adiam.")
        }
        TipScreen.FUTURO -> buildList {
            milestones(today).firstOrNull { !it.done }?.let { add("Sua próxima conquista: ${it.title}${it.month?.let { m -> ", prevista para ${mLabel(m)}" } ?: ""}.") }
            add("Use o \"E se...?\" para ver quanto antecipa guardar mais, cortar opcionais ou aumentar a renda.")
            add("Regra do aumento: metade de qualquer aumento de renda vai para o aporte, e a outra metade é sua.")
        }
    }
}
