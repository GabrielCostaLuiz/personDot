# PersonDot

Assistente pessoal, começando pelas finanças. App Android nativo (Kotlin + Jetpack Compose + Room), feito sob medida
para quem é PJ/MEI e quer enxergar a saúde financeira inteira, decidir compras com os próprios números e ir fazendo
as contas da casa serem pagas pelo rendimento.

O visual vem do [mindful-finance](https://github.com/GabrielCostaLuiz/mindful-finance) (Bento Grid, modo escuro e claro).

## O que ele faz

| Aba | Para quê |
|---|---|
| **Painel** | "Livre de verdade" (o que é seu depois das parcelas e planejados), nota de saúde financeira com próximo passo, reserva de emergência, contas, entradas a receber e gastos planejados |
| **Gastos** | Tudo que sai por mês: casa (essencial), trabalho, opcionais, parcelas, planejados e gastos variáveis por categoria. Interruptor para simular o corte de opcionais |
| **Decidir** | **Vale a pena?** simula uma compra à vista ou parcelada (impacto no livre, nos meses seguintes e na meta) e **Reflexão 24h** com resfriamento anti-impulso |
| **Futuro** | Dívidas e parcelas (pagar a próxima, editar, adicionar), calendário de alívio e fluxo mês a mês com aporte editável |
| **Metas** | **Contas riscadas** (regra dos 200: valor da conta × 200), metas gerais e **carro à vista** com custo real de propriedade (TCO) |
| **Ajustes** | Parâmetros do plano, backup (exportar/importar), atualização do app e tema |

Tudo é editável. Os dados ficam **só no aparelho** (Room/SQLite). Nada vai para servidor.

## Método

1. Compromissos e reserva primeiro: o "livre de verdade" desconta parcelas e gastos planejados.
2. Reserva de emergência = meses de (casa + trabalho), em liquidez diária (sem oscilar).
3. Só depois, aporte no balde IPCA+ (juros reais acima da inflação).
4. Regra dos 200: o rendimento real de 0,5% ao mês de um valor paga uma conta sem tocar no principal. Cada conta
   mensal exige 200× o valor guardado; as contas são "riscadas" da menor para a maior.

As análises usam as regras do seu próprio plano; não são recomendação financeira personalizada.

## Instalar e atualizar

**Instalar:** no celular Android, abra o link e instale o APK
(permita "instalar apps desta fonte" quando o Android pedir):

<https://github.com/GabrielCostaLuiz/personDot/releases/latest/download/PersonDot.apk>

**Atualizar:** em **Ajustes → Atualização do app → Procurar atualização**. O app consulta a última release,
baixa o APK e abre o instalador. Seus dados são mantidos.

**Seus dados:** em **Ajustes → Importar**, escolha o seu arquivo `.json` de backup. O app **não** vem com dados
pessoais; o arquivo fica só com você (e no `.gitignore`).

## Publicar uma versão nova (desenvolvimento)

```bash
git tag v0.2.0
git push origin v0.2.0
```

O GitHub Actions roda os testes, gera o APK assinado e publica a release. A versão do app vem da tag
(`v1.2.3` → `versionCode` 10203).

### Chave de assinatura

O Android só atualiza um app se a assinatura for a mesma. A chave **não está no repositório**:

- Local: `~/.persondot/release.jks` + `~/.persondot/keystore.properties`
- GitHub: secrets `KEYSTORE_B64`, `STORE_PASSWORD`, `KEY_PASSWORD`

**Guarde um backup de `~/.persondot/`.** Se perder a chave, só dá para atualizar desinstalando o app (e perdendo os
dados que não foram exportados).

## Desenvolvimento

```bash
./gradlew testDebugUnitTest      # testes da lógica financeira
./gradlew assembleDebug          # APK de debug
./gradlew assembleRelease        # APK assinado (precisa da chave acima)
```

Requisitos: JDK 17+ (o do Android Studio serve) e Android SDK 36.
A lógica fica em `domain/Finance.kt` (Kotlin puro, testável); telas em `ui/screens`; dados em `data/`.
