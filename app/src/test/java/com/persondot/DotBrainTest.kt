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

/** As perguntas do Dot são montadas em blocos; aqui conferimos cada caminho e o tamanho das respostas. */
class DotBrainTest {
    private val today = LocalDate.of(2026, 11, 17)

    private val data = Backup.fromJson(
        """
        {"settings":{"income":5000,"multiplier":200,"reserveMonths":6,"realRate":0.06,"aporteLongo":1500,
                     "categories":"Mercado extra|Pet|Outros","groupNames":"Moradia|Trabalho|Supérfluos"},
         "accounts":[{"name":"MP","balance":10025.81,"kind":"reserva"},{"name":"IPCA","balance":0,"kind":"ipca"}],
         "receivables":[],
         "debts":[{"name":"Nubank","installments":[{"month":"2026-12","amount":3241.21},{"month":"2027-01","amount":1717.35}]}],
         "fixed":[{"name":"Luz","amount":134,"group":"casa"},{"name":"Mercado","amount":600,"group":"casa"},
                  {"name":"DAS MEI","amount":86.05,"group":"trabalho"},
                  {"name":"Disney+","amount":29.9,"group":"opcional","card":true},{"name":"Academia","amount":159.9,"group":"opcional"}],
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
        assertEquals(AmountField.VALUE, nextStep(b.toDraft(), data, today).input?.field)          // pede o valor

        b = b + AmountBlock(AmountField.VALUE, 2000.0)
        val pay = nextStep(b.toDraft(), data, today)
        assertEquals("Como vai pagar?", pay.prompt)
        assertTrue(pay.options.any { it.label == "à vista" } && pay.options.any { it.label == "em 10x" })
        assertEquals(AmountField.INSTALLMENTS, pay.input?.field)                                    // e aceita outro número

        b = b + PayBlock(10)
        assertEquals("Tem juros?", nextStep(b.toDraft(), data, today).prompt)                       // parcelado pergunta pelos juros

        b = b + InterestBlock(0.0)
        val done = nextStep(b.toDraft(), data, today)
        assertTrue(done.complete)
        assertTrue(done.extras.any { it.label == "É essencial" } && done.extras.any { it.input?.field == AmountField.MONTHLY_COST })
        assertEquals("Posso comprar algo de R$ 2.000 em 10x sem juros?", b.toDraft().sentence())
    }

    @Test fun compraAVistaNaoPerguntaJuros() {
        val d = draft(TopicBlock(Topic.COMPRA), AmountBlock(AmountField.VALUE, 800.0), PayBlock(1))
        assertTrue(nextStep(d, data, today).complete)
    }

    @Test fun variantesVaoSeAcumulando() {
        val d = draft(TopicBlock(Topic.COMPRA), AmountBlock(AmountField.VALUE, 5000.0), PayBlock(1), EssentialBlock, AmountBlock(AmountField.MONTHLY_COST, 400.0))
        assertTrue(d.essential && d.monthlyCost == 400.0)
        assertEquals("Posso comprar algo de R$ 5.000 à vista, sendo essencial, e vem com custo de R$ 400 por mês?", d.sentence())
        assertTrue(nextStep(d, data, today).extras.isEmpty())                                       // nada mais a acrescentar
    }

    @Test fun comoEstaTemSubtemas() {
        assertEquals(4, nextStep(draft(TopicBlock(Topic.COMO_ESTA)), data, today).options.size)
        val mes = nextStep(draft(TopicBlock(Topic.COMO_ESTA), SubBlock("mes", "o mês")), data, today)
        assertEquals("De qual mês?", mes.prompt)
        assertTrue(mes.options.size >= 3)
        assertTrue(nextStep(draft(TopicBlock(Topic.COMO_ESTA), SubBlock("reserva", "a reserva")), data, today).complete)
    }

    @Test fun categoriasDoGastoVemDosAjustes() {
        val s = nextStep(draft(TopicBlock(Topic.REGISTRAR), SubBlock("gastou", "um gasto"), AmountBlock(AmountField.VALUE, 45.0)), data, today)
        assertEquals(listOf("Mercado extra", "Pet", "Outros"), s.options.map { it.label })         // a lista é sua, não do código
    }

    @Test fun desfazerUmBlocoVoltaUmPasso() {
        val blocks = listOf(TopicBlock(Topic.COMPRA), AmountBlock(AmountField.VALUE, 100.0), PayBlock(3))
        assertEquals(3, blocks.toDraft().installments)
        assertEquals(1, blocks.dropLast(1).toDraft().installments)
    }

    // ---------- respostas ----------
    @Test fun respostaDiretaECurtaEDetalhadaECompleta() {
        val blocks = arrayOf<Block>(TopicBlock(Topic.COMPRA), AmountBlock(AmountField.VALUE, 2000.0), PayBlock(10), InterestBlock(0.0))
        val direto = ask(*blocks)
        val detalhado = ask(*blocks, detailed = true)
        assertEquals("simular_compra", direto.tool)
        assertNotNull(direto.level)
        assertTrue(direto.hasMore && !detalhado.hasMore)
        assertTrue(detalhado.text.length > direto.text.length)
        assertTrue(direto.text.contains("10x"))
    }

    @Test fun cadaTemaChamaSuaFerramenta() {
        assertEquals("saude_financeira", ask(TopicBlock(Topic.SAUDE)).tool)
        assertEquals("norte", ask(TopicBlock(Topic.NORTE)).tool)
        assertEquals("livre_de_verdade", ask(TopicBlock(Topic.LIVRE)).tool)
        assertEquals("corte_de_opcionais", ask(TopicBlock(Topic.CORTAR)).tool)
        assertEquals("resumo_do_mes", ask(TopicBlock(Topic.COMO_ESTA), SubBlock("mes", "o mês"), MonthBlock("2026-12", "de dez/26")).tool)
        assertEquals("dividas", ask(TopicBlock(Topic.COMO_ESTA), SubBlock("parcelas", "as parcelas")).tool)
        assertEquals("reserva", ask(TopicBlock(Topic.COMO_ESTA), SubBlock("reserva", "a reserva")).tool)
        assertEquals("contas_riscadas", ask(TopicBlock(Topic.COMO_ESTA), SubBlock("riscadas", "as contas riscadas")).tool)
        assertEquals("e_se", ask(TopicBlock(Topic.E_SE), SubBlock("guardar", "guardar a mais"), AmountBlock(AmountField.VALUE, 500.0)).tool)
        assertEquals("e_se", ask(TopicBlock(Topic.E_SE), SubBlock("cortar", "cortar os opcionais")).tool)
    }

    @Test fun cortarMostraOQueSaiNoDetalhe() {
        val direto = ask(TopicBlock(Topic.CORTAR))
        val det = ask(TopicBlock(Topic.CORTAR), detailed = true)
        assertTrue(direto.text.contains("Academia"))                       // o maior item aparece na resposta curta
        assertTrue(det.text.contains("Disney+") && det.text.contains("Academia"))
        assertTrue(det.text.length > direto.text.length)
    }

    // ---------- ações com confirmação ----------
    @Test fun pagarContaSoDepoisDoSim() {
        val r = ask(TopicBlock(Topic.REGISTRAR), SubBlock("pagou", "que paguei"), CostBlock(luz().id, "Luz"))
        val p = r.pending as DotAction.ConfirmForecast
        assertEquals("Luz", p.name); assertEquals(134.0, p.amount, 0.001); assertEquals("2026-11", p.month)
        assertNull(r.run)
        assertEquals(p, DotBrain.confirm(p).run)
        assertNull(DotBrain.decline().run)
    }

    @Test fun cancelarCustoDaquiPraFrente() {
        val r = ask(TopicBlock(Topic.REGISTRAR), SubBlock("cancelou", "que cancelei"), CostBlock(disney().id, "Disney+"))
        val p = r.pending as DotAction.CancelFrom
        assertEquals("2026-12", p.month)
        assertTrue(r.text.contains("29,90"))
    }

    @Test fun lancarGastoComCategoriaEscolhida() {
        val r = ask(TopicBlock(Topic.REGISTRAR), SubBlock("gastou", "um gasto"), AmountBlock(AmountField.VALUE, 45.0), CategoryBlock("Pet"))
        val p = r.pending as DotAction.AddExpense
        assertEquals(45.0, p.amount, 0.001); assertEquals("Pet", p.cat)
    }

    @Test fun contasParaPagarSaoAsPrevistasDoMes() {
        val s = nextStep(draft(TopicBlock(Topic.REGISTRAR), SubBlock("pagou", "que paguei")), data, today)
        assertTrue(s.options.any { it.label == "Luz" } && s.options.any { it.label == "Academia" })
    }

    // ---------- nada fixo ----------
    @Test fun nomesDosGruposEDasCategoriasVemDosAjustes() {
        assertEquals(listOf("Mercado extra", "Pet", "Outros"), data.settings.categoryList)
        assertEquals("Moradia", data.settings.groupLabel(FixedCost.GRP_CASA))
        assertEquals("Supérfluos", data.settings.groupLabel(FixedCost.GRP_OPCIONAL))
    }

    @Test fun categoriasEGruposSobreviveAoBackup() {
        val again = Backup.fromJson(Backup.toJson(data))
        assertEquals(data.settings.categories, again.settings.categories)
        assertEquals(data.settings.groupNames, again.settings.groupNames)
    }
}
