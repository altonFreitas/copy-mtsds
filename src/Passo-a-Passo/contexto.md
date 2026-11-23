# Contexto de negócio da Parte 3 — `garantia-acesso` e `historico`

> **Versão 2 — alinhada com o Runbook v2.1 (19/11/2026).** Nomes anteriores: `incumprimento` → `garantia-acesso`; `indicadores` → `historico`; `tmrg` → `gestao-acesso`.

> Para perceberes **o que o código faz e porquê**, antes (ou enquanto) o escreves.
> Baseado na proposta do Grupo 5 (secções 1 a 3 e Anexos A.5, A.6 e B). Onde a proposta **não** define um pormenor e eu escolhi uma solução, está marcado com 🛠️ **decisão de design** (podes mudá-la, mas combina com o grupo).

## Índice

1. [Resumo em 1 minuto](#1-resumo-em-1-minuto)
2. [O problema que o sistema resolve](#2-o-problema-que-o-sistema-resolve)
3. [Onde a tua parte entra no fluxo](#3-onde-a-tua-parte-entra-no-fluxo)
4. [Glossário](#4-glossário)
5. [As regras de negócio que afetam a tua parte](#5-as-regras-de-negócio-que-afetam-a-tua-parte)
6. [`garantia-acesso` em detalhe](#6-garantia-acesso-em-detalhe)
7. [Exemplo completo com datas](#7-exemplo-completo-com-datas)
8. [`historico` em detalhe](#8-historico-em-detalhe)
9. [Os eventos da tua parte](#9-os-eventos-da-tua-parte)
10. [Do negócio ao código](#10-do-negócio-ao-código)
11. [Decisões de design e limitações](#11-decisões-de-design-e-limitações)
12. [Perguntas para testares se percebeste](#12-perguntas-para-testares-se-percebeste)

---

## 1. Resumo em 1 minuto

Um hospital público tem de dar a **primeira consulta de especialidade** dentro de um **prazo máximo fixado por lei** (30, 60 ou 120 dias, conforme a gravidade). Quando há mais pedidos do que vagas, a lista de espera cresce e os prazos começam a falhar.

A tua parte tem **dois serviços**:

| Serviço | Pergunta a que responde | Analogia |
|---|---|---|
| **`garantia-acesso`** | *"O prazo falhou (ou vai falhar). O que fazemos, e a quem avisamos, e por que ordem?"* | Um **procedimento de emergência** com 3 escalões: avisar → pressionar o responsável → procurar outro hospital |
| **`historico`** | *"O que aconteceu a cada episódio? Que eventos correram e quando?"* | O **diário de bordo** do hospital — registo de tudo, fonte de verdade para indicadores (Fase 2) |

Nenhum dos dois **cria** a lista de espera nem **calcula** prazos (isso é do `lista-espera` e do `gestao-acesso`, dos teus colegas). Os teus serviços **reagem** ao que os outros anunciam.

---

## 2. O problema que o sistema resolve

Hoje tudo está numa aplicação única (monólito). Quatro problemas (secção 1.2):

| Problema atual | Consequência | Quem o resolve |
|---|---|---|
| O cumprimento do prazo só se conhece nos **relatórios mensais** | Descobre-se quando o prazo **já passou** | `gestao-acesso` (alertas diários) e **o teu `garantia-acesso`** (agir) |
| As regras estão no código | Cada portaria obriga a parar o sistema | `gestao-acesso` (regras versionadas) |
| Tudo no mesmo servidor | O pico das 8h derruba faturação e internamento | Microserviços separados |
| **Ninguém vê** que episódios estão em risco nem quanto a lista vai crescer | Gestão "às cegas" | **o teu `historico`** |

> **A tua parte resolve dois dos quatro problemas**: agir quando o prazo falha (e já **não** descobri-lo tarde) e dar visibilidade.

Base legal (proposta, secção 2.1): **Portaria n.º 153/2017** (tempos máximos de resposta garantidos) e **Lei n.º 15/2014**; a **Entidade Reguladora da Saúde (ERS)** acompanha o cumprimento. Ou seja: falhar o prazo é um **problema legal**, não só operacional.

---

## 3. Onde a tua parte entra no fluxo

```text
   Centro de saúde                                 (Parte 1)
        │ pedido
        ▼
   referenciacao ── pedido.triado ──► lista-espera ── episodio.inscrito ──► gestao-acesso  (Partes 1 e 2)
                                          ▲                                        │
                                          └──────────── prazo.definido ────────────┤
                                                                                   │ todos os dias verifica prazos
                       ┌───────────────────────────────────────────────────────────┤
                       │ prazo.em-risco  (75% do prazo)                            │
                       │ prazo.ultrapassado-previsto  (consulta marcada depois do limite)
                       │ prazo.ultrapassado  (data-limite passou)                  │
                       ▼                                                           │
              ╔══════════════════╗                                                 │
              ║ garantia-acesso  ║ ◄── consulta.agendada (agendamento)             │   <- A TUA PARTE
              ╚══════════════════╝ ◄── relogio.avancado (gestao-acesso, demo)      │
                       │ caso.aberto / caso.diretor-alertado                       │
                       │ transferencia.proposta / aceite / recusada                │
                       │ caso.resolvido                                            │
                       ▼                                                           ▼
   agendamento · notificacoes · lista-espera        ╔══════════════╗ ◄── (todos os eventos, binding #)
   (reagem: reagendar, avisar, dar saída)           ║   historico  ║               <- A TUA PARTE
                                                    ╚══════════════╝
```

Duas ideias a reter:

1. **Ninguém chama ninguém.** Os serviços comunicam por **eventos** (RabbitMQ). O `gestao-acesso` anuncia *"o episódio 101 falhou o prazo"* e o teu `garantia-acesso` reage.
2. **O `garantia-acesso` é o único serviço que "manda"** num processo de vários passos (a transferência); todos os outros só reagem. Chama-se **orquestração**.

---

## 4. Glossário

| Termo | Significado |
|---|---|
| **TMRG** | *Tempo Máximo de Resposta Garantido*: o prazo legal para a primeira consulta |
| **Pedido / referenciação** | O médico de família pede a consulta ao hospital |
| **Triagem** | Um médico do hospital (o **triador**) aceita ou devolve o pedido e fixa a **prioridade** |
| **Prioridade** | Muito prioritária (30 dias), Prioritária (60), Normal (120) |
| **Episódio** | O "processo" de um pedido aceite, desde que entra na lista até sair |
| **Data-limite** | Data do **pedido** + prazo da prioridade (R2) |
| **Em risco** | Alerta aos **75%** do prazo (regra do grupo, não da portaria). **Não é um estado** do episódio |
| **Incumprimento** | A data-limite passou e a consulta não foi realizada (R4) |
| **Incumprimento previsto** | Consulta marcada para **depois** da data-limite (R5) |
| **Caso** (no `garantia-acesso`) | O "dossiê" que acompanha **um** episódio problemático, com o seu nível de tratamento |
| **Nível 1 / 2 / 3** | Escalões do tratamento (R6): registar · alertar o diretor · propor outra instituição |
| **Diretor de serviço** | Responsável clínico da especialidade; tem **5 dias úteis** para reagendar |
| **Gestor de acesso** | Quem acompanha a lista e **propõe a consulta noutra instituição** |
| **Dia útil** | Não é sábado, domingo nem feriado nacional (lista configurável) |
| **Transferência** | Propor ao doente uma consulta **noutra instituição** que consiga atendê-lo a tempo |
| **Compensação** | Desfazer/corrigir quando um passo falha (a outra instituição recusa) |
| **Saga / orquestração** | Processo com vários passos coordenado por um serviço, com compensação |
| **Relógio do sistema** | Data "de hoje" usada pelos serviços; **simulável** para demonstrações (RNF07) |
| **Modelo de leitura** | Dados preparados só para consultar (o `historico`), construídos a partir de eventos |
| **Idempotência** | Processar o mesmo evento duas vezes dá o mesmo resultado que uma |
| **Consistência eventual** | Durante instantes, serviços diferentes podem ver estados diferentes; acabam por convergir |

---

## 5. As regras de negócio que afetam a tua parte

Regras da secção 2.1 da proposta. A coluna "Quem aplica" mostra que só **R6** é inteiramente tua; as outras chegam-te **já aplicadas** pelo `gestao-acesso`, e tu **reages**.

| Regra | Em linguagem simples | Quem aplica | O que isto significa para ti |
|---|---|---|---|
| **R1** | O prazo conta **desde o pedido**, não desde a triagem | `gestao-acesso` | O teu `historico` mede o **tempo de triagem** porque a triagem demorada **gasta prazo** |
| **R2** | Data-limite = data do pedido + prazo da prioridade | `gestao-acesso` | Tu **não** calculas datas-limite; recebes eventos |
| **R3** | Falta à consulta → o pedido é anulado | `agendamento` / `lista-espera` | Não é teu, mas um episódio anulado já não precisa de caso |
| **R4** | **Incumprimento** = data-limite passou sem consulta | `gestao-acesso` publica `prazo.ultrapassado` | **Abre o caso, nível 1** |
| **R5** | **Incumprimento previsto** = consulta marcada para depois da data-limite | `gestao-acesso` publica `prazo.ultrapassado-previsto` | **Abre o caso, nível 1** (antes de o prazo falhar de facto) |
| **R6** | O incumprimento é tratado **em 3 níveis**: (1) registar e reportar; (2) alertar o diretor, que tem **5 dias úteis** (sem sábados, domingos e feriados; lista configurável) para reagendar; (3) propor consulta noutra instituição | **`garantia-acesso` (tua)** | **O coração da tua parte** (secção 6) |
| **R7** | Se a triagem sobe a prioridade e a nova data-limite **já passou**, o episódio entra logo em incumprimento | `gestao-acesso` (publica `prazo.ultrapassado`) | Para ti é igual a R4 |

### Requisitos que cobres

RF07, RF08, RF10 (em parte), RF06 (em parte), RF11 (só o evento) e os RNF 02, 03, 06 a 12: a tabela completa, com o que é meu e o que não é, está em [`minha-parte.md`](minha-parte.md), secção 3.

---

## 6. `garantia-acesso` em detalhe

### 6.1 Para que serve

Quando o `gestao-acesso` anuncia que um episódio **falhou ou vai falhar** o prazo, é preciso **agir de forma organizada**, não deixar o problema esquecido. O `garantia-acesso` cria um **caso** por episódio e acompanha-o por **escalões de pressão crescente**.

### 6.2 Os 3 níveis (regra R6)

| Nível | Ação | Quem | Prazo | O que o sistema faz |
|---|---|---|---|---|
| **1 — Registar e reportar** | O incumprimento fica **registado** e comunicado | Automático | — | Abre o caso e publica `caso.aberto` |
| **2 — Alertar o diretor** | O **diretor de serviço** é avisado e tem de **reagendar** | Diretor de serviço | **5 dias úteis** | Fixa `dataLimiteDiretor` e publica `caso.diretor-alertado` (o `agendamento` e as `notificacoes` reagem) |
| **3 — Outra instituição** | Se o diretor não reagendou a tempo, **propõe-se uma consulta noutra instituição** | Gestor de acesso | Resposta em `respostaAte` | Cria a transferência `PROPOSTA` e publica `transferencia.proposta` |

**Porque assim?** Cada nível é mais "caro" e disruptivo para o doente: primeiro tenta-se resolver **dentro** do hospital (nível 2); só se falhar se **procura fora** (nível 3).

### 6.3 O ciclo de vida de um caso

```text
                         ┌──────────────────────── consulta.agendada ────────────────────────┐
                         ▼  (qualquer caso aberto)                                            │
 prazo.em-risco ─► EM_RISCO ─(previsto/ultrapassado)─► REGISTADO ──alerta──► DIRETOR_ALERTADO ──(passaram os 5 dias úteis)──► TRANSFERENCIA_PROPOSTA
   (nível 0)                                            (nível 1)            (nível 2)                                          (nível 3)
 prazo.ultrapassado / prazo.ultrapassado-previsto ───────►   ▲                                                                        │
        (se ainda não havia caso)                                                                                                     │
                                                                              ┌─────────────── aceite ◄───────────────────────────────┤
                                                                              ▼                                                       │
                                                                          RESOLVIDO                                        recusada ou sem resposta
                                                                                                                                      ▼
                                                                                                                  SINALIZADO_DIRECAO_CLINICA
```

| Estado | Significado |
|---|---|
| `EM_RISCO` | 75% do prazo consumido; ainda **não** há incumprimento (🛠️ decisão de design: abre-se um caso para acompanhar) |
| `REGISTADO` | Nível 1. Incumprimento (ou previsto) registado e reportado |
| `DIRETOR_ALERTADO` | Nível 2. O diretor tem até `dataLimiteDiretor` para reagendar |
| `TRANSFERENCIA_PROPOSTA` | Nível 3. À espera da resposta da outra instituição |
| `RESOLVIDO` | Terminou bem: consulta marcada, ou outra instituição aceitou |
| `SINALIZADO_DIRECAO_CLINICA` | A outra instituição recusou (ou não respondeu): o episódio **mantém-se** na lista e a **direção clínica** é avisada |

### 6.4 Os 5 dias úteis

R6 diz que o diretor tem **5 dias úteis**, *"sem contar sábados, domingos e feriados nacionais, que vão estar numa lista configurável"*.

- Conta-se a partir do dia seguinte ao alerta (o próprio dia **não** conta).
- **Porque configurável?** Os feriados mudam todos os anos e podem ser alterados por lei; assim muda-se uma configuração e não o código.

### 6.5 A transferência e a compensação (a parte mais difícil)

A transferência envolve **outro sistema** (a outra instituição), tem **vários passos** e **pode falhar a meio**. Por isso é a **única parte do sistema com orquestração** (secção 4.3 da proposta): em vez de cada serviço reagir por conta própria, o `garantia-acesso` **coordena** todos os passos.

```text
incumprimento                          outra instituição (simulada)            lista-espera / agendamento
     │ transferencia.proposta ────────────────►│
     │                                          │ analisa...
     │◄──────── resposta (PUT /transferencias/{id}/resposta) ─┐
     │                                                        │
     ├─ ACEITE ──► transferencia.aceite ──────────────────────────────────────►  episódio sai da lista (TRANSFERIDO)
     │             caso.resolvido
     │
     └─ RECUSADA ou SEM RESPOSTA ──► transferencia.recusada   (COMPENSAÇÃO)
                                      o episódio MANTÉM-SE na lista;
                                      caso → SINALIZADO_DIRECAO_CLINICA
```

- **Compensação** = o que fazemos para **desfazer** o efeito de um passo que falhou. Aqui: a proposta falhou, logo o episódio **não sai** da lista e alguém humano (direção clínica) tem de decidir.
- **Timeout:** se a outra instituição **nunca responde**, a proposta não pode ficar pendurada para sempre. Há um prazo (`respostaAte`) e uma tarefa diária que, passado esse prazo, **dá a proposta como recusada** (🛠️ decisão de design: a proposta diz "se não responder no prazo, o episódio continua em espera e o caso é sinalizado", e isto implementa-o).

### 6.6 Quem faz o quê

| Ator | Interage com a tua parte | Como (no projeto, via Postman) |
|---|---|---|
| **Relógio do sistema** (`gestao-acesso`) | Publica os eventos que **abrem** os casos | Eventos `tmrg.*` |
| **Administrativo** (`agendamento`) | Ao marcar nova consulta, **resolve** o caso | Evento `consulta.agendada` |
| **Diretor de serviço** | É alertado (nível 2) e reagenda | `PUT /casos/{id}/alerta-diretor` simula o alerta |
| **Gestor de acesso** | Propõe consulta noutra instituição (nível 3) | `POST /casos/{id}/transferencias` |
| **Instituição externa (simulada)** | Responde à proposta | `PUT /transferencias/{id}/resposta` |

---

## 7. Exemplo completo com datas

**Cenário:** pedido de **Cardiologia**, prioridade **Prioritária** (60 dias).

| Data | O que acontece | Serviço | Resultado na tua parte |
|---|---|---|---|
| 01/10/2026 | Pedido registado; triagem aceita como Prioritária | `referenciacao` / `gestao-acesso` | — |
| 01/10/2026 | Data-limite = 01/10 + 60 dias = **30/11/2026** (R2) | `gestao-acesso` | — |
| 15/11/2026 | 75% do prazo (45 dias) → `prazo.em-risco` | `gestao-acesso` | Abre caso `EM_RISCO` (nível 0). **Nada é publicado** |
| 01/12/2026 | O dia-limite (30/11) ainda cumpre; **no dia seguinte**, sem consulta, o `gestao-acesso` publica `prazo.ultrapassado` | `gestao-acesso` | O caso sobe para `REGISTADO` (nível 1) e publica `caso.aberto` |
| 02/12/2026 (quarta) | O sistema alerta o diretor | tu | `DIRETOR_ALERTADO`; limite = **+5 dias úteis**: 03, 04, 07, 09 e 10/12 (**05 e 06/12 são fim de semana; 08/12 é feriado** e está na lista configurada) = **10/12/2026** |
| 07/12 | O gestor tenta propor transferência | tu | **409**: o diretor ainda tem até 10/12 |
| 11/12 | Ninguém reagendou. O gestor propõe a transferência | tu | `TRANSFERENCIA_PROPOSTA` (nível 3), `respostaAte` = 16/12 (5 dias de calendário); publica `transferencia.proposta` |
| 14/12 | A outra instituição **aceita** | tu | `ACEITE` → caso `RESOLVIDO`; publica `transferencia.aceite` e `caso.resolvido`; a `lista-espera` dá saída ao episódio |

**Variante A (recusa):** em 14/12 a instituição recusa → publica `transferencia.recusada`; o episódio fica na lista; caso `SINALIZADO_DIRECAO_CLINICA`.
**Variante B (silêncio):** ninguém responde até 16/12 → a tarefa diária dá a proposta como `RECUSADA`, igual à variante A.
**Variante C (reagendamento):** a 04/12 (antes de o diretor esgotar o prazo) o `agendamento` marca nova consulta → `consulta.agendada` → caso `RESOLVIDO` (`caso.resolvido`) e já não há transferência.

> Os números do 5 dias úteis vêm do `DiasUteisService`: a partir de uma **segunda 16/11/2026**, 5 dias úteis acabam na **segunda 23/11/2026**; com o feriado de 25/12, **segunda 21/12/2026 + 5** acaba na **terça 29/12/2026** (testes `DiasUteisServiceTest`).

---

## 8. `historico` em detalhe

### 8.1 Para que serve

Responde a dois problemas: *"o que aconteceu ao episódio X?"* (auditoria, Fase 1) e *"estamos a cumprir os prazos?"* (indicadores, Fase 2). É um **log de eventos append-only**: em vez de pré-calcular contadores, o `historico` guarda **uma linha por evento** e calcula indicadores a partir dessas linhas. Consultar o log nunca atrasa as marcações (RNF02), porque o `historico` é um serviço separado.

**Guarda só identificadores e timestamps, nunca dados pessoais** (RNF03). O campo `conteudo` guarda o JSON completo do evento, que por sua vez nunca tem nomes de doentes.

### 8.2 O modelo de dados (tabela `evento_historico`)

| Coluna | O que é | Para quê |
|---|---|---|
| `evento_id` (PK) | UUID único do evento | Idempotência: se o mesmo evento chegar duas vezes, o segundo é ignorado |
| `routing_key` | p.ex. `prazo.ultrapassado`, `pedido.triado` | Filtrar por tipo de evento |
| `episodio_id` | Identificador do episódio (pode ser null) | Ver todos os eventos de um episódio |
| `trace_id` | UUID de rastreio do fluxo | Ver todos os eventos de um pedido (ex.: da referenciação até resolução) |
| `autor` | Serviço ou utilizador que publicou | Auditoria |
| `data_hora_evento` | Timestamp do evento | Filtrar por período |
| `conteudo` | JSON completo do evento (TEXT) | Ter todos os detalhes para qualquer consulta futura |

**A idempotência é por PK:** o serviço faz `existsById(eventoId)` antes de `save()`; se o evento já foi guardado, retorna sem fazer nada.

### 8.3 Os 3 endpoints da Fase 1

| Endpoint | O que responde |
|---|---|
| `GET /historico/episodios/{id}` | Todos os eventos de um episódio, por ordem cronológica |
| `GET /historico/eventos?tipo=&de=&ate=&page=&size=` | Eventos filtrados por tipo e/ou período, paginados |
| `GET /historico/traces/{traceId}` | Todos os eventos de um mesmo pedido (mesmo `traceId`) |

### 8.4 Os indicadores (Fase 2)

Na Fase 2, os endpoints `GET /indicadores/...` ficam **no mesmo serviço `historico`**, calculados com queries SQL sobre a tabela `evento_historico`. Não há um serviço separado — os dados estão todos lá.

| Indicador | Como se calcula a partir do log |
|---|---|
| **Tamanho da lista** | `COUNT(episodio.inscrito)` − `COUNT(episodio.saida)` no período |
| **Taxa de cumprimento** | `(COUNT(episodio.inscrito) − COUNT(prazo.ultrapassado)) / COUNT(episodio.inscrito)` |
| **Tempo médio de triagem** | média de `(dataHoraEvento de pedido.triado − dataPedido)` |
| **Projeção** | média de entradas/semana − média de saídas/semana × N semanas futuras |

**Por que esta abordagem é melhor?** A versão anterior pré-calculava contadores por semana. Se precisasses de um novo indicador tinhas de **reprocessar todo o histórico**. Com o log append-only, basta escrever uma nova query — os dados já estão todos lá. A troca-off é que as queries são um pouco mais pesadas, mas como o `historico` é um serviço separado, isso nunca afeta as marcações.

---

## 9. Os eventos da tua parte

O `garantia-acesso` consome 5 eventos e publica 6; o `historico` consome **todos os eventos** (binding `#`) e guarda cada um como uma linha. A lista, os campos obrigatórios e os exemplos JSON estão em [`minha-parte.md`](minha-parte.md), secção 5 (**único sítio com o contrato**).

**Regra de ouro dos eventos (secção 4.5 da proposta):** só levam **identificadores e datas**, **nunca dados pessoais**.

---

## 10. Do negócio ao código

| Regra / conceito | Onde está no código |
|---|---|
| R4/R5 abrem o caso | `CasosService.tratarEvento` → `abrirOuEscalar` |
| R6 nível 1 | `abrirOuEscalar` publica `caso.aberto` |
| R6 nível 2 + 5 dias úteis | `CasosService.alertarDiretor` + `DiasUteisService` |
| R6 nível 3 | `CasosService.proporTransferencia` |
| Só passa ao nível 3 depois dos 5 dias úteis | `proporTransferencia` (compara com `dataLimiteDiretor`) |
| Compensação | `CasosService.registarResposta` (ramo "recusada") |
| Timeout da transferência | `expirarTransferencias` + `TransferenciasScheduler` |
| Um só caso aberto por episódio | `CasosRepository.findFirstByEpisodioIdAndEstadoIn` + `EstadoCaso.ABERTOS` |
| Relógio simulável (RNF07) | `RelogioService` |
| Feriados configuráveis | `application.yml` (`garantia-acesso.feriados`) |
| Idempotência do `garantia-acesso` | entidade `EventoProcessado` (PK `eventoId`) |
| Idempotência do `historico` | PK `eventoId` na tabela `evento_historico`; `existsById` antes de `save` |
| Guardar todos os eventos | `HistoricoService.guardar(evento, routingKey)` |
| Auditoria por episódio | `EventoHistoricoRepository.findByEpisodioIdOrderByDataHoraEventoAsc` |
| Auditoria por período/tipo | `EventoHistoricoRepository.findByRoutingKeyAndDataHoraEventoBetween` (Fase 1) |
| Indicadores (Fase 2) | queries sobre `evento_historico` no mesmo serviço `historico` |

---

## 11. Decisões de design e limitações

O que a proposta **não** especifica e foi escolhido (podes e deves confirmar com o grupo):

| 🛠️ Decisão | Porquê |
|---|---|
| O risco (75%) abre um caso **EM_RISCO nível 0**, sem publicar nada | A proposta diz que o serviço "vai atuar também antes do incumprimento, quando o prazo estiver em risco" |
| **Um caso aberto** por episódio; um novo evento do mesmo episódio só **sobe** o caso de `EM_RISCO` para nível 1 | Evita casos duplicados |
| `consulta.agendada` **resolve** o caso aberto | Corresponde à seta "vaga recuperada" da figura 2.1 |
| Só se propõe transferência **depois** dos 5 dias úteis do diretor | Alinha com o diagrama de casos de uso (*extend* "5 dias úteis sem marcação") |
| A proposta tem **5 dias (de calendário)** para ser respondida | Valor configurável (`prazo-resposta-dias`); a proposta não o fixa |
| O alerta ao diretor é um **endpoint** (`PUT .../alerta-diretor`) | A proposta não diz que evento o dispara automaticamente |

**Limitações assumidas:**

1. **Taxa de cumprimento simplificada** (conta os que entraram e não falharam); a Fase 2 pode afiná-la.
2. **RF10** — *"incumprimentos causados por triagem tardia"* **não** está implementado: só se mede o tempo médio de triagem.
3. O `historico` **ignora** 13 dos 17 eventos (fazem parte do contrato; ainda não alteram contadores).
4. **Publicar eventos não é atómico com a base de dados** (falta o padrão *Outbox*).
5. A tarefa agendada só é segura com **1 réplica** do `garantia-acesso`.
6. A "outra instituição" é **simulada**: quem responde é o Postman.

---

## 12. Perguntas para testares se percebeste

Responde **sem olhar** para o texto (e para as respostas); depois confirma. As respostas estão logo a seguir a cada pergunta.

### 1. Quem **calcula** a data-limite: tu ou o `gestao-acesso`? E quem **detecta** que passou?

**O `gestao-acesso`, nas duas coisas.** Ele guarda as regras do TMRG (30/60/120 dias), calcula a data-limite (data do pedido + prazo da prioridade, R2) e verifica todos os dias se algum prazo passou. O `garantia-acesso` **não calcula datas-limite**: só **reage** aos eventos do `gestao-acesso` (`prazo.em-risco`, `prazo.ultrapassado-previsto`, `prazo.ultrapassado`).
*Porquê assim?* As regras mudam por portaria; ficando só no `gestao-acesso`, uma nova portaria muda **um** serviço e não todos.

### 2. Qual é a diferença entre **risco**, **incumprimento previsto** e **incumprimento**?

| | Quando acontece | Evento do `gestao-acesso` | O prazo já passou? |
|---|---|---|---|
| **Risco** | Passaram **75%** do prazo (regra do grupo, não da portaria) | `prazo.em-risco` | Não |
| **Incumprimento previsto** | A consulta foi **marcada para depois** da data-limite (R5) | `prazo.ultrapassado-previsto` | Ainda não, mas **vai** passar |
| **Incumprimento** | A data-limite passou e **não houve consulta** (R4) | `prazo.ultrapassado` | **Sim** |

Exemplo (prazo de 60 dias, pedido a 01/10, data-limite 30/11): risco a 15/11; incumprido a **01/12** se o último dia (30/11) passou sem consulta. Se a 20/11 marcarem a consulta para 10/12, é um incumprimento **previsto**.
No código: o risco abre um caso `EM_RISCO` (nível 0, sem publicar nada); os outros dois abrem um caso `REGISTADO` (nível 1) e publicam `caso.aberto`.

### 3. Porque é que o nível 3 só pode acontecer **depois** dos 5 dias úteis do diretor?

Porque a regra R6 define uma **escalada por escalões**: primeiro tenta-se resolver **dentro** do hospital (o diretor tem 5 dias úteis para reagendar) e só se **falhar** se procura **fora**. Propor já a outra instituição seria:
- **desrespeitar o prazo** que o diretor tem por regra;
- **mais disruptivo para o doente** (mudar de hospital) quando ainda podia ser resolvido sem isso.

No código: `proporTransferencia` devolve **409** se `hoje` não for posterior a `dataLimiteDiretor` (mensagem: *"o diretor ainda tem até ... para reagendar"*). É coerente com o diagrama de casos de uso da proposta (*"extend"* com a condição *"5 dias úteis sem marcação"*).

### 4. Por que razão "5 dias úteis" não é o mesmo que "7 dias"? Dá um exemplo com um feriado.

Dias úteis **não contam sábados, domingos nem feriados nacionais** (R6), por isso 5 dias úteis são **mais** do que 5 dias de calendário (normalmente uma semana, e mais com feriados).

Exemplos com o código:
- A partir de **segunda 16/11/2026**: 17, 18, 19, 20 e 23 → termina na **segunda 23/11** (7 dias de calendário: o fim de semana não conta).
- Com feriado, a partir de **segunda 21/12/2026**: 22, 23, 24, (25 é feriado, 26 e 27 fim de semana), 28 e 29 → termina na **terça 29/12** (**8** dias de calendário).

Os feriados vêm de uma **lista configurável** (`application.yml`), para mudar sem alterar o código. O dia em que se alerta **não conta** (conta-se a partir do dia seguinte).

### 5. O que é a **compensação** e o que acontece ao episódio se a outra instituição recusar?

**Compensação** é o que se faz para **desfazer ou corrigir** o efeito de um passo que **falhou** num processo com vários passos (uma *saga*). Aqui, o passo é "propor a consulta noutra instituição"; se ela **recusa**, a proposta falhou, por isso é preciso repor o estado anterior.

Se a instituição recusar:
- o **episódio mantém-se na lista de espera** (não sai, não é "transferido");
- o `garantia-acesso` publica **`transferencia.recusada`**;
- o caso passa a **`SINALIZADO_DIRECAO_CLINICA`**: **uma pessoa** (direção clínica) tem de decidir o que fazer.

Se aceitar, o oposto: `transferencia.aceite` + `caso.resolvido`, e a `lista-espera` dá saída ao episódio.

### 6. O que acontece se a instituição **nunca** responder?

Sem tratamento, a proposta ficaria **pendurada para sempre** e o doente sem solução. Por isso cada proposta tem um prazo, **`respostaAte`** (5 dias de calendário, configurável). Uma **tarefa agendada diária** (`TransferenciasScheduler`) procura as propostas sem resposta cujo prazo terminou e **trata-as como recusadas** (o mesmo que a pergunta 5, caminho da recusa).

É o **timeout** da saga: garante que o processo termina sempre, num estado conhecido. A proposta (secção 4.3) diz exatamente isto: *"se a instituição recusar ou não responder no prazo, o episódio vai continuar em espera e o caso vai ser sinalizado à direção clínica"*.

### 7. Se o `historico` estiver em baixo, o `garantia-acesso` continua a funcionar? Porquê?

**Sim.** Os serviços comunicam por **eventos** (RabbitMQ), não por chamadas diretas: o `garantia-acesso` publica o evento na exchange e **segue em frente**, sem esperar por ninguém. O que o `historico` não recebeu **fica na fila dele** e é processado quando ele voltar (as mensagens não se perdem).

É o requisito **RNF02**: *"uma falha nos indicadores ou nas notificações não deverá impedir a marcação de consultas"*. Se a ligação fosse síncrona (HTTP), uma falha no `historico` faria **falhar** o `garantia-acesso`.

### 8. Porque é que o `historico` guarda **um registo por evento** e não contadores pré-calculados?

Por 3 razões:
1. **Flexibilidade:** se quiseres um novo indicador, basta escrever uma nova query. Com contadores pré-calculados terias de **reprocessar todo o histórico**.
2. **Auditoria completa:** o `historico` guarda o JSON de cada evento, o `traceId` e o `autor`. Qualquer auditoria (ex.: *"quem abriu o caso 42 e quando?"*) fica respondida sem perguntar a outros serviços.
3. **Proteção de dados (RNF03):** os eventos só levam identificadores, nunca nomes de doentes. O campo `conteudo` guarda o JSON do evento (que também não tem dados pessoais).

A troca-off é que as queries de indicadores são um pouco mais pesadas (calculam na hora), mas como o `historico` é um serviço separado, **nunca atrasa as marcações** (RNF02).

É um **modelo de leitura append-only**: construído a partir dos eventos, só serve para consultar; nunca há `UPDATE` nem `DELETE`.

### 9. Porque é que os eventos **não** levam o nome do doente?

Porque os **dados pessoais** (nome, contactos) existem **só no serviço `utentes`** (RNF03). Se andassem nos eventos, ficariam **espalhados** por filas, logs e bases de dados de vários serviços, **aumentando o risco** de fuga e dificultando a proteção de dados (RGPD).

Os eventos levam **só identificadores e datas** (por exemplo `episodioId`). Quem precisar de um contacto (as `notificacoes`) **pergunta ao `utentes`** na hora, por uma das duas únicas chamadas síncronas do sistema. Isto também permite que o `utentes` registe **quem consultou** os dados e quando (RF13).

### 10. O que é a **idempotência** e que problema real evita no teu código?

**Idempotência:** processar o mesmo evento **duas vezes** dá o **mesmo resultado** que processá-lo uma.

*Problema real:* o RabbitMQ garante entrega **"pelo menos uma vez"**, ou seja, o **mesmo evento pode chegar repetido** (por exemplo, o ack perdeu-se ou houve uma repetição após falha). Sem proteção:
- no `garantia-acesso`: o mesmo `prazo.ultrapassado` abriria **dois casos** e publicaria `caso.aberto` duas vezes;
- no `historico`: o contador de entradas subiria **duas vezes** e a lista pareceria maior do que é.

*Como o código evita:*
- **`garantia-acesso`:** guarda o `eventoId` na entidade `EventoProcessado` **na mesma transação**; se já existir, ignora.
- **`historico`:** o `eventoId` é a **primary key** da tabela `evento_historico`; faz `existsById` antes de `save`. Se já existir, retorna sem fazer nada (testes `eventoRepetidoEIgnorado` e `eventoRepetidoNaoCriaSegundoCaso`).

### 11. Porque é que a **triagem lenta** faz subir o número de incumprimentos (R1)?

Porque a **contagem do prazo começa no pedido, não na triagem** (R1). Cada dia que o triador demora a aceitar o pedido é um dia **consumido** do prazo, sem que o doente tenha ainda sido inscrito na lista, e sobra **menos tempo** para marcar a consulta.

Exemplo: prioridade "Muito prioritária" (30 dias). Pedido a 01/03; triagem a 15/03 → a consulta tem de acontecer em **15 dias**, não em 30. Em casos extremos (R7), a triagem sobe a prioridade tarde, a nova data-limite **já passou** e o episódio entra **logo** em incumprimento.

Por isso, na Fase 2, o `historico` calcula o **tempo médio de triagem** a partir dos eventos `pedido.triado` que guarda: `GET /historico/eventos?tipo=pedido.triado` permite ver esses eventos e **calcular o problema antes** de gerar incumprimentos (RF10).

### 12. Porque é a transferência a **única** orquestração do sistema?

No resto do sistema usa-se **coreografia**: ninguém manda; cada serviço **reage** aos eventos que lhe interessam (por exemplo, `pedido.triado` → a `lista-espera` abre o episódio → o `gestao-acesso` calcula o prazo...). É simples, desacoplada e resiliente.

A transferência é diferente porque:
- tem **vários passos** (propor → esperar resposta → aceitar ou recusar → dar saída ou compensar);
- envolve um **sistema externo** (a outra instituição) que pode **recusar ou não responder**;
- **pode ter de ser desfeita** (compensação) e precisa de **timeout**.

Quando um processo tem passos que dependem uns dos outros e pode falhar a meio, **alguém tem de ter a visão do conjunto** e decidir o que fazer em cada falha. Esse "alguém" é o `garantia-acesso` (o **orquestrador**). Com coreografia pura, a lógica de compensação ficaria **espalhada** por vários serviços e seria difícil de seguir e de depurar.

---

*Se conseguires responder a estas 12 e desenhar no quadro o ciclo de vida do caso (secção 6.3), estás preparado para a parte de negócio da defesa.*
