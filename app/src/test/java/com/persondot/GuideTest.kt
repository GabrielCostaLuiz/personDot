package com.persondot

import com.persondot.data.Backup
import com.persondot.data.model.Revenue
import com.persondot.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** O guia do Dot: marcos, sugestões e alertas de PJ/MEI, calculados dos seus números. */
class GuideTest {
    private val today = LocalDate.of(2026, 11, 17)

    private val base = Backup.fromJson(
        """
        {"settings":{"income":5000,"multiplier":200,"reserveMonths":6,"realRate":0.06,"aporteLongo":1500},
         "accounts":[{"name":"MP","balance":10025.81,"kind":"reserva"},{"name":"IPCA","balance":0,"kind":"ipca"}],
         "receivables":[],"debts":[],
         "fixed":[{"name":"Luz","amount":134,"group":"casa"},{"name":"Mercado","amount":600,"group":"casa"},
                  {"name":"DAS MEI","amount":86.05,"group":"trabalho"}],
         "aportes":{}}
        """.trimIndent()
    ).let { d -> d.copy(fixed = d.fixed.mapIndexed { i, f -> f.copy(id = i.toLong() + 1) }) }

    private fun ids(d: FinanceData, t: LocalDate = today) = d.insights(t).map { it.id }

    @Test fun meiForaDoLimiteNaoAvisa() {
        val m = base.mei(today)!!
        assertEquals(60_000.0, m.projectedYear, 0.01)
        assertNull(m.crossMonth)
        assertTrue("mei-estouro" !in ids(base))
    }

    @Test fun meiEstouraNoRitmoAtual() {
        val d = base.copy(settings = base.settings.copy(income = 7000.0))
        val m = d.mei(today)!!
        assertEquals(84_000.0, m.projectedYear, 0.01)
        assertEquals("2026-12", m.crossMonth)                              // 77 mil até novembro + 7 mil em dezembro > 81 mil
        val ins = d.insights(today).first { it.id == "mei-estouro" }
        assertTrue(ins.title.contains("dez/26"))
        assertEquals(InsightKind.PJ, ins.kind)
    }

    @Test fun faturamentoRealSubstituiAEstimativa() {
        val d = base.copy(revenues = listOf(Revenue("2026-01", 12_000.0)))
        assertEquals(12_000.0, d.revenueOf("2026-01"), 0.01)
        assertEquals(5_000.0, d.revenueOf("2026-02"), 0.01)
        assertEquals(55_000.0 + 7_000.0, d.mei(today)!!.ytd, 0.01)
    }

    @Test fun limiteEhEditavelEDesligavel() {
        val alto = base.copy(settings = base.settings.copy(income = 7000.0, meiLimit = 120_000.0))
        assertTrue("mei-estouro" !in ids(alto))
        assertNull(base.copy(settings = base.settings.copy(meiOn = false)).mei(today))
    }

    @Test fun dasLembraEDepoisCobra() {
        assertTrue("das" in ids(base, LocalDate.of(2026, 11, 17)))
        assertTrue("das-atraso" in ids(base, LocalDate.of(2026, 11, 22)))
        assertTrue("das" !in ids(base, LocalDate.of(2026, 11, 5)))
    }

    @Test fun multiplicadorEmFalsoComORendimentoPedeAjuste() {
        val d = base.copy(settings = base.settings.copy(realRate = 0.05))
        val ins = d.insights(today).first { it.id == "mult-coerente" }
        val act = ins.action as GuideAction.Apply
        assertEquals(240.0, act.changes.getValue("multiplier"), 0.001)
        assertEquals(240.0, d.settings.applyChanges(act.changes).multiplier, 0.001)
    }

    @Test fun planejarComMargemSugere250() {
        val ins = base.insights(today).first { it.id == "mult-margem" }
        val act = ins.action as GuideAction.Apply
        val s = base.settings.applyChanges(act.changes)
        assertEquals(250.0, s.multiplier, 0.001); assertEquals(0.048, s.realRate, 0.0001)
        assertTrue("mult-coerente" !in ids(base.copy(settings = s)))       // depois de aplicar, o plano fica coerente
    }

    @Test fun poupancaBaixaSugereGuardarMais() {
        val d = base.copy(settings = base.settings.copy(aporteLongo = 200.0))
        val ins = d.insights(today).first { it.id == "aporte-baixo" }
        assertNotNull(ins.action)
        assertEquals(750.0, (ins.action as GuideAction.Apply).changes.getValue("aporteLongo"), 0.001)   // 15% de 5.000
    }

    @Test fun marcosDePatrimonioEDeReserva() {
        val i = ids(base)
        assertTrue("marco-10000" in i)                                     // 10.025 guardados
        assertTrue("proximo-marco" in i)
        assertTrue("reserva-completa" in i && "reserva-pj" in i)           // reserva de 6 meses fechada: sugere 9 como PJ
    }

    @Test fun alertasVemAntesDosMarcos() {
        val d = base.copy(settings = base.settings.copy(income = 7000.0))
        val list = d.insights(today)
        assertEquals(Level.WARN, list.first().level)
        assertTrue(list.indexOfFirst { it.kind == InsightKind.MARCO } > list.indexOfFirst { it.id == "mei-estouro" })
    }
}
