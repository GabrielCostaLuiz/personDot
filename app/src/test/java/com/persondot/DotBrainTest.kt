package com.persondot

import com.persondot.data.Backup
import com.persondot.data.model.FixedCost
import com.persondot.data.model.categoryList
import com.persondot.data.model.groupLabel
import com.persondot.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** As perguntas e pedidos ao Dot são montados em blocos; aqui conferimos cada caminho e o tamanho das respostas. */
class DotBrainTest {
    private val today = LocalDate.of(2026, 11, 17)

    private val data = Backup.fromJson(
        """
        {"settings":{"income":5000,"multiplier":200,"reserveMonths":6,"realRate":0.06,"aporteLongo":1500,
                     "categories":"Mercado extra|Pet|Outros","groupNames":"Moradia|Trabalho|Supérfluos"},
         "accounts":[{"name":"MP","balance":10025.81,"kind":"reserva"},{"name":"IPCA","balance":0,"kind":"ipca"}],
         "receivables":[{"desc":"Acerto","amount":900,"received":false}],
         "debts":[{"name":"Nubank","installments":[{"month":"2026-12","amount":3241.21},{"month":"2027-01","amount":1717.35}]}],
         "fixed":[{"name":"Luz","amount":134,"group":"casa"},{"name":"Mercado","amount":600,"group":"casa"},
                  {"name":"DAS MEI","amount":86.05,"group":"trabalho"},
                  {"name":"Disney+","amount":29.9,"group":"opcional","card":true},{"name":"Academia","amount":159.9,"group":"opcional"}],
         "expenses":[{"desc":"Lanche","amount":45,"cat":"Outros","date":"2026-11-10"}],
         "aportes":{"2026-12":0,"2027-01":500}}
        """.trimIndent()
    ).let { d -> d.copy(fixed = d.fixed.mapIndexed { i, f -> f.copy(id = i.toLong() + 1) }) }

    private fun draft(vararg b: Block) = b.toList().toDraft()
    private fun ask(vararg b: Block, detailed: Boolean = false) = DotBrain.answer(draft(*b), data, detailed, today)
    private fun luz() = data.fixed.first { it.name == "Luz" }
    private fun disney() = data.fixed.first { it.name == "Disney+" }

    // ---------- construção da pergunta ----------
    @Test fun primeiroPassoOferecaOsTemas() {
        val s = nextStep(Draft(), data, today)
        assertEquals(Topic.entries.size, s.options.size)
        assertTrue(!s.complete)
    }

    @Test fun compraSeConstroiPassoAPasso() {
        var b = listOf<Block>(TopicBlock(Topic.COMPRA))
        assertEquals(AmountField.VALUE, nextStep(b.toDraft(), data, today).input?.field)
        b = b + AmountBlock(AmountField.VALUE, 2000.0)
        val pay = nextStep(b.toDraft(), data, today)
        assertTrue(pay.options.any { it.label == "à vista" } && pay.options.any { it.label == "em 10x" })
        b = b + PayBlock(10)
        assertEquals("Tem juros?", nextStep(b.toDraft(), data, today).prompt)
        b = b + InterestBlock(0.0)
        val done = nextStep(b.toDraft(), data, today)
        assertTrue(done.complete)
        assertEquals("Posso comprar algo de R$ 2.000 em 10x sem juros?", b.toDraft().sentence())
    }

    @Test fun comoEstaTemSubtemasInclusiveMei() {
        val subs = nextStep(draft(TopicBlock(Topic.COMO_ESTA)), data, today).options.map { it.label }
        assertTrue("minha saúde" in subs && "meu livre de verdade" in subs && "meu MEI" in subs)
        assertEquals("De qual mês?", nextStep(draft(TopicBlock(Topic.COMO_ESTA), SubBlock("mes", "o mês")), data, today).prompt)
    }

    @Test fun categoriasEGruposVemDosAjustes() {
        val s = nextStep(draft(TopicBlock(Topic.REGISTRAR), SubBlock("gastou", "um gasto"), AmountBlock(AmountField.VALUE, 45.0)), data, today)
        assertEquals(listOf("Mercado extra", "Pet", "Outros"), s.options.map { it.label })
        assertEquals("Moradia", data.settings.groupLabel(FixedCost.GRP_CASA))
    }

    // ---------- respostas ----------
    @Test fun respostaDiretaECurtaEDetalhadaECompleta() {
        val blocks = arrayOf<Block>(TopicBlock(Topic.COMPRA), AmountBlock(AmountField.VALUE, 2000.0), PayBlock(10), InterestBlock(0.0))
        val direto = ask(*blocks)
        val detalhado = ask(*blocks, detailed = true)
        assertEquals("simular_compra", direto.tool)
        assertTrue(direto.hasMore && !detalhado.hasMore)
        assertTrue(detalhado.text.length > direto.text.length)
    }

    @Test fun cadaTemaChamaSuaFerramenta() {
        fun como(sub: String, vararg extra: Block) = ask(TopicBlock(Topic.COMO_ESTA), SubBlock(sub, sub), *extra).tool
        assertEquals("saude_financeira", como("saude"))
        assertEquals("livre_de_verdade", como("livre"))
        assertEquals("resumo_do_mes", como("mes", MonthBlock("2026-12", "de dez/26")))
        assertEquals("dividas", como("parcelas"))
        assertEquals("reserva", como("reserva"))
        assertEquals("contas_riscadas", como("riscadas"))
        assertEquals("mei", como("mei"))
        assertEquals("norte", ask(TopicBlock(Topic.NORTE)).tool)
        assertEquals("corte_de_opcionais", ask(TopicBlock(Topic.CORTAR)).tool)
        assertEquals("e_se", ask(TopicBlock(Topic.E_SE), SubBlock("guardar", "g"), AmountBlock(AmountField.VALUE, 500.0)).tool)
    }

    @Test fun guiaTrazOQueImporta() {
        val r = ask(TopicBlock(Topic.GUIA))
        assertEquals("guia", r.tool)
        val det = ask(TopicBlock(Topic.GUIA), detailed = true)
        assertTrue(det.text.contains("DAS"))                                // dia 17: lembra do DAS que vence dia 20
        assertTrue(det.text.length > r.text.length)
    }

    // ---------- o Dot executa (sempre com confirmação) ----------
    @Test fun pagarContaSoDepoisDoSim() {
        val r = ask(TopicBlock(Topic.REGISTRAR), SubBlock("pagou", "x"), CostBlock(luz().id, "Luz"))
        val p = r.pending as DotAction.ConfirmForecast
        assertEquals("Luz", p.name); assertEquals(134.0, p.amount, 0.001)
        assertNull(r.run)
        assertEquals(p, DotBrain.confirm(p).run)
        assertNull(DotBrain.decline().run)
    }

    @Test fun cadastrarCustoComNomeEmTexto() {
        var b = listOf<Block>(TopicBlock(Topic.CADASTRAR), SubBlock("custo", "um custo"))
        assertTrue(nextStep(b.toDraft(), data, today).input!!.isText)                          // pede o nome em texto
        b = b + NameBlock("Spotify")
        assertEquals(AmountField.VALUE, nextStep(b.toDraft(), data, today).input?.field)
        b = b + AmountBlock(AmountField.VALUE, 21.9)
        val groups = nextStep(b.toDraft(), data, today).options.map { it.label }
        assertEquals(listOf("Moradia", "Trabalho", "Supérfluos"), groups)                        // nomes dos grupos são seus
        b = b + GroupBlock(FixedCost.GRP_OPCIONAL, "Supérfluos")
        assertEquals("É cobrado no cartão?", nextStep(b.toDraft(), data, today).prompt)
        b = b + CardBlock(true)
        assertTrue(nextStep(b.toDraft(), data, today).complete)
        val p = DotBrain.answer(b.toDraft(), data, false, today).pending as DotAction.AddFixed
        assertEquals("Spotify", p.name); assertEquals(21.9, p.amount, 0.001); assertTrue(p.onCard)
    }

    @Test fun mudarValorExcluirETirarDoMes() {
        val aj = DotBrain.answer(draft(TopicBlock(Topic.MUDAR), SubBlock("ajustar", "a"), CostBlock(luz().id, "Luz"), AmountBlock(AmountField.VALUE, 150.0)), data, false, today)
        assertEquals(DotAction.SetFixedAmount(luz().id, "Luz", 150.0), aj.pending)
        val ex = ask(TopicBlock(Topic.MUDAR), SubBlock("excluir", "e"), CostBlock(disney().id, "Disney+"))
        assertEquals(DotAction.DeleteFixed(disney().id, "Disney+"), ex.pending)
        val mes = ask(TopicBlock(Topic.MUDAR), SubBlock("tirarmes", "t"), CostBlock(luz().id, "Luz"))
        assertEquals("2026-11", (mes.pending as DotAction.RemoveFromMonth).month)
    }

    @Test fun apagarGastoEReceberEntradaListamOQueExiste() {
        val gastos = nextStep(draft(TopicBlock(Topic.MUDAR), SubBlock("apagargasto", "a")), data, today).options
        assertEquals(1, gastos.size)
        val receb = nextStep(draft(TopicBlock(Topic.MUDAR), SubBlock("receber", "r")), data, today).options
        assertEquals(1, receb.size)
    }

    @Test fun registrarFaturamentoSomaNoMes() {
        val r = ask(TopicBlock(Topic.REGISTRAR), SubBlock("recebi", "r"), AmountBlock(AmountField.VALUE, 5000.0))
        val p = r.pending as DotAction.AddRevenue
        assertEquals("2026-11", p.month); assertEquals(5000.0, p.amount, 0.001)
    }

    @Test fun reflexaoVirouFerramentaDoDot() {
        var b = listOf<Block>(TopicBlock(Topic.REFLEXAO), SubBlock("nova", "n"))
        assertTrue(nextStep(b.toDraft(), data, today).input!!.isText)
        b = b + NameBlock("Fone") + AmountBlock(AmountField.VALUE, 800.0)
        assertEquals(EMOTIONS.size, nextStep(b.toDraft(), data, today).options.size)
        b = b + EmotionBlock("Tédio") + CardBlock(false)
        assertTrue(nextStep(b.toDraft(), data, today).complete)
        val p = DotBrain.answer(b.toDraft(), data, false, today).pending as DotAction.StartReflection
        assertEquals("Fone", p.name); assertEquals(800.0, p.price, 0.001); assertTrue(p.cash)
        assertTrue(hoursLeft(0L, 24, 1_000L) > 0 && hoursLeft(0L, 24, 90_000_000L) == 0)
    }

    @Test fun desfazerUmBlocoVoltaUmPasso() {
        val blocks = listOf(TopicBlock(Topic.COMPRA), AmountBlock(AmountField.VALUE, 100.0), PayBlock(3))
        assertEquals(3, blocks.toDraft().installments)
        assertEquals(1, blocks.dropLast(1).toDraft().installments)
    }

    @Test fun categoriasEGruposSobreviveAoBackup() {
        val again = Backup.fromJson(Backup.toJson(data))
        assertEquals(data.settings.categories, again.settings.categories)
        assertEquals(data.settings.categoryList, again.settings.categoryList)
        assertEquals(data.settings.groupNames, again.settings.groupNames)
        assertNotNull(again.settings.meiLimit)
    }

    @Test fun comVariasContasODotPerguntaDeQualDescontar() {
        val two = data.copy(accounts = data.accounts.mapIndexed { i, a -> a.copy(id = i + 1L) } + com.persondot.data.model.Account(id = 9, name = "Nubank", balance = 500.0))
        val b = listOf(TopicBlock(Topic.REGISTRAR), SubBlock("gastou", "g"), AmountBlock(AmountField.VALUE, 40.0), CategoryBlock("Outros"))
        val step = nextStep(b.toDraft(), two, today)
        assertEquals("Em qual conta?", step.prompt)
        assertTrue(step.options.any { it.label == "Nubank" } && step.options.any { it.label.startsWith("nenhuma") })
        val done = b + AccountBlock(9, "Nubank")
        assertTrue(nextStep(done.toDraft(), two, today).complete)
        val p = DotBrain.answer(done.toDraft(), two, false, today).pending as DotAction.AddExpense
        assertEquals(9L, p.accountId)
    }

    @Test fun novasFerramentasCadastramContaEMudamOPlano() {
        var b = listOf<Block>(TopicBlock(Topic.CADASTRAR), SubBlock("conta", "c"), NameBlock("Nubank"), AmountBlock(AmountField.VALUE, 300.0))
        assertEquals("Que tipo de conta?", nextStep(b.toDraft(), data, today).prompt)
        b = b + KindBlock("RESERVA", "r")
        val a = DotBrain.answer(b.toDraft(), data, false, today).pending as DotAction.AddAccount
        assertEquals("Nubank", a.name); assertEquals(300.0, a.balance, 0.001)
        val plano = DotBrain.answer(draft(TopicBlock(Topic.MUDAR), SubBlock("p_income", "i"), AmountBlock(AmountField.VALUE, 6000.0)), data, false, today).pending as DotAction.ApplyChanges
        assertEquals(6000.0, data.settings.applyChanges(plano.changes).income, 0.001)
    }

    @Test fun saudeSemDadosNaoInventaNota() {
        val h = FinanceData().health(today)
        assertTrue(!h.hasData); assertEquals(0, h.score)
    }

    @Test fun mascaraDeValorEhNatural() {
        assertEquals("5.000", com.persondot.ui.components.maskMoney("5000"))
        assertEquals("1.234.567", com.persondot.ui.components.maskMoney("1234567"))
        assertEquals("5.000,5", com.persondot.ui.components.maskMoney("5.0005,5".replace("5,5", ",5")))
        assertEquals("0,", com.persondot.ui.components.maskMoney("0,"))
        assertEquals("", com.persondot.ui.components.maskMoney("abc"))
        assertEquals(5000.0, com.persondot.ui.components.parseMoney("5.000"), 0.001)
        assertEquals(5000.5, com.persondot.ui.components.parseMoney("5.000,50"), 0.001)
        assertEquals(2.5, com.persondot.ui.components.parseMoney("2.5"), 0.001)
    }
}
