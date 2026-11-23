# Plano de trabalho: o que faço, por que ordem, com que branches e commits

> **Versão 2 — alinhada com o Runbook v2.1 (19/11/2026).** Nomes anteriores: `incumprimento` → `garantia-acesso`; `indicadores` → `historico`.
> **Uma página para organizar o trabalho.** O **quê** (requisitos, endpoints, eventos) está em [`minha-parte.md`](minha-parte.md); o **negócio** em [`contexto.md`](contexto.md); o **Git e o POM** em [`detalhe.md`](detalhe.md). Aqui está o **como e o quando**.

## Índice

1. [A ideia em 30 segundos](#1-a-ideia-em-30-segundos)
2. [Tarefas independentes e dependentes](#2-tarefas-independentes-e-dependentes)
3. [Os ficheiros que uso](#3-os-ficheiros-que-uso)
4. [As tarefas independentes, uma a uma (branch, ficheiros, commits)](#4-as-tarefas-independentes-uma-a-uma)
5. [Como trabalho em cada tarefa](#5-como-trabalho-em-cada-tarefa)
6. [As regras dos commits e das branches](#6-as-regras-dos-commits-e-das-branches)
7. [Quando a parte do Avelino estiver pronta](#7-quando-a-parte-do-avelino-estiver-pronta)
8. [Plano dividido por dias](#8-plano-dividido-por-dias)

---

## 1. A ideia em 30 segundos

- **Escrevo todo o código dos meus dois serviços sem esperar por ninguém.** Os eventos dos colegas simulo-os (RabbitMQ ou testes). Só preciso deles para **ver a cadeia completa a funcionar**.
- **Uma tarefa = uma branch = 1 a 3 commits = um PR pequeno.** Cada tarefa termina com `./mvnw verify` a passar.
- **Começo pelo `historico`** (mais pequeno e 100% independente) e depois faço o `garantia-acesso`.
- A **integração** com o `gestao-acesso` e os outros é um bloco à parte (secção 7), feito quando eles publicarem.

## 2. Tarefas independentes e dependentes

**Legenda:** 🟢 faço já, sem ninguém · 🔴 depende de outro serviço.

| # | Tarefa | Depende de | |
|---|---|---|---|
| **T1** | Módulo `garantia-acesso` copiado do `servico-modelo` | Só da `main` | 🟢 |
| **T2** | Módulo `historico` copiado do `servico-modelo` | Só da `main` | 🟢 |
| **T3** | `historico`: modelo `EventoHistorico`, repositório e service | Ninguém | 🟢 |
| **T4** | `historico`: RabbitConfig (binding `#`), listener e testes | O **envelope**, que já existe (`MensagemBase`) | 🟢 testado com evento simulado |
| **T5** | `historico`: controller (3 endpoints Fase 1) | Ninguém | 🟢 |
| **T6** | `garantia-acesso`: enums, modelos, repositórios e instituições iniciais | Ninguém | 🟢 |
| **T7** | `garantia-acesso`: `DiasUteisService` e `RelogioService` | Ninguém | 🟢 |
| **T8** | `garantia-acesso`: mensagem, filas e `EventosPublisher` | O envelope, que já existe | 🟢 |
| **T9** | `garantia-acesso`: `CasosService` (3 níveis), tarefa agendada e listener | Ninguém | 🟢 testado com eventos simulados |
| **T10** | `garantia-acesso`: DTO e os 3 controllers | Ninguém | 🟢 |
| **T11** | `Dockerfile`, `docker-compose.yml` e `k8s/` dos dois serviços | **Decisão do grupo** sobre o Service | 🟢 depois de confirmar |
| **D1** | Ver os casos abrirem a sério (`prazo.em-risco`, `prazo.ultrapassado`) e o relógio | **Avelino**: o `gestao-acesso` publicar os eventos | 🔴 |
| **D2** | Resolver o caso com `consulta.agendada` | **Avelino**: o `agendamento` a funcionar | 🔴 |
| **D3** | Verificar o historico com todos os eventos do fluxo principal | Todos (os 13 eventos da Fase 1) | 🔴 |
| **D4** | Demonstração completa e coleção Postman | Todos | 🔴 |

**Resumo:** T1 a T11 são minhas e escrevo-as já. D1 a D4 só servem para **ver** o sistema inteiro.

**Estado do `gestao-acesso` (21/11/2026):** o Avelino já tem os modelos, o `VerificacaoPrazosService` e o `DemoController`. Falta o `EventosPublisher` e os listeners. Enquanto não publicar, simulo os eventos à mão. Ver a análise em [`referencia/dependencia.md`](referencia/dependencia.md) (pode estar desatualizada — verificar no repositório).

## 3. Os ficheiros que uso

| Pasta | Para quê | Quando |
|---|---|---|
| **[`referencia-grupo/`](referencia-grupo/LEIA-ME.md)** | **O código que escrevo à mão.** Pasta `incumprimento/` = `garantia-acesso`; pasta `indicadores/` = `historico` (renomear ao criar no projeto real). Compila com o `verify` do grupo | **Sempre**, em cada tarefa |
| **[`01`–`12` (capítulos)](00-LEIA-ME.md)** | **Porquê** de cada ficheiro | Antes de escrever cada ficheiro |

> Os capítulos 02–09 mostram o código da versão antiga. **A lógica do `garantia-acesso` é a mesma** (3 níveis, dias úteis, transferência); o que mudou foi o nome dos eventos. Para o `historico`, o design é completamente novo (ver secção 4 abaixo).

## 4. As tarefas independentes, uma a uma

Caminhos de `referencia-grupo/` abreviados: `his/` = `indicadores/src/main/java/pt/estg/mtsds/acesso/indicadores/`, `gar/` = `incumprimento/src/main/java/pt/estg/mtsds/acesso/incumprimento/`; testes em `…/src/test/java/…`.

| # | Branch | O que leva | Verificação |
|---|---|---|---|
| **T1** | `feature/garantia-acesso-modulo` | Módulo copiado do `servico-modelo`; `<module>` no POM pai | `verify`: 2 testes (landing e 404) |
| **T2** | `feature/historico-modulo` | O mesmo para `historico` | idem |
| **T3** | `feature/historico-dados` | `his/models/EventoHistorico`, `his/data/EventoHistoricoRepository`, `his/services/HistoricoService` (método `guardar`) | arranca e cria a tabela |
| **T4** | `feature/historico-eventos` | `his/messages/EventoMessage`, `his/configuration/RabbitConfig` (binding `#`), `his/listeners/EventosListener` | `HistoricoServiceTest`, `EventosListenerTest` |
| **T5** | `feature/historico-api` | `his/dtos/EventoHistoricoDTO`, `his/controllers/HistoricoController` (3 endpoints) | `HistoricoControllerTest` |
| **T6** | `feature/garantia-acesso-dados` | `gar/enums/` (3), `gar/models/` (4), `gar/data/` (4), `gar/configuration/DadosIniciais` | arranca e cria as 4 tabelas |
| **T7** | `feature/garantia-acesso-prazos` | `gar/services/DiasUteisService`, `gar/services/RelogioService` | `DiasUteisServiceTest` |
| **T8** | `feature/garantia-acesso-mensagens` | `gar/messages/EventoMessage`, `gar/configuration/RabbitConfig` (5 bindings: `prazo.*`, `consulta.agendada`, `relogio.avancado`), `gar/services/EventosPublisher` | `EventoMessageContratoTest`, `EventosPublisherTest` |
| **T9** | `feature/garantia-acesso-regras` | `gar/services/CasosService`, `TransferenciasScheduler`, `gar/listeners/EventosListener` | `CasosServiceTest` (switch com novas routing keys), `EventosListenerTest` |
| **T10** | `feature/garantia-acesso-api` | 5 DTO, `gar/configuration/ModelMapperConfig`, `gar/controllers/CasosController`, `TransferenciasController`, `InstituicoesController` | `CasosControllerTest` |
| **T11** | `feature/garantia-acesso-historico-infra` | `Dockerfile` ×2, entradas no `docker-compose.yml`, `k8s/` ×2 | `docker compose up` e Pods *Ready* |

**Os commits de cada tarefa:**

| # | Commits |
|---|---|
| T1 | `Esqueleto do módulo garantia-acesso a partir do servico-modelo` |
| T2 | `Esqueleto do módulo historico a partir do servico-modelo` |
| T3 | 1. `Modelo EventoHistorico (append-only) e repositório` · 2. `HistoricoService: guardar evento com idempotência por PK` |
| T4 | 1. `Fila historico.eventos com binding # e dead-letter` · 2. `Listener do historico: persiste todos os eventos` |
| T5 | 1. `DTO EventoHistoricoDTO e endpoints /historico com testes` |
| T6 | 1. `Enums do garantia-acesso (estado do caso, transferência e origem)` · 2. `Modelos e repositórios do garantia-acesso` · 3. `Instituições simuladas no arranque` |
| T7 | `Dias úteis e relógio simulável do garantia-acesso com testes` |
| T8 | 1. `Mensagem e fila do garantia-acesso (5 bindings com prazo.* e dead-letter)` · 2. `Publicação dos eventos caso.* e transferencia.*` |
| T9 | 1. `Caso de garantia-acesso: níveis 1 e 2 e 5 dias úteis` · 2. `Transferência com compensação e expiração por prazo` · 3. `Listener dos eventos do gestao-acesso e do agendamento` |
| T10 | 1. `DTO e mapeamento do garantia-acesso` · 2. `Endpoints /casos, /transferencias e /instituicoes com testes` |
| T11 | 1. `Dockerfile dos serviços garantia-acesso e historico` · 2. `Compose e k8s do garantia-acesso e do historico` |

## 5. Como trabalho em cada tarefa

```bash
cd .../acesso-a-tempo
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

git switch main && git pull                        # 1. main atualizada
git switch -c feature/historico-dados              # 2. a branch da tarefa

# 3. escrever à mão no IntelliJ, olhando para referencia-grupo/ e para o capítulo
./mvnw -pl historico -am verify                    # 4. compilar e testar

git add historico                                  # 5. commit por bloco
git commit -m "Modelo EventoHistorico e repositório"
git push -u origin feature/historico-dados         # 6. push e PR
```

## 6. As regras dos commits e das branches

| | Regra |
|---|---|
| **Branch** | `feature/<servico>-<tema>`, tudo em minúsculas. Serviços: `garantia-acesso`, `historico` |
| **Uma branch** | Uma tarefa. Nunca misturar os dois serviços (exceto T11) |
| **Commit** | Pequeno e **compilável**. Mensagem em português, sem ponto final, até ~70 caracteres |
| **Nunca** | Commitar `target/`, `.idea/`, `.env`; dar `push` na `main` |
| **PR** | Título = mensagem do commit principal. Um PR por tarefa. Pedir revisão a outro elemento |

## 7. Quando a parte do Avelino estiver pronta

**O que preciso dele** (D1): o `gestao-acesso` publicar `prazo.em-risco`, `prazo.ultrapassado-previsto`, `prazo.ultrapassado` e `relogio.avancado`.

**Estado atual:** o `gestao-acesso` tem a lógica de deteção (`VerificacaoPrazosService`) mas ainda **não publica eventos**. O `RabbitConfig` só declara a exchange; faltam fila, bindings e `EventosPublisher`.

**Como integro:**

1. `git fetch && git switch main && git pull`
2. `grep -rn "convertAndSend\|prazo.em-risco" gestao-acesso/src/main` — confirmo que já publica.
3. `docker compose up -d postgres rabbitmq`
4. Arranco `garantia-acesso` e `historico` (perfil `dev`) e `gestao-acesso` com `SPRING_PROFILES_ACTIVE=dev,demo`.
5. `PUT /demo/relogio` com data depois da data-limite (cabeçalho `X-Utilizador: 1;GESTOR_ACESSO`).
6. Verifico: `GET /casos` mostra o caso em **nível 1**, e `GET /historico/episodios/{id}` mostra os eventos.
7. **Se não aparecer nada:** abro a fila `garantia-acesso.eventos.dlq` no RabbitMQ (`localhost:15672`) e investigo o campo que falhou.

**Se o `gestao-acesso` ainda não publicar:** continuo com eventos simulados e não fico bloqueado em nenhuma das T1 a T11.

## 8. Plano dividido por dias

Calendário do Runbook v2.1 (secção 17): `/landing` e base de dados até **21/11**; endpoints mínimos e filas até **24/11**; Fase 1 de ponta a ponta até **27/11**; Minikube até **01/12**; fecho **04/12–06/12**; defesa **09/12**.

| Dia | Tarefas | No fim do dia tenho |
|---|---|---|
| **Sex 21/11** | T1, T2, T3 | Os dois módulos a compilar e o `historico` com tabela |
| **Seg 24/11** | T4, T5, T6 | **`historico` completo**; `garantia-acesso` com dados (deadline filas no `servico-modelo`) |
| **Ter 25/11** | T7, T8 | Dias úteis, relógio e publicação de eventos |
| **Qua 26/11** | T9, T10 | **`garantia-acesso` completo** |
| **Qui 27/11** | T11; D1 se o `gestao-acesso` já publicar | Docker/Compose; integração (deadline Fase 1 ponta a ponta) |
| **28/11–30/11** | Kubernetes (capítulo 12), correções dos PR | Pods *Ready* (deadline 01/12) |
| **01/12** | Reenvio diário da DLQ no `servico-modelo` (deadline) | `servico-modelo` com reenvio completo |
| **02/12–04/12** | Fase 2 se houver tempo; integração final | Demonstração de ponta a ponta |
| **05/12–06/12** | Ensaio da defesa | Sei explicar cada linha |
| **09/12** | Defesa | |

> Se o dia não der, o que **escorrega** é a T11, **não** as T1–T10.

**Lista de entrega:** na secção 12 de [`minha-parte.md`](minha-parte.md).
