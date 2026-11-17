package com.persondot

import com.persondot.data.Backup
import com.persondot.data.model.FixedCost
import com.persondot.data.model.MonthEntry
import com.persondot.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Confere os números que combinamos na conversa, com dados embutidos (e o arquivo pessoal, se existir). */
class FinanceTest {

    private val today = LocalDate.of(2026, 11, 17)

    private fun loadSeed(): FinanceData? {
        val path = System.getenv("PERSONDOT_SEED") ?: return null
        val f = File(path)
        return if (f.exists()) Backup.fromJson(f.readText()) else null
    }

    private val json = """
    {"settings":{"income":5000,"multiplier":200,"reserveMonths":6,"realRate":0.06,"aporteLongo":1500,"ipcaStart":"2027-06"},
     "accounts":[{"name":"MP","balance":10025.81,"kind":"reserva"},{"name":"Nu","balance":3266.08,"kind":"reserva"},{"name":"IPCA","balance":0,"kind":"ipca"}],
     "receivables":[{"desc":"a","amount":4111.25,"received":false},{"desc":"b","amount":1750,"received":false}],
     "debts":[{"name":"Nubank","installments":[
        {"month":"2026-12","amount":3241.21,"paid":false,"includesSubs":true},{"month":"2027-01","amount":1717.35,"paid":false},
        {"month":"2027-02","amount":1458.91,"paid":false},{"month":"2027-03","amount":682.82,"paid":false},{"month":"2027-04","amount":682.82,"paid":false},
        {"month":"2027-05","amount":682.82,"paid":false},{"month":"2027-06","amount":682.82,"paid":false},{"month":"2027-07","amount":682.82,"paid":false},
        {"month":"2027-08","amount":207.39,"paid":false}]},
        {"name":"Renner","installments":[{"month":"2027-01","amount":123.94},{"month":"2027-02","amount":123.94},{"month":"2027-03","amount":123.94},{"month":"2027-04","amount":123.94},{"month":"2027-05","amount":123.94}]},
        {"name":"Esposa","installments":[{"month":null,"amount":379.98}]}],
     "fixed":[{"name":"Internet","amount":100,"group":"casa"},{"name":"Luz","amount":134,"group":"casa"},{"name":"Mercado","amount":600,"group":"casa"},{"name":"Predio","amount":696.56,"group":"casa"},
        {"name":"DAS","amount":86.05,"group":"trabalho"},{"name":"GPT","amount":99.25,"group":"trabalho","card":true},{"name":"Claude","amount":114.4,"group":"trabalho","card":true},
        {"name":"Udemy","amount":45.96,"group":"opcional","card":true},{"name":"Academia","amount":159.9,"group":"opcional"},{"name":"Corte","amount":150,"group":"opcional"},
        {"name":"Disney","amount":29.9,"group":"opcional","card":true},{"name":"Lightroom","amount":9.9,"group":"opcional","card":true},{"name":"GoogleOne","amount":24.99,"group":"opcional","card":true},
        {"name":"YouTube","amount":26.9,"group":"opcional","card":true},{"name":"Livelo","amount":49.9,"group":"opcional","card":true}],
     "planned":[{"desc":"Procedimento","amount":1600,"month":"2026-11"}],
     "aportes":{"2026-12":0,"2027-01":500,"2027-02":800,"2027-03":1200}}
    """.trimIndent()

    // o banco dá ids aos custos; nos testes usamos a posição
    private val data = Backup.fromJson(json).let { d -> d.copy(fixed = d.fixed.mapIndexed { i, f -> f.copy(id = i.toLong()) }) }
    private fun fixedId(name: String) = data.fixed.indexOfFirst { it.name == name }.toLong()

    @Test fun livreDeVerdade() {
        val c = data.core()
        assertEquals(13291.89, c.inHand, 0.01)
        assertEquals(5861.25, c.toReceive, 0.01)
        assertEquals(11038.64, c.owed, 0.01)
        assertEquals(8114.50, c.free, 0.01)            // o "planejado" saiu do app: o simulador cobre esse caso
        assertEquals(10981.56, c.reserveTarget, 0.01)
    }

    @Test fun dezembroNoVermelho() {
        val m = data.monthPlan("2026-12")
        assertTrue(m.subsIncluded)
        assertEquals(-167.72, m.sobra, 0.01)           // renda 5000 - fixos sem cartão - fatura de dezembro
    }

    @Test fun previsaoDeUmMesSemFatura() {
        val m = data.monthPlan("2027-03")
        assertEquals(1530.56, m.casa, 0.01); assertEquals(299.70, m.trabalho, 0.01); assertEquals(497.45, m.opcional, 0.01)
        assertEquals(682.82 + 123.94, m.parcelas, 0.01)
        assertEquals(0.0, m.pago, 0.001)               // tudo ainda é previsão
        assertEquals(m.previsto, m.aPagar, 0.001)
    }

    @Test fun confirmarAjustarRemoverECancelar() {
        val base = data.monthPlan("2027-03")
        val comEntradas = data.copy(
            monthEntries = listOf(
                MonthEntry(fixedId = fixedId("Luz"), month = "2027-03", status = MonthEntry.PAGO, amount = 140.0),          // pago, valor diferente
                MonthEntry(fixedId = fixedId("Disney"), month = "2027-03", status = MonthEntry.REMOVIDO),                  // cancelou neste mês
                MonthEntry(fixedId = fixedId("Mercado"), month = "2027-03", status = MonthEntry.PREVISTO, amount = 700.0) // ajustou o preço
            )
        )
        val m = comEntradas.monthPlan("2027-03")
        assertEquals(base.casa - 134.0 - 600.0 + 140.0 + 700.0, m.casa, 0.01)
        assertEquals(base.opcional - 29.9, m.opcional, 0.01)
        assertEquals(140.0, m.pagoFixos, 0.01)
        assertEquals(m.previsto - 140.0, m.aPagar, 0.01)

        // cancelar a Udemy a partir de 2027-03 não mexe em fevereiro
        val udemy = data.fixed.first { it.name == "Udemy" }
        val cancelada = data.copy(fixed = data.fixed.map { if (it.id == udemy.id) it.copy(endsBefore = "2027-03") else it })
        assertEquals(base.opcional - 45.96, cancelada.monthPlan("2027-03").opcional, 0.01)
        assertEquals(data.monthPlan("2027-02").opcional, cancelada.monthPlan("2027-02").opcional, 0.01)
    }

    @Test fun corteMostraOQueCortou() {
        val cut = data.cutOptional("2027-03")
        assertEquals(497.45, cut.monthly, 0.01)
        assertEquals(497.45 * 12, cut.yearly, 0.05)
        assertEquals("Academia", cut.items.first().cost.name)    // maior primeiro
        assertTrue(cut.items.size == 8)
    }

    @Test fun contasRiscadasEmOrdem() {
        val r = data.riscadas(today)
        assertEquals(listOf("Internet", "Luz", "Mercado", "Predio"), r.map { it.cost.name })
        assertEquals(306112.0, r.last().cum, 0.5)
    }

    @Test fun evolucaoReservaEWhatIf() {
        assertEquals("2027-04", data.reserveEta(0.0, today))
        assertEquals(1, data.whatIf(0.0, true, 0.0, today).reserveSooner)      // cortar opcionais adianta 1 mês
        assertTrue(data.whatIf(1000.0, false, 0.0, today).reserveSooner >= 2)
        assertTrue(data.milestones(today).isNotEmpty())
    }

    @Test fun norteComecaPelosPagamentosDoMes() {
        val steps = data.norte(today)
        assertTrue(steps.isNotEmpty() && steps.size <= 4)
        assertTrue(steps.first().title.contains("Confirme os pagamentos"))
        assertEquals(Dest.GASTOS, steps.first().dest)
    }

    @Test fun carroAVistaNaoCabe() {
        val res = data.evalPurchase(PurchaseInput(desc = "Gol", valor = 5000.0, recorrente = 400.0), today)
        assertEquals(Level.BAD, res.level)
        assertEquals(3114.50, res.freeAfter, 0.01)
    }

    @Test fun backupIdaEVoltaComAjustesPorMes() {
        val comEntrada = data.copy(
            monthEntries = listOf(MonthEntry(fixedId = fixedId("Luz"), month = "2027-03", status = MonthEntry.PAGO, amount = 140.0)),
            fixed = data.fixed.map { if (it.name == "Udemy") it.copy(endsBefore = "2027-05") else it }
        )
        // o repositório do app dá ids novos; aqui simulamos ids = posição, como no arquivo
        val withIds = comEntrada.copy(fixed = comEntrada.fixed.mapIndexed { i, f -> f.copy(id = i.toLong()) })
        val withIdsEntry = withIds.copy(monthEntries = withIds.monthEntries.map { it.copy(fixedId = withIds.fixed.first { f -> f.name == "Luz" }.id) })
        val again = Backup.fromJson(Backup.toJson(withIdsEntry))
        assertEquals(1, again.monthEntries.size)
        assertEquals(140.0, again.monthEntries.first().amount!!, 0.001)
        assertEquals("Luz", again.fixed[again.monthEntries.first().fixedId.toInt()].name)
        assertEquals("2027-05", again.fixed.first { it.name == "Udemy" }.endsBefore)
        assertEquals(data.core().free, again.core().free, 0.001)
    }

    @Test fun seedPessoalSeExistir() {
        val d = loadSeed() ?: return
        assertEquals(8114.50, d.core().free, 0.01)
    }
}
