# PersonDot

Assistente pessoal, começando pelas finanças. App Android nativo (Kotlin + Jetpack Compose + Room), feito sob medida
para quem é PJ/MEI e quer enxergar a saúde financeira inteira, decidir compras com os próprios números e ir fazendo
as contas da casa serem pagas pelo rendimento.

O visual vem do [mindful-finance](https://github.com/GabrielCostaLuiz/mindful-finance) (Bento Grid, modo escuro e claro).

## O que ele faz

| Aba | Para quê |
|---|---|
| **Painel** | Visão geral: o Dot com o seu **norte** (o que fazer agora), "livre de verdade", anéis de saúde, reserva e contas riscadas, o que falta confirmar no mês, contas e entradas a receber |
| **Gastos** | **Mês**: todo custo é uma **previsão** até você **confirmar** o pagamento (dá para ajustar o valor do mês, tirar só daquele mês ou cancelar daqui pra frente); simulação de corte de opcionais mostrando o que sairia; gastos do dia a dia. **Parcelas**: dívidas e faturas. **Fluxo**: calendário de alívio e mês a mês |
| **Dot** | Conversa em tela cheia (sem cabeçalho nem barra inferior; sai pelo X). Você **monta a pergunta em blocos**: escolhe o tema, depois as variações (valor, à vista ou em Nx, com ou sem juros, mês...), e o Dot responde chamando a ferramenta certa. Respostas **diretas** (a conclusão) ou **detalhadas** (os números). Ações que mudam dados pedem um "sim" |
| **Decidir** | **Vale a pena?** simula uma compra à vista ou parcelada e **Reflexão 24h** com resfriamento anti-impulso |
| **Futuro** | Evoluir: linha do tempo das conquistas, **"E se...?"** (guardar mais, cortar opcionais, aumento de renda), contas riscadas (regra dos 200) e metas |
| **Ajustes** | Um hub com o Dot no topo e os assuntos embaixo: **Meu plano**, **Contas e saldos**, **Custos previstos**, **Categorias e grupos**, **Aportes por mês**, **Dot e aparência**, **Dados e backup** e **Atualização do app**. Nada é fixo: renda, categorias de gasto, nomes dos grupos, aportes de cada mês, o visual e a cor do Dot, tudo se edita aqui |

O Dot aparece em todas as telas numa faixa fina com dicas e reações (dá para desligar em Ajustes). O chat **não usa IA nem internet**: as perguntas em blocos chamam as mesmas funções do app, então tudo roda no seu aparelho.
Para criar um Dot novo, adicione uma linha em `ui/dot/DotAvatar.kt`.

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

> **Se o Android bloquear a instalação** ("App não instalado", ou o Play Protect recusa): no emulador Android 17 com
> Play Protect, o instalador recusou o APK (`VerifyApps: REJECT`), enquanto `adb install` funcionou. O Google passou a
> exigir verificação de desenvolvedor para apps de fora da Play Store em alguns países, incluindo o Brasil. Caminhos:
> registrar o app (nome do pacote `com.persondot.app` + chave de assinatura) no Android Developer Console, na conta
> gratuita para hobby/uso pessoal; instalar por `adb install PersonDot.apk`; ou publicar na Play Store (teste interno).

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
