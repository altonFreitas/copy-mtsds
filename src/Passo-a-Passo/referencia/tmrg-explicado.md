# O `gestao-acesso` do Avelino explicado

> ⚠️ **v2.1:** o serviço `tmrg` foi renomeado para `gestao-acesso` no Runbook v2.1 (19/11/2026). As routing keys `tmrg.*` passaram a `prazo.*` (`tmrg.risco-detetado` → `prazo.em-risco`, etc.). O serviço ainda não publica eventos no repositório do grupo (21/11/2026): ver análise em `dependencia.md`.

> Explicação do código do serviço `gestao-acesso` do repositório do grupo, para perceber o que lá está antes de rever o PR.
> Base: `main` do repositório do grupo, com o PR #9 integrado (19/11/2026, 15:26). **Só leitura**: o repositório do grupo não foi alterado e o build do grupo não foi corrido.
> O que isto significa para a minha parte, os pontos para a revisão do PR e as perguntas ao Avelino estão em [`dependencia.md`](dependencia.md).

## Índice

1. [O que o `gestao-acesso` é](#1-o-que-o-tmrg-é)
2. [O mapa do código](#2-o-mapa-do-código)
3. [Os dados](#3-os-dados)
4. [`CalculadoraPrazos`: as contas](#4-calculadoraprazos-as-contas)
5. [`RelogioService`: a data de hoje](#5-relogioservice-a-data-de-hoje)
6. [A verificação diária](#6-a-verificação-diária)
7. [O relógio de demonstração](#7-o-relógio-de-demonstração)
8. [A configuração](#8-a-configuração)
9. [Os testes](#9-os-testes)
10. [O que o código ainda não faz](#10-o-que-o-código-ainda-não-faz)

---

## 1. O que o `gestao-acesso` é

O `gestao-acesso` é o serviço que **sabe os prazos**. Para cada pedido guarda a **data-limite** e a **data do alarme**; todos os dias à noite verifica quem está **em risco** ou **atrasado**.

Ele **deteta** o problema. Quem o **trata** é o `garantia-acesso` (a minha parte). Daí a analogia: o `gestao-acesso` é o alarme de incêndio e o `garantia-acesso` é a equipa de bombeiros.

---

## 2. O mapa do código

```text
tmrg/src/main/java/pt/estg/mtsds/acesso/tmrg/
├── models/          O que guarda na base de dados
│   ├── EpisodioPrazo        um registo por pedido: datas e situação
│   ├── RegraVersao          os prazos (30/60/120 dias) por versão
│   ├── Relogio              a "data de hoje" (uma só linha)
│   ├── EventoProcessado     eventos já tratados (idempotência)
│   └── Prioridade, SituacaoPrazo     (enums)
├── data/            4 repositórios (leitura e escrita sem SQL)
├── services/        A lógica (a parte importante)
│   ├── CalculadoraPrazos            as contas, sem base de dados
│   ├── RelogioService               a data de hoje (real ou simulada)
│   ├── VerificacaoPrazosService     a verificação dos prazos
│   ├── VerificacaoPrazosTarefa      corre a verificação todos os dias à 01:00
│   └── ResultadoVerificacao         o resultado (listas de ids)
├── controllers/     LandingController e DemoController (relógio de demonstração)
├── configuration/   Clock, tarefas agendadas, carga das regras, RabbitMQ, traceId, ModelMapper
├── dtos/  exceptions/  handlers/     (vêm do servico-modelo; mais 3 DTO do relógio)
└── auditing/        JpaAuditingConfiguration
```

---

## 3. Os dados

### `EpisodioPrazo` (o centro)

Um registo **por pedido**. A chave é o `pedidoId`, atribuído na `referenciacao` (por isso não é gerada aqui).

| Campo | Para quê |
|---|---|
| `pedidoId` | Chave. O identificador do episódio |
| `especialidade` | Texto livre (por exemplo, `"Cardiologia"`) |
| `dataPedido` | Data do registo do pedido (a contagem do prazo começa aqui, R1) |
| `prioridade` | `MUITO_PRIORITARIA`, `PRIORITARIA` ou `NORMAL` |
| `regraVersao` | Qual versão das regras foi usada no cálculo |
| `dataLimite`, `dataAlarme` | As duas datas calculadas |
| `situacao` | `DENTRO_PRAZO` (por omissão), `EM_RISCO` ou `INCUMPRIDO` |
| `alarmeRiscoEm` | Quando o alarme tocou (vazio = ainda não tocou) |
| `encerradoEm` | Quando o episódio acabou (vazio = ainda aberto) |
| `criadoEm`, `atualizadoEm` | Preenchidos pela auditoria do Spring Data |

Tem três índices, para as pesquisas previstas: por especialidade e data-limite, por data do alarme e só por data-limite.

### `RegraVersao`

Uma linha por **(versão, prioridade)**, com os `dias` do prazo e o `inicioVigencia`. Há uma restrição: não pode haver duas regras com a mesma versão e prioridade. As **versões antigas ficam guardadas**, para cada episódio manter a versão com que foi calculado.

### `Relogio`

Uma tabela com **uma só linha** (id 1) e a `dataAtual`. Existe para a demonstração poder avançar o tempo.

### `EventoProcessado`

O `eventoId` (chave) e o `tipo` (a routing key). Serve para **idempotência**: um evento já processado é ignorado. Ainda não é usado por nenhum consumidor.

---

## 4. `CalculadoraPrazos`: as contas

São **funções puras**: só recebem datas e devolvem datas, **sem base de dados nem relógio**. Por isso são fáceis de testar e de explicar.

| Função | O que faz | Regra |
|---|---|---|
| `dataLimite(dataPedido, dias)` | `dataPedido + dias` | R1, R2 |
| `dataAlarme(dataPedido, dias)` | `dataPedido + dias × 3 / 4` (divisão inteira) | alerta aos 75% (regra do grupo) |
| `situacaoEm(alarme, limite, hoje)` | Diz se está `DENTRO_PRAZO`, `EM_RISCO` ou `INCUMPRIDO` | R4, R7 |
| `incumprimentoPrevisto(limite, consulta)` | `consulta > limite` | R5 |

A divisão inteira (`dias × 3 / 4`) evita erros de vírgula flutuante. Com 30, 60 e 120 dias dá **22, 45 e 90** dias.

**Exemplo:** pedido a 01/01/2026, prazo de 30 dias.

| Dia | Situação |
|---|---|
| até 22/01 | `DENTRO_PRAZO` |
| **23/01** (o alarme) | `EM_RISCO` |
| **31/01** (o próprio dia-limite) | **ainda** `EM_RISCO` (ainda cumpre) |
| **01/02** | `INCUMPRIDO` |

| Prazo | Alarme (pedido a 01/01/2026) | Limite |
|---|---|---|
| 30 dias | 23/01 | 31/01 |
| 60 dias | 15/02 | 02/03 |
| 120 dias | 01/04 | 01/05 |

**O dia-limite ainda cumpre:** só é incumprimento quando `hoje > limite`. Do mesmo modo, uma consulta marcada **no** dia-limite não é incumprimento previsto; só a marcada **depois**.

`situacaoEm` também lança erro se alguma data for nula ou se o alarme for depois do limite. Numa reavaliação que **sobe** a prioridade (R7), as datas recalculadas podem já ter passado e o resultado é logo `INCUMPRIDO`.

---

## 5. `RelogioService`: a data de hoje

| Método | O que faz |
|---|---|
| `hoje()` | Se há linha na tabela `relogio`, devolve essa data; senão, a data real (`LocalDate.now(clock)`) |
| `avancarPara(data)` | Grava a nova data, criando a linha se não existir. **Só para a frente**: uma data anterior a hoje dá erro 400 (`DATA_RELOGIO_ANTERIOR`); uma data nula também (`DATA_RELOGIO_EM_FALTA`). Avançar para o próprio dia é aceite |
| `repor()` | Apaga a linha: volta à data real |

**Regra de ouro do código:** ninguém usa `LocalDate.now()` diretamente. Usa-se sempre o `RelogioService`, e o `Clock` é injetado (`RelogioConfig`), para a demonstração e os testes poderem controlar o tempo (RNF07).

---

## 6. A verificação diária

### Quem a corre

`VerificacaoPrazosTarefa`: `@Scheduled(cron = "0 0 1 * * *", zone = "Europe/Lisbon")`, **todos os dias à 01:00**. O fuso é fixo, para a hora não depender do servidor ou do contentor. Chama `VerificacaoPrazosService.verificar()` e escreve no log quantos episódios mudaram.

### O que faz `verificar()`

Usa a data de `hoje` do `RelogioService` e faz **duas pesquisas**, só sobre episódios abertos (`encerradoEm` vazio):

| Pesquisa | Apanha | O que faz |
|---|---|---|
| 1. **Alarme** | Alarme ainda por tocar **e** `dataAlarme ≤ hoje` | Grava `alarmeRiscoEm` e passa a `EM_RISCO` |
| 2. **Incumprimento** | Situação ≠ `INCUMPRIDO` **e** `dataLimite < hoje` | Passa a `INCUMPRIDO` |

Devolve um `ResultadoVerificacao` com duas listas de `pedidoId`: os que passaram a `EM_RISCO` e os que passaram a `INCUMPRIDO`.

### Três pormenores de desenho

1. **O alarme toca uma só vez.** `alarmeRiscoEm` fica preenchido e a pesquisa só apanha quem o tem vazio.
2. **Quem passa o alarme e o limite de uma vez só** (por exemplo, se a verificação não correu uns dias) fica logo `INCUMPRIDO` e **não** aparece na lista de risco; mas o alarme fica gravado. O teste `quemPassaOAlarmeEOLimiteDeUmaVezFicaComAlarmeESoNosIncumpridos` cobre isto.
3. **Não há `save()`.** O método é `@Transactional` e as entidades lidas pelo repositório são geridas pelo Hibernate: ao mudar um campo, ele grava tudo no fim da transação.

Quem já é `INCUMPRIDO` **não é reavaliado**.

---

## 7. O relógio de demonstração

`DemoController`, só com o perfil `demo` (para o usar: `SPRING_PROFILES_ACTIVE=dev,demo`):

| Pedido | O que faz |
|---|---|
| `PUT /demo/relogio` com `{"data":"2026-02-01"}` | Avança o relógio e **corre logo a verificação**. Devolve `{dataAtual, passaramEmRisco[], passaramIncumpridos[]}` |
| `DELETE /demo/relogio` | Repõe a data real |

Serve para mostrar na defesa, em segundos, um prazo a passar de "em risco" a "incumprido", sem esperar dias.

**Como funciona:** os DTO são `record` (`AvancarRelogioDTO` com `@NotNull`, `RelogioAvancadoDTO`, `RelogioDTO`). Sem data ou com data anterior a hoje, a resposta é **400** no formato comum. Na Gateway, estes dois pedidos exigem o papel `GESTOR_ACESSO`.

---

## 8. A configuração

| Classe | O que faz |
|---|---|
| `RelogioConfig` | Cria o `Clock` (a data real), para ser injetado e substituído nos testes |
| `AgendamentoConfig` | Liga as tarefas `@Scheduled` (`@EnableScheduling`) |
| `RegrasIniciaisRunner` | No arranque, **se** a tabela `regra_versao` estiver vazia, carrega a versão 1: 30, 60 e 120 dias, em vigor desde 04/05/2017 (Portaria 153/2017). Se já houver regras, não faz nada |
| `RabbitConfig` | Declara a exchange `acesso.eventos` (topic, durável) e o conversor JSON. **Não declara filas** |
| `TraceIdFilter` | Põe o `traceId` de cada pedido nos logs (cabeçalho `X-Trace-Id`; se não vier, gera um) |
| `ModelMapperConfig` | ModelMapper com estratégia `STRICT` |
| `JpaAuditingConfiguration` | Preenche `criadoEm` e `atualizadoEm` |

O `application.yml`: porta `${PORT:8084}`; perfil por omissão `dev` (base **H2** em memória); perfis `docker` e `k8s` (PostgreSQL, com `DB_URL`, `DB_USER` e `DB_PASSWORD`); só `health` e `metrics` expostos no Actuator; datas em JSON como texto ISO (não como números).

---

## 9. Os testes

Estão bem cobertos: **10 classes de teste**, com **35 testes**.

| Teste | O que cobre |
|---|---|
| `CalculadoraPrazosTest` (9) | Véspera do alarme, dia do alarme, dia-limite, dia seguinte, prioridade que sobe, R5 no dia-limite e no dia seguinte, datas nulas, alarme depois do limite |
| `RelogioServiceTest` (5) | Sem linha, com linha, avançar, recusar data anterior, repor |
| `VerificacaoPrazosServiceTest` (6) | Os casos da verificação, com repositório e relógio simulados (*mocks*) |
| `DemoControllerTest` (4) | `PUT` válido, sem data (400), data anterior (400), `DELETE` |
| `DemoControllerSemPerfilTest` (1) | Sem o perfil `demo`, o controller nem existe |
| Repositórios `@DataJpaTest` (8) | Gravar e ler; a pesquisa do alarme e a dos incumpridos; a restrição de duas regras iguais |
| `LandingControllerTest` (2) | Landing e o formato do erro 404 |

---

## 10. O que o código ainda não faz

| Em falta | Efeito |
|---|---|
| **Não publica nenhum evento** (não há `RabbitTemplate`) | `prazo.em-risco`, `prazo.ultrapassado` e `relogio.avancado` não saem |
| **Não cria `EpisodioPrazo`** (nenhum `@RabbitListener`) | A tabela `episodio_prazo` fica vazia |
| **`RegraVersao` não é lida** por nenhuma classe | Faltam 5 dos 6 endpoints da proposta (`/regras`, `/regras/vigente`, `POST /regras`, `/prazos/{id}`, `/monitorizacao`) |
| **R5**, **encerrar episódios**, **reavaliar prioridade** | Ver [`dependencia.md`](dependencia.md), secção 3 |

São lacunas normais numa branch "de regras": o PR traz a lógica de prazos e o relógio; a ligação ao resto do sistema vem a seguir. **A lógica é a parte forte** (funções puras, relógio injetado, alarme idempotente, fuso fixo, testes completos).
