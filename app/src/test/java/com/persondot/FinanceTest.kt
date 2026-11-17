package com.persondot

import com.persondot.data.Backup
import com.persondot.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Confere os números que combinamos na conversa, usando o arquivo de dados reais (se existir). */
class FinanceTest {

    private val today = LocalDate.of(2026, 11, 17)

    private fun loadSeed(): FinanceData? {
        val path = System.getenv("PERSONDOT_SEED") ?: return null
        val f = File(path)
        return if (f.exists()) Backup.fromJson(f.readText()) else null
    }

    /** Dados mínimos embutidos, para o teste rodar em qualquer máquina. */
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
     "planned":[{"desc":"Procedimento","amount":1600,"month":"2026-11"},{"desc":"Produtos","amount":300,"month":"2026-11"}],
     "aportes":{"2026-12":0,"2027-01":500,"2027-02":800,"2027-03":1200}}
    """.trimIndent()

    private val data = Backup.fromJson(json)

    @Test fun livreDeVerdade() {
        val c = data.core()
        assertEquals(13291.89, c.inHand, 0.01)
        assertEquals(5861.25, c.toReceive, 0.01)
        assertEquals(11038.64, c.owed, 0.01)
        assertEquals(1900.0, c.planned, 0.01)
        assertEquals(6214.50, c.free, 0.01)
        assertEquals(10981.56, c.reserveTarget, 0.01)
    }

    @Test fun dezembroNoVermelho() {
        val m = data.monthPlan("2026-12")
        assertTrue(m.subsIncluded)
        assertEquals(-167.72, m.sobra, 0.01)   // renda 5000 - fixos sem cartão - fatura de dezembro
    }

    @Test fun contasRiscadasEmOrdem() {
        val r = data.riscadas(today)
        assertEquals(listOf("Internet", "Luz", "Mercado", "Predio"), r.map { it.cost.name })
        assertEquals(306112.0, r.last().cum, 0.5)
    }

    @Test fun carroAVistaNaoCabe() {
        val res = data.evalPurchase(PurchaseInput(desc = "Gol", valor = 5000.0, recorrente = 400.0), today)
        assertEquals(Level.BAD, res.level)
        assertEquals(1214.50, res.freeAfter, 0.01)
    }

    @Test fun backupIdaEVolta() {
        val again = Backup.fromJson(Backup.toJson(data))
        assertEquals(data.core().free, again.core().free, 0.001)
        assertEquals(data.fixed.size, again.fixed.size)
    }

    @Test fun seedPessoalSeExistir() {
        val d = loadSeed() ?: return
        assertEquals(6214.50, d.core().free, 0.01)
    }
}
