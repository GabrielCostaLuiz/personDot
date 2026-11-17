package com.persondot

import com.persondot.data.Backup
import com.persondot.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DotBrainTest {
    private val today = LocalDate.of(2026, 11, 17)

    private val data = Backup.fromJson(
        """
        {"settings":{"income":5000,"multiplier":200,"reserveMonths":6,"realRate":0.06,"aporteLongo":1500},
         "accounts":[{"name":"MP","balance":10025.81,"kind":"reserva"},{"name":"IPCA","balance":0,"kind":"ipca"}],
         "receivables":[],
         "debts":[{"name":"Nubank","installments":[{"month":"2026-12","amount":3241.21},{"month":"2027-01","amount":1717.35}]}],
         "fixed":[{"name":"Luz","amount":134,"group":"casa"},{"name":"Mercado","amount":600,"group":"casa"},
                  {"name":"DAS MEI","amount":86.05,"group":"trabalho"},
                  {"name":"Disney+","amount":29.9,"group":"opcional","card":true},{"name":"Academia","amount":159.9,"group":"opcional"}],
         "aportes":{"2026-12":0,"2027-01":500}}
        """.trimIndent()
    ).let { d -> d.copy(fixed = d.fixed.mapIndexed { i, f -> f.copy(id = i.toLong() + 1) }) }

    private fun ask(q: String, pending: DotAction? = null) = DotBrain.reply(q, data, pending, today)

    @Test fun entendeValoresEmPortugues() {
        assertEquals(listOf(2000.0), DotBrain.amounts("posso comprar um celular de 2000 em 10x"))
        assertEquals(listOf(1500.5), DotBrain.amounts("1.500,50"))
        assertEquals(listOf(2000.0), DotBrain.amounts("2 mil"))
        assertEquals(listOf(800.0), DotBrain.amounts("fone de r$ 800 com 2,5% de juros"))
    }

    @Test fun simulaCompraParcelada() {
        val r = ask("Posso comprar um celular de 2000 em 10x?")
        assertEquals("simular_compra", r.tool)
        assertNotNull(r.level)
        assertTrue(r.text.contains("10x"))
    }

    @Test fun pedeValorQuandoFaltar() {
        val r = ask("posso comprar um carro?")
        assertNull(r.tool)
        assertTrue(r.text.contains("Quanto custa"))
    }

    @Test fun confirmarPagamentoSoDepoisDoSim() {
        val ask = ask("paguei a luz")
        assertEquals("confirmar_pagamento", ask.tool)
        val pending = ask.pending as DotAction.ConfirmForecast
        assertEquals("Luz", pending.name); assertEquals(134.0, pending.amount, 0.001); assertEquals("2026-11", pending.month)
        assertNull(ask.run)                                   // nada executa antes do "sim"

        val yes = ask("sim", ask.pending)
        assertEquals(pending, yes.run)                        // agora sim
        assertNull(yes.pending)

        val no = ask("não", ask.pending)
        assertNull(no.run)
    }

    @Test fun cancelarCustoDaquiPraFrente() {
        val r = ask("cancelei a Disney")
        val p = r.pending as DotAction.CancelFrom
        assertEquals("2026-12", p.month)                      // a partir do mês seguinte
        assertTrue(r.text.contains("29,90"))
    }

    @Test fun lancaGastoComCategoria() {
        val r = ask("gastei 45 no lanche")
        val p = r.pending as DotAction.AddExpense
        assertEquals(45.0, p.amount, 0.001); assertEquals("Alimentação fora", p.cat)
    }

    @Test fun mostraOQueCortar() {
        val r = ask("o que posso cortar?")
        assertEquals("corte_de_opcionais", r.tool)
        assertTrue(r.text.contains("Disney+") && r.text.contains("Academia"))
    }

    @Test fun responderSobreMesReservaESaude() {
        assertEquals("resumo_do_mes", ask("como está o mês que vem?").tool)
        assertEquals("reserva", ask("como está minha reserva?").tool)
        assertEquals("saude_financeira", ask("como estou?").tool)
        assertEquals("norte", ask("o que eu faço agora?").tool)
        assertEquals("dividas", ask("como estão minhas parcelas?").tool)
        assertEquals("livre_de_verdade", ask("quanto posso gastar?").tool)
    }

    @Test fun eSeComAporteMaior() {
        val r = ask("e se eu guardar 500 a mais?")
        assertEquals("e_se", r.tool)
    }

    @Test fun naoEntendeFrasesSolta() {
        val r = ask("blablabla")
        assertNull(r.tool)
        assertTrue(r.quick.isNotEmpty())
    }
}
