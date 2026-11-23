# A minha parte: `garantia-acesso` e `historico` (Parte 3)

> **Versão 2 — alinhada com o Runbook v2.1 (19/11/2026).** Nomes anteriores: `incumprimento` → `garantia-acesso`; `indicadores` → `historico`.
> **O quê:** o que é meu, o que não é, e o contrato com os outros. **Como e quando** vou fazê-lo está em [`plano-de-trabalho.md`](plano-de-trabalho.md); o **negócio** em [`contexto.md`](contexto.md); o **Git e o POM** em [`detalhe.md`](detalhe.md).
> Fonte: Runbook_Grupo5_v2.1.md, secções 9.5, 9.6, 10, 16, 17.

## Índice

1. [Os meus dois serviços](#1-os-meus-dois-serviços)
2. [Qual é a diferença entre a minha parte e a do Avelino?](#2-qual-é-a-diferença-entre-a-minha-parte-e-a-do-avelino)
3. [Requisitos e regras que são meus](#3-requisitos-e-regras-que-são-meus)
4. [Os endpoints](#4-os-endpoints)
5. [Os eventos (o contrato)](#5-os-eventos-o-contrato)
6. [Os dados que guardo](#6-os-dados-que-guardo)
7. [As regras que implemento](#7-as-regras-que-implemento)
8. [O que NÃO é meu](#8-o-que-não-é-meu)
9. [Dependências e decisões a fechar](#9-dependências-e-decisões-a-fechar)
10. [Estrutura do projeto](#10-estrutura-do-projeto)
11. [Âmbito por fase](#11-âmbito-por-fase)
12. [Lista de entrega](#12-lista-de-entrega)

---

## 1. Os meus dois serviços

| | **`garantia-acesso`** | **`historico`** |
|---|---|---|
| **Subdomínio** | **Principal** (o coração do negócio) | Suporte |
| **Para que serve** | Trata cada incumprimento em **3 níveis** (R6) e **orquestra** a transferência para outra instituição | **Auditoria e fonte de KPI**: guarda todos os eventos de cada episódio |
| **Porta · base de dados** | 8085 · `garantia_acesso_db` | 8086 · `historico_db` |
| **Endpoints** (sem o `/landing`) | **6** | **3** (Fase 1); `/indicadores/...` na Fase 2 |
| **Eventos** | consome 5, publica 6 | consome TODOS (`#`), publica 0 |
| **Chamadas HTTP a outros serviços** | Nenhuma | Nenhuma |
| **Orquestração** | **Sim**, na transferência (a única do sistema, Fase 3) | Não |
| **Pacote Java** | `pt.estg.mtsds.acesso.garantiaacesso` | `pt.estg.mtsds.acesso.historico` |
| **Pasta em `referencia-grupo/`** | `incumprimento/` (renomear para `garantia-acesso/` no projeto real) | `indicadores/` (renomear para `historico/` no projeto real) |

**Diferença face à versão anterior:** o `historico` deixou de agregar contadores por semana. Agora guarda **uma linha por evento**, como um log imutável. Serve de auditoria e é a fonte para os indicadores calculados na Fase 2.

## 2. Qual é a diferença entre a minha parte e a do Avelino?

São **serviços diferentes** (Runbook v2.1, secção 12): **Avelino = Parte 2 (`gestao-acesso` e `agendamento`)**; **eu = Parte 3 (`garantia-acesso` e `historico`)**.

| | **Avelino (`gestao-acesso`)** | **Eu (`garantia-acesso`)** |
|---|---|---|
| Pergunta | *"Este episódio **está** em risco ou atrasado?"* | *"O prazo falhou. **O que fazemos?**"* |
| Faz | Calcula a data-limite, verifica os prazos, **deteta** o risco e o atraso, agenda automaticamente | Abre um **caso**, escala em 3 níveis, propõe outra instituição |
| Em resumo | **Deteta** o problema | **Trata** o problema |

**Analogia:** o `gestao-acesso` é o alarme de incêndio; o `garantia-acesso` é a equipa de bombeiros.

O `gestao-acesso` **publica** `prazo.em-risco`, `prazo.ultrapassado-previsto`, `prazo.ultrapassado` e `relogio.avancado` e eu **consumo-os**.

## 3. Requisitos e regras que são meus

| ID | Requisito | É meu? | Como o cumpro |
|---|---|---|---|
| **RF07** | Tratar o incumprimento por níveis (R6) | ✅ Totalmente | 3 níveis, 5 dias úteis, transferência com compensação |
| **RF08** | Guardar todos os eventos de cada episódio e consultá-los para auditoria | ✅ Totalmente | `historico` guarda uma linha por evento; `GET /historico/episodios/{id}` |
| **RF10** | Indicadores calculados a partir do historico | 🟡 Fase 2 | `/indicadores/...` calculados a partir dos eventos guardados |
| **RF06** | Alertar o risco, avisar na marcação além do limite (R5), assinalar os incumpridos | 🟡 Em parte | O alarme e o cálculo são do `gestao-acesso`; eu **reajo** (abro o caso) |
| **RF11** | Avisar o utente | 🟡 Só o evento | Publico `caso.diretor-alertado` e `transferencia.aceite`; **as `notificacoes` enviam** |

| RNF | Como cumpro |
|---|---|
| **02** Uma falha no historico não impede marcações | Tudo por eventos; ninguém espera por mim |
| **04** Cada mudança de episódio registada no historico | O `historico` guarda todos os eventos com `eventoId`, `traceId`, `autor` |
| **06** Kubernetes com réplicas e *health checks* | Deployment, ConfigMap, Secret e sondas |
| **07** Relógio configurável | `RelogioService` + `relogio.avancado` |
| **08** Logs com `traceId`, sem dados pessoais | SLF4J; o `TraceIdFilter` vem do molde |
| **09** Actuator só com `health` e `metrics` | `management.endpoints.web.exposure.include` |
| **10 / 11 / 12** DTO · erros descritivos · listas paginadas | `dtos`, `ExceptionHandlers` do molde, `PaginaDTO.de(...)` |

**Regras de negócio:** **R6** é minha. **R4, R5 e R7** são calculadas pelo `gestao-acesso`, e eu abro o caso a partir dos eventos correspondentes. **R1 e R2** não as calculo. **R3** não é minha.

**Não são meus:** RF01–RF05, RF09, RF12, RF13 e RNF01, RNF05.

## 4. Os endpoints

Todos passam pela **Gateway** (`http://localhost:8080`), que exige o cabeçalho `X-Utilizador: <id>;<PAPEL>` (exceto no `/landing`). Os `GET` estão abertos a todos os papéis válidos.

### `garantia-acesso` (6 + landing) — porta 8085

| Método | Caminho | O que faz | Papel | Códigos |
|---|---|---|---|---|
| GET | `/garantia-acesso/landing` | Confirma que responde | livre | 200 |
| GET | `/casos?esp=&nivel=&page=&size=` | Casos por especialidade e nível, **paginado** | qualquer | 200, 400 |
| GET | `/casos/{id}` | Um caso | qualquer | 200, 404 |
| PUT | `/casos/{id}/alerta-diretor` | **Nível 2**: alerta o diretor; fixa 5 dias úteis | `GESTOR_ACESSO` | 200, 404, 409 |
| POST | `/casos/{id}/transferencias` | **Nível 3**: propõe outra instituição. Corpo `{"instituicaoId": 1}` | `GESTOR_ACESSO` | **202**, 400, 404, 409 |
| PUT | `/transferencias/{id}/resposta` | Resposta da instituição. Corpo `{"aceite": true}` | `GESTOR_ACESSO` | 200, 400, 404, 409 |
| GET | `/instituicoes?esp=` | Instituições por especialidade | qualquer | 200, 400 |

### `historico` (3 + landing, Fase 1) — porta 8086

| Método | Caminho | O que devolve | Códigos |
|---|---|---|---|
| GET | `/historico/landing` | Confirma que responde | 200 |
| GET | `/historico/episodios/{id}` | Todos os eventos de um episódio, por ordem | 200, 404 |
| GET | `/historico/eventos?tipo=&de=&ate=&page=&size=` | Eventos por tipo e período, paginados | 200, 400 |
| GET | `/historico/traces/{traceId}` | Eventos de um mesmo pedido (mesmo traceId) | 200, 404 |

> Na **Fase 2** virão os endpoints `/indicadores/...` calculados a partir dos eventos do historico.

## 5. Os eventos (o contrato)

Todos na exchange **`acesso.eventos`** (Topic), uma fila por serviço e uma Dead-Letter Queue cada.

### O que consumo

#### `garantia-acesso` — fila `garantia-acesso.eventos`

| Routing key (v2.1) | Antes (v1) | Para quê |
|---|---|---|
| `prazo.em-risco` | `tmrg.risco-detetado` | 75% do prazo: abre o caso `EM_RISCO` |
| `prazo.ultrapassado-previsto` | `tmrg.incumprimento-previsto` | R5: consulta marcada depois do limite |
| `prazo.ultrapassado` | `tmrg.incumprido` | R4: data-limite passou sem consulta |
| `consulta.agendada` | (igual) | Resolve o caso aberto |
| `relogio.avancado` | (igual) | Acerta o relógio simulável |

#### `historico` — fila `historico.eventos`, binding `#`

Ouve **todos** os eventos do sistema. Não filtra por routing key.

### O que publico (`garantia-acesso`)

| Routing key (v2.1) | Antes (v1) | Nível | Quem a usa |
|---|---|---|---|
| `caso.aberto` | `incumprimento.registado` | 1 | `historico` |
| `caso.diretor-alertado` | `incumprimento.diretor-alertado` | 2 | `historico`, `notificacoes` |
| `caso.resolvido` | `incumprimento.resolvido` | — | `historico` |
| `transferencia.proposta` | (igual) | 3 | `historico`, `notificacoes` (Fase 3) |
| `transferencia.aceite` | (igual) | — | `lista-espera`, `notificacoes`, `historico` |
| `transferencia.recusada` | (igual) | — | `historico` |

```json
// prazo.ultrapassado (antes: tmrg.incumprido) — campos do gestao-acesso confirmados em 19/11
{ "eventoId": "uuid", "traceId": "abc", "autor": "sistema", "dataHoraEvento": "2026-11-15T01:00:00",
  "episodioId": 101, "especialidade": "CARDIOLOGIA", "prioridade": "PRIORITARIA",
  "dataPedido": "2026-10-01", "dataLimite": "2026-11-30" }
// relogio.avancado — igual
{ "eventoId": "uuid", "dataHoraEvento": "2026-12-01T10:00:00", "dataAtual": "2027-02-01" }
// caso.aberto (antes: incumprimento.registado)
{ "eventoId": "…", "traceId": "…", "autor": "garantia-acesso", "dataHoraEvento": "…",
  "episodioId": 101, "especialidade": "CARDIOLOGIA", "casoId": 7, "origem": "INCUMPRIDO" }
```

## 6. Os dados que guardo

| Serviço | Tabela | Conteúdo |
|---|---|---|
| `garantia-acesso` | `casos_incumprimento` | `episodioId`, `especialidade`, `origem`, `estado`, `nivel` (0 a 3), `dataDeteccao`, `dataLimiteDiretor`, `versao` |
| | `transferencias` | caso, instituição, `estado`, `dataProposta`, `respostaAte`, `dataResposta` |
| | `instituicoes` | 6 instituições **simuladas** |
| | `eventos_processados` | `eventoId` já tratados (idempotência) |
| `historico` | `evento_historico` | Uma linha por evento: `eventoId` (PK), `routingKey`, `episodioId`, `traceId`, `autor`, `dataHoraEvento`, `conteudo` (JSON) |

**Não guardo:** episódios completos, utentes, nomes, contactos nem NUS.

**Diferença face à versão anterior:** o `historico` tem uma única tabela de append (antes eram `indicador_semanal` e `eventos_processados`). A idempotência é garantida por `eventoId` como chave primária: um `INSERT` em duplicado falha silenciosamente.

## 7. As regras que implemento

| `garantia-acesso` | Onde |
|---|---|
| Só **um caso aberto** por episódio | `CasosRepository.findFirstByEpisodioIdAndEstadoIn` |
| `prazo.em-risco` → `EM_RISCO` (nível 0); `prazo.ultrapassado*` → `REGISTADO` (nível 1) | `CasosService.abrirOuEscalar` |
| Nível 2: **5 dias úteis** (sem fins de semana nem feriados configuráveis) | `alertarDiretor` + `DiasUteisService` |
| Nível 3: só **depois** dos 5 dias úteis, numa instituição que atenda a especialidade | `proporTransferencia` |
| **Aceite**: caso resolvido. **Recusada**: compensação (caso sinalizado à direção) | `registarResposta` |
| Proposta **sem resposta** no prazo: dada como recusada (tarefa diária) | `expirarTransferencias` + `TransferenciasScheduler` |
| `consulta.agendada` resolve o caso aberto | `resolverPorReagendamento` |
| Relógio simulável | `RelogioService` |

| `historico` | Regra |
|---|---|
| Append-only | Nunca há `UPDATE` nem `DELETE` na tabela `evento_historico` |
| Idempotência por PK | `eventoId` é a chave primária; duplicado → `existsById` → ignora |
| Guarda o JSON completo | `objectMapper.writeValueAsString(evento)` no campo `conteudo` |

## 8. O que NÃO é meu

| Assunto | De quem | O que lhe peço |
|---|---|---|
| Data-limite, alarme aos 75%, verificação diária, relógio do `gestao-acesso` | Avelino (Parte 2) | Publicar `prazo.em-risco`, `prazo.ultrapassado-previsto`, `prazo.ultrapassado` e `relogio.avancado` |
| Episódio de espera, estados, saída (`lista-espera`) | Nuno (Parte 1) | `episodio.inscrito` e `episodio.saida` para o `historico` |
| Receber e triar pedidos (`referenciacao`) | Nuno (Parte 1) | `pedido.triado` para o `historico` |
| Agendas, marcações, falta (`agendamento`) | Avelino (Parte 2) | `consulta.agendada`; reagir a `caso.diretor-alertado` e `transferencia.aceite` |
| Identificação e consentimentos (`utentes`); **enviar** avisos (`notificacoes`) | Pedro (Parte 4) | Que `notificacoes` consuma `caso.diretor-alertado`, `transferencia.proposta` e `transferencia.aceite` |
| Gateway, Eureka, POM pai, `docker-compose`, CI | Grupo | Nada: as rotas dos meus serviços já existem |

## 9. Dependências e decisões a fechar

**Resposta curta:** os meus dois serviços **compilam e passam os testes sozinhos**. Dependem dos colegas só para **ver a cadeia a funcionar**.

```text
   QUEM ME ALIMENTA (garantia-acesso)                                QUEM ME CONSOME
   gestao-acesso ── prazo.em-risco / ultrapassado / ultrapassado-previsto ──┐
   gestao-acesso ── relogio.avancado ────────────────────────────────────────┼► garantia-acesso ──► notificacoes [diretor-alertado, proposta, aceite]
   agendamento   ── consulta.agendada ──────────────────────────────────────┘               └──► lista-espera  [aceite]
                                                                                            └──► historico     [tudo]

   QUEM ME ALIMENTA (historico)
   TODOS os serviços (binding #) ──► historico (apenas consome, não publica)
```

**Estado do `gestao-acesso` no repositório do grupo (21/11/2026):**
O Avelino já tem os modelos (`EpisodioPrazo`, `RegraVersao`, `Relogio`), o `VerificacaoPrazosService` (deteta risco e incumprimento) e o `DemoController`. **Ainda não publicou os eventos** (`EventosPublisher` não encontrado, `listeners/` só tem `package-info`). Os eventos `prazo.em-risco` e `prazo.ultrapassado` são os que me alimentam — enquanto não estiverem, simulo-os à mão no RabbitMQ.

**Decisões a fechar:**

| # | Decisão | Com quem | A minha proposta | Estado |
|---|---|---|---|---|
| 1 | Formato de `especialidade` | Avelino e Nuno | Código em maiúsculas (`CARDIOLOGIA`); o `historico` guarda como veio | ⏳ Por confirmar |
| 2 | Campos de `prazo.ultrapassado` | Avelino | `episodioId`, `especialidade`, `prioridade`, `dataPedido`, `dataLimite` (Runbook 10, PR #9) | ✅ Confirmado |
| 3 | `relogio.avancado`: campo `dataAtual` | Avelino | Como descrito na secção 5 | ⏳ Por confirmar |
| 4 | Quem consome `caso.diretor-alertado` e `transferencia.aceite` | Nuno e Pedro | Os da secção 5 | ⏳ |

## 10. Estrutura do projeto

Os dois módulos ficam **ao lado** dos outros, sob o POM pai do grupo. As pastas em `referencia-grupo/` ainda têm os nomes antigos — ao criar no projeto real, renomeia-as.

```text
acesso-a-tempo/
├── pom.xml                  + <module>garantia-acesso</module> e <module>historico</module>
├── gestao-acesso/  agendamento/  referenciacao/  lista-espera/  ...  (dos colegas)
├── garantia-acesso/           porta 8085
│   └── src/main/java/pt/estg/mtsds/acesso/garantiaacesso/
│       ├── enums/           EstadoCaso, EstadoTransferencia, OrigemCaso
│       ├── models/          CasoIncumprimento, Transferencia, Instituicao, EventoProcessado
│       ├── data/            CasosRepository, TransferenciasRepository, InstituicoesRepository, EventosProcessadosRepository
│       ├── dtos/            CasoDTO, TransferenciaDTO, TransferenciaRequestDTO, RespostaTransferenciaDTO, InstituicaoDTO
│       ├── exceptions/      EventoInvalidoException (+ as 6 do molde)
│       ├── messages/        EventoMessage, MensagemBase, RoutingKeys
│       ├── configuration/   RabbitConfig, ModelMapperConfig, DadosIniciais, RelogioConfig, TraceIdFilter
│       ├── services/        CasosService, DiasUteisService, RelogioService, EventosPublisher, TransferenciasScheduler
│       ├── listeners/       EventosListener
│       └── controllers/     CasosController, TransferenciasController, InstituicoesController, LandingController
└── historico/               porta 8086  (mais simples: sem publisher, sem enum, sem tarefa agendada)
    └── src/main/java/pt/estg/mtsds/acesso/historico/
        ├── models/          EventoHistorico
        ├── data/            EventoHistoricoRepository
        ├── dtos/            EventoHistoricoDTO, PaginaDTO, ErroDTO
        ├── exceptions/      EventoInvalidoException (+ as 6 do molde)
        ├── messages/        EventoMessage, MensagemBase, RoutingKeys
        ├── configuration/   RabbitConfig, TraceIdFilter, ModelMapperConfig
        ├── services/        HistoricoService
        ├── listeners/       EventosListener
        └── controllers/     HistoricoController, LandingController
```

## 11. Âmbito por fase

| Fase | Inclui, para a minha parte |
|---|---|
| **1. Garantida** | Os 2 serviços a correr; `/landing`; `garantia-acesso`: abre o caso (R4), relógio; `historico`: guarda todos os eventos e responde ao `GET /historico/episodios/{id}`; Docker Compose e Minikube |
| **2. Planeada** | **Escalada até ao nível 2** (`caso.diretor-alertado`, 5 dias úteis); `GET /historico/eventos` e `/historico/traces/{traceId}`; `/indicadores/...` calculados a partir do historico |
| **3. Se houver tempo** | **Transferência orquestrada (nível 3)** com compensação; `GET /historico/traces/{traceId}` |

## 12. Lista de entrega

**`garantia-acesso`**
- [ ] 6 endpoints + landing, com os códigos da secção 4
- [ ] Os 3 níveis, com **409** nas transições inválidas
- [ ] Consome 5 eventos (`prazo.em-risco`, `prazo.ultrapassado-previsto`, `prazo.ultrapassado`, `consulta.agendada`, `relogio.avancado`); publica 6 (`caso.*`, `transferencia.*`)
- [ ] Idempotência e Dead-Letter Queue (`garantia-acesso.eventos.dlq`) provadas
- [ ] Testes a passar (`./mvnw verify`)

**`historico`**
- [ ] 3 endpoints Fase 1 + landing
- [ ] Ouve todos os eventos (`#`); persiste uma linha por evento; idempotência por PK
- [ ] Testes a passar

**Ambos**
- [ ] `/actuator/health` e `/actuator/metrics` (só estes); logs com `traceId` e sem dados pessoais
- [ ] Entradas no `docker-compose.yml` e no `k8s/` (Deployment, ConfigMap, Secret)
- [ ] README do grupo atualizado (portas, filas, routing keys); Swagger nos endpoints
- [ ] PR revisto por **outro elemento**; sei explicar **cada linha**

**Trabalho transversal (Runbook v2.1, secção 16):**
- [ ] Até **24/11**: 3 tentativas e DLQ no `servico-modelo` (configuração por propriedades, copiável por todos)
- [ ] Até **01/12**: reenvio diário da DLQ e fila de paradas (`<serviço>.eventos.parada`) no `servico-modelo`
