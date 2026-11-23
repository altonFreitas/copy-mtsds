# Dependência do `gestao-acesso`: bate certo com `garantia-acesso` e `historico`?

> ⚠️ **v2.1:** análise feita sobre o serviço `tmrg` (agora `gestao-acesso`). As routing keys mudaram: `tmrg.*` → `prazo.*`; `incumprimento.*` → `caso.*`. O serviço ainda não publica eventos no repositório do grupo (21/11/2026).

> **Só leitura** do repositório do grupo (`main` com o PR #9 do Avelino, integrado a 19/11/2026 às 15:26). Nada foi alterado lá.
> **O que o código do `gestao-acesso` faz:** [`tmrg-explicado.md`](tmrg-explicado.md). **O contrato de eventos:** [`../minha-parte.md`](../minha-parte.md), secção 5. **O que fazer quando ele estiver pronto:** [`../plano-de-trabalho.md`](../plano-de-trabalho.md), secção 7. Este ficheiro tem só **a análise e os pontos para o PR**.

## Índice

1. [Resumo: bate certo?](#1-resumo-bate-certo)
2. [As regras de negócio batem certo?](#2-as-regras-de-negócio-batem-certo)
3. [Pontos para a revisão do PR](#3-pontos-para-a-revisão-do-pr)
4. [Resposta do Avelino e o que falta confirmar](#4-resposta-do-avelino-e-o-que-falta-confirmar)
5. [A mensagem para o Avelino](#5-a-mensagem-para-o-avelino)

---

## 1. Resumo: bate certo?

**A lógica do `gestao-acesso` bate certo com o que o `garantia-acesso` e o `historico` esperam. Falta a ligação: o `gestao-acesso` calcula os alarmes mas ainda não os publica.**

| Ponto | Estado |
|---|---|
| Fórmulas dos prazos, quando passa a "em risco" e a "incumprido" | ✅ Bate certo (secção 2) |
| Alarme de risco **uma só vez** por episódio | ✅ Casa com "um caso aberto por episódio" |
| Exchange `acesso.eventos` (topic, durável) e envelope `MensagemBase` | ✅ Igual; o meu `EventoMessage` estende o `MensagemBase` |
| Rotas e permissões da Gateway para os meus 7 endpoints | ✅ Já existem |
| **Publicar `prazo.em-risco` e `prazo.ultrapassado`** | ❌ Não existe (só devolve listas de ids e escreve no log) |
| **Publicar `relogio.avancado`** | ❌ Não existe (nem no `PUT` nem no `DELETE /demo/relogio`) |
| `prazo.ultrapassado-previsto` (R5) | ❌ A função existe, ninguém a usa |
| Criar `EpisodioPrazo` (consumir `pedido.triado`/`episodio.inscrito`) | ❌ A tabela fica vazia |
| Nome do identificador | ✅ `episodioId` com o valor do `pedidoId`; o meu código aceita os dois |
| Formato de `especialidade` (`"Cardiologia"` vs `"CARDIOLOGIA"`) | ⚠️ Por combinar |

**Consequência prática:** hoje, **nenhum alarme chega** ao `garantia-acesso`. Funciona com eventos simulados; com o `gestao-acesso` real só depois de ele publicar. Isto **não bloqueia** o meu desenvolvimento.

## 2. As regras de negócio batem certo?

| Regra | `gestao-acesso` | A minha parte | |
|---|---|---|---|
| Limite = pedido + prazo (R1/R2) | `dataLimite` | Não calculo; reajo | ✅ |
| Alerta aos 75% | `dias × 3 / 4` → 22, 45, 90 | Reajo ao evento | ✅ |
| **Dia-limite ainda cumpre** | incumprido só com `hoje > limite` | Com limite a 30/11, o incumprimento é detetado a **01/12** | ✅ (exemplo do `contexto.md` corrigido) |
| Alarme de risco uma só vez | `alarmeRiscoEm` | Um caso aberto por episódio; o risco abre `EM_RISCO` (nível 0) | ✅ |
| Alarme e limite no mesmo dia | Só emite incumprido | Cria o caso logo em `REGISTADO` (nível 1) | ✅ |
| Já incumprido não é reavaliado | Só uma vez | Um só `prazo.ultrapassado` por episódio; idempotência de qualquer modo | ✅ |
| Relógio só avança | `avancarPara` recusa datas anteriores | O meu `RelogioService` aceita qualquer data | ⚠️ ver abaixo |

**Atenção ao `DELETE /demo/relogio`:** repõe a data real mas **não avisa ninguém**. Se não publicar `relogio.avancado` também no `DELETE`, o `garantia-acesso` fica "preso" na data simulada. O meu serviço aceita qualquer data, por isso um evento com a data real repõe-no.

## 3. Pontos para a revisão do PR

🔴 **Bloqueante** (sem isto o `garantia-acesso` não recebe nada) · 🟠 **Importante** (vai dar problemas na integração) · 🟡 **Sugestão**.

| # | | Ponto |
|---|---|---|
| 1 | 🔴 | **Publicar os alarmes.** `VerificacaoPrazosTarefa` e `DemoController` só usam o `ResultadoVerificacao` para o log ou a resposta HTTP. Falta publicar `prazo.em-risco` e `prazo.ultrapassado` por pedido. O `ResultadoVerificacao` só tem `List<Long>`: devolver os episódios (`especialidade`, `prioridade`, `dataLimite`, `dataAlarme`), não só ids |
| 2 | 🔴 | **Publicar `relogio.avancado`** com `dataAtual`, no `PUT` **e no `DELETE`**, e **antes** dos alarmes. O `garantia-acesso` calcula a data de deteção e os 5 dias úteis com o relógio dele; se os alarmes chegarem primeiro, usa a data antiga (provado) |
| 3 | 🟠 | **`especialidade`**: `EpisodioPrazo` usa texto livre (`"Cardiologia"`). O `historico` agrupa por texto exato, logo `"Cardiologia"` ≠ `"CARDIOLOGIA"`. Fixar um código (maiúsculas sem acentos) |
| 4 | 🟠 | **`dataHoraEvento` real vs simulada:** o valor por omissão do `MensagemBase` é `LocalDateTime.now()`. Com o relógio avançado, o `historico` (que agrupa por semana desse campo) mostraria tudo na semana real. Preencher com a data do `RelogioService` |
| 5 | 🟠 | **Publicar fora da transação:** dentro de um `@Transactional`, um *rollback* anuncia o que não foi gravado. Publicar depois de `verificar()` terminar, no chamador. (Se o RabbitMQ cair, o evento perde-se: precisaria do padrão *Outbox*, ver `explicacao.md`) |
| 6 | 🟡 | **R5:** falta o consumidor de `consulta.agendada` que use `incumprimentoPrevisto`; o evento levaria `episodioId`, `dataLimite` e `dataConsulta` |
| 7 | 🟡 | **Reavaliação de prioridade:** se o alarme já tocou e uma reavaliação **adia** o limite, o alarme não volta a tocar e a situação pode ficar `EM_RISCO` sem razão. Definir o que acontece a `alarmeRiscoEm` |
| 8 | 🟡 | **Encerrar episódios** (`encerradoEm`) com `consulta.realizada`, `consulta.falta-registada` ou `episodio.saida`; senão continuam a gerar alarmes |
| 9 | 🟡 | **Teste de contrato** com o conversor real (como o meu `EventoMessageContratoTest`): nenhum teste do `gestao-acesso` cobre o formato do evento |
| 10 | 🟡 | `RoutingKeys` e `MensagemBase` só existem em `referenciacao` e `servico-modelo`, não em `gestao-acesso`: pedir um módulo comum ou copiar |

**✅ O que está bem feito** (para dizer no PR): `CalculadoraPrazos` com funções puras; relógio **injetado** (`Clock`); alarme **idempotente** e o caso "alarme e limite no mesmo dia" **testado**; `@Scheduled` com **fuso fixo** (`Europe/Lisbon`); consultas por método derivado; perfil `demo` isolado e testado.

## 4. Resposta do Avelino e o que falta confirmar

**Já confirmado (19/11):** os dois eventos levam o envelope (`eventoId`, `traceId`, `autor`, `dataHoraEvento`) e:

- `prazo.em-risco`: `episodioId`, `especialidade`, `prioridade`, `dataPedido`, `dataAlarme`, `dataLimite`
- `prazo.ultrapassado`: `episodioId`, `especialidade`, `prioridade`, `dataPedido`, `dataLimite`
- o `episodioId` tem o **mesmo valor** do `pedidoId`.

Não preciso de mais nenhum campo desses dois: o meu `EventoMessage` já os lê e ignora o que não conhece.

**Por confirmar:**

| # | Pergunta |
|---|---|
| 1 | O `PUT` **e o `DELETE`** de `/demo/relogio` vão publicar `relogio.avancado` com `dataAtual`, **antes** dos alarmes? |
| 2 | `especialidade`: texto livre ou código fixo (`CARDIOLOGIA`)? |
| 3 | `dataHoraEvento`: data do `RelogioService` ou real? |
| 4 | Se fizer o R5, `prazo.ultrapassado-previsto` leva `dataConsulta`? |
| 5 | Quando passa a criar `EpisodioPrazo` (consumir `pedido.triado` ou `episodio.inscrito`)? |

**Proteção no meu código** (para não depender da resposta 2): comparar a especialidade **ignorando maiúsculas** (`findByEspecialidadeIgnoreCase` e `equalsIgnoreCase` em `proporTransferencia`). Para os contadores do `historico`, se a especialidade vier sempre com o mesmo texto, funciona; senão, normalizar com `toUpperCase()` ao gravar.

## 5. A mensagem para o Avelino

> Olá Avelino! Li o `feature/tmrg-regras` e a lógica dos prazos está muito boa. 👏
>
> Para o meu `garantia-acesso` funcionar com o teu `gestao-acesso`, faltam:
>
> 1. **Publicar os alarmes:** `prazo.em-risco` e `prazo.ultrapassado`. Obrigado pelos campos, não preciso de mais.
> 2. **Publicar `relogio.avancado`** com `dataAtual`, no `PUT` e no `DELETE` de `/demo/relogio`, **antes** dos alarmes.
> 3. **A `especialidade`** é texto livre ou código (`CARDIOLOGIA`)?
>
> Para o resto (não bloqueia o PR): criar o `EpisodioPrazo` quando chega um pedido; os endpoints `/regras`, `/prazos/{id}` e `/monitorizacao`; o R5.
>
> E a `lista-espera`: quando começas? Preciso do `episodio.inscrito` e do `episodio.saida`. 🙂
