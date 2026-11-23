# O resto do repositório do grupo explicado (Gateway, Eureka, modelo, referenciação e ferramentas)

> ⚠️ **v2.1:** o serviço `tmrg` foi renomeado para `gestao-acesso`; `incumprimento` → `garantia-acesso`; `indicadores` → `historico`. Esta análise foi feita sobre o PR #9 de 19/11/2026.

> Complementa [`tmrg-explicado.md`](tmrg-explicado.md). Base: `main` do repositório do grupo (19/11/2026, com o PR #9), **só leitura**: não alterei o repositório nem corri o build do grupo. Onde digo "não testei", é porque só li o código.

## Índice

1. [Visão geral: o que existe e o que não existe](#1-visão-geral-o-que-existe-e-o-que-não-existe)
2. [A raiz do repositório e as ferramentas](#2-a-raiz-do-repositório-e-as-ferramentas)
3. [A infraestrutura (Docker Compose e base de dados)](#3-a-infraestrutura-docker-compose-e-base-de-dados)
4. [O Eureka](#4-o-eureka)
5. [A Gateway](#5-a-gateway)
6. [O `servico-modelo` (o molde dos 8 serviços)](#6-o-servico-modelo-o-molde-dos-8-serviços)
7. [A `referenciacao`](#7-a-referenciacao)
8. [O que ainda não existe](#8-o-que-ainda-não-existe)
9. [O que levar daqui para a minha parte](#9-o-que-levar-daqui-para-a-minha-parte)

---

## 1. Visão geral: o que existe e o que não existe

```text
acesso-a-tempo/                  módulos Maven (pom pai: Spring Boot 3.5.16, JDK 17)
├── eureka/          ✅ pronto     registo de serviços (porta 8761)
├── gateway/         ✅ pronto     ponto de entrada (porta 8080): rotas, traceId, X-Utilizador, permissões
├── servico-modelo/  ✅ pronto     o molde: o que todos os serviços têm em comum
├── referenciacao/   🟡 só a base  uma cópia do molde + OpenFeign (porta 8081)
├── tmrg/            🟡 na main    ver tmrg-explicado.md (porta 8084); sem eventos
└── (lista-espera, agendamento, incumprimento, indicadores, utentes, notificacoes)   ❌ ainda não existem
```

| Módulo | Estado | O que tem |
|---|---|---|
| `eureka` | ✅ | O servidor de registo |
| `gateway` | ✅ | 9 rotas, filtro de `traceId`, filtro de `X-Utilizador` com 19 permissões, 11 testes |
| `servico-modelo` | ✅ | Estrutura, erros, paginação, auditoria, RabbitMQ, relógio, logs |
| `referenciacao` | 🟡 | O molde + `spring-cloud-starter-openfeign`. **Sem endpoints, modelo nem eventos** |
| `gestao-acesso` | 🟡 | Lógica de prazos, relógio e verificação (integrado na `main`, PR #9). **Não publica eventos** |

---

## 2. A raiz do repositório e as ferramentas

### 2.1 O `pom.xml` pai

| O que tem | Para quê |
|---|---|
| `spring-boot-starter-parent` **3.5.16** | Fixa a versão do Spring Boot **num só sítio** (regra 12.5 da proposta) |
| Spring Cloud **2025.0.3** (importado no `dependencyManagement`) | Eureka, Gateway, LoadBalancer, OpenFeign |
| ModelMapper **3.2.8**, Lombok (opcional), `spring-boot-starter-test` | Dependências comuns a todos os módulos |
| `maven-enforcer-plugin` | **Falha o build** se o JDK não for o **17** (`[17,18)`) ou o Maven for anterior ao 3.9 |
| `spotless-maven-plugin` (formatador Eclipse) | Formata o código igual para todos. **Ao compilar, aplica a formatação** (`process-sources`); **no CI só verifica** |
| `<modules>` | `eureka`, `gateway`, `referenciacao`, `servico-modelo`, `gestao-acesso` |

### 2.2 Maven Wrapper (`mvnw`)

O grupo usa sempre `./mvnw` (Maven 3.9.11), não o Maven instalado, para todos terem a mesma versão.

### 2.3 CI (`.github/workflows/build.yml`)

Corre em **pull requests para a `main`** e em *push* na `main`:

1. Instala o JDK 17 (Temurin).
2. Corre `./mvnw -B -ntp -Dspotless.apply.skip=true spotless:check verify`.

Ou seja: **compila, corre todos os testes e recusa código mal formatado**. Por isso convém correr `./mvnw verify` localmente antes do PR (o Spotless formata por ti).

### 2.4 Regras de trabalho

| Ficheiro | O que faz |
|---|---|
| `.githooks/pre-push` | **Bloqueia o `push` direto para a `main`**. Ativa-se **uma vez por clone**: `git config core.hooksPath .githooks`. A mensagem pede para criares uma branch `feature/<servico>-<tema>` e abrires um PR |
| `.github/pull_request_template.md` | A *checklist* de cada PR: `./mvnw verify` passa; endpoints e eventos iguais à proposta; DTO (nunca entidades); erros no formato comum; listas paginadas; logs sem dados pessoais; serviço no `docker-compose.yml` e no `k8s/`; README atualizado (portas, filas, routing keys); **revisão por um elemento de outra equipa** |
| `.editorconfig` | UTF-8, `lf`, 4 espaços (2 em `yml` e `json`) |
| `.gitignore` | Ignora `target/`, `.env`, `.idea/`, `.vagrant/`, `*.ova`, parte do `.vscode/` |
| `.vscode/` | `extensions.json`, `settings.json` e `launch.json` partilhados |

> ⚠️ **O `.editorconfig` diz "espaços" mas o código Java usa tabulações.** É o Spotless (Eclipse) que decide: ao compilar, reformata os ficheiros Java com tabulações. Não te preocupes com isto, deixa o `./mvnw` formatar.

### 2.5 Pastas ainda quase vazias

| Pasta | Conteúdo |
|---|---|
| `k8s/` | Só um README: *"Um Deployment e um Service por serviço, ConfigMap e Secret. Imagens construídas no Docker do Minikube (`eval $(minikube docker-env)`), com tag fixa e `imagePullPolicy: Never`."* |
| `postman/` | Só um README: coleção com os cenários da proposta (secção 11.1); todos os pedidos pela Gateway em `http://localhost:8080` |
| `docs/` | Só um README: proposta, relatório, diagramas, coleção Postman |

---

## 3. A infraestrutura (Docker Compose e base de dados)

`docker-compose.yml` (projeto `acesso-a-tempo`): **só a infraestrutura**; os serviços entram *"à medida que forem criados"*.

| Serviço | Imagem | Portas | Notas |
|---|---|---|---|
| `postgres` | `postgres:17` | 5432 | Volume `pgdata`; *healthcheck* com `pg_isready`; corre os scripts de `infra/postgres/init/` |
| `pgadmin` | `dpage/pgadmin4:9.18.0` | 9999 | Interface web |
| `rabbitmq` | `rabbitmq:3.13-management` | 5672 e 15672 | *Healthcheck* com `rabbitmq-diagnostics ping` |

**As credenciais vêm do `.env`** (copiar de `.env.exemplo`, que tem valores de desenvolvimento iguais para todos: `Grupo5`; RabbitMQ com utilizador `acesso`). O `.env` nunca vai para o repositório.

### `infra/postgres/init/01-bases-por-servico.sh`

Corre **uma só vez**, quando o volume está vazio. Para cada um dos 8 serviços (`referenciacao`, `agendamento`, `lista_espera`, `gestao-acesso`, `garantia-acesso`, `historico`, `utentes`, `notificacoes`):

1. Cria um **utilizador** com o nome do serviço (por exemplo, `garantia-acesso`) e a palavra-passe `SERVICOS_DB_PASSWORD`.
2. Cria a **base de dados** `<servico>_db` (por exemplo, `incumprimento_db`), com esse utilizador como dono.
3. `REVOKE ALL ... FROM PUBLIC`: mais ninguém lhe acede. Resultado: **cada serviço só vê a sua base** (*database per service*).

Para repetir: `docker compose down -v` e `docker compose up -d`.

---

## 4. O Eureka

Muito pequeno: `@SpringBootApplication` + `@EnableEurekaServer`.

| Configuração | Valor |
|---|---|
| Porta | **8761** |
| `register-with-eureka` e `fetch-registry` | **`false`**: o servidor não se regista nele próprio nem lê o seu registo (o mesmo da aula 04) |
| `eureka.instance.hostname` | `${EUREKA_HOSTNAME:localhost}` |
| Actuator | só `health` e `metrics`, com *probes* |

**O que faz:** é o **diretório de serviços**. Cada serviço regista-se com o seu `spring.application.name` e envia *heartbeats*; a Gateway consulta-o para saber onde estão as instâncias.

> Para a minha parte: o nome com que me registo é o `spring.application.name` (`garantia-acesso`, `historico`). A Gateway já tem as rotas `lb://incumprimento` e `lb://indicadores`.

---

## 5. A Gateway

É o **ponto de entrada único** (porta **8080**): tudo o que o Postman envia passa por aqui. Usa **Spring Cloud Gateway (WebFlux)**: `spring-cloud-starter-gateway-server-webflux`, o nome novo do starter na versão 2025 (ver `10-integracao-ponta-a-ponta.md`).

### 5.1 As rotas (`application.yml`)

Cada rota encaminha para `lb://<nome-no-Eureka>` (o LoadBalancer escolhe a instância). Os recursos da API **não têm prefixo** do serviço (`/pedidos`, não `/referenciacao/pedidos`); a landing é a exceção (`/<serviço>/landing`).

| Serviço | Caminhos |
|---|---|
| `referenciacao` | `/referenciacao/landing`, `/pedidos/**` |
| `agendamento` | `/agendamento/landing`, `/agendas/**`, `/marcacoes/**` |
| `lista-espera` | `/lista-espera/landing`, `/episodios/**` |
| `gestao-acesso` | `/tmrg/landing`, `/regras/**`, `/prazos/**`, `/monitorizacao/**`, `/demo/**` |
| **`garantia-acesso`** | `/incumprimento/landing`, `/casos/**`, `/transferencias/**`, `/instituicoes/**` |
| **`historico`** | `/indicadores/**` |
| `utentes` | `/utentes/**` |
| `notificacoes` | `/notificacoes/**`, `/modelos/**`, `/lembretes/**` |
| `servico-modelo` | `/servico-modelo/**` (temporária) |

### 5.2 Os dois filtros globais

**`TraceIdFilter`** (corre primeiro): garante que cada pedido tem um cabeçalho **`X-Trace-Id`**. Se não vier, gera um com 16 caracteres hexadecimais. O valor segue para o serviço e **volta na resposta** (só uma vez: substitui o que o serviço devolver).

**`UtilizadorFilter`** (corre a seguir): valida o cabeçalho **`X-Utilizador`**, no formato `<id>;<PAPEL>` (por exemplo, `123;GESTOR_ACESSO`).

```text
Caminho livre? (/*/landing, /actuator/**)  ──sim──► passa sem cabeçalho
        │ não
Cabeçalho em falta ───────────────────────► 401  X_UTILIZADOR_EM_FALTA
Mal formado (não é <id>;<PAPEL>) ─────────► 401  X_UTILIZADOR_MAL_FORMADO
Papel desconhecido ───────────────────────► 401  PAPEL_DESCONHECIDO
É um GET? ──sim──► passa (aberto a todos os papéis)
        │ não
Operação não listada nas permissões ──────► 403  OPERACAO_NAO_PERMITIDA
Papel não está na lista da operação ──────► 403  PAPEL_SEM_PERMISSAO
Caso contrário ───────────────────────────► passa para o serviço
```

- **Os papéis** são 6: `MEDICO_FAMILIA`, `TRIADOR`, `ADMINISTRATIVO`, `DIRETOR_SERVICO`, `GESTOR_ACESSO`, `UTENTE`.
- Os erros 401 e 403 saem **no formato comum** (`servico`, `estado`, `erro`, `mensagem`, `caminho`, `dataHora`, `traceId`), com o código estável em `erro`.
- O cabeçalho **segue para o serviço sem alterações**, *"para o serviço guardar o id como autor"*.
- As regras são lidas do `application.yml` (`acesso.autorizacao`), por uma classe `AutorizacaoProperties` (`record`); um caminho inválido impede a Gateway de arrancar.

### 5.3 As 19 permissões (só estas operações de escrita passam; os `GET` estão abertos)

| Operação | Papéis |
|---|---|
| `POST /pedidos` | `MEDICO_FAMILIA` |
| `PUT /pedidos/*/triagem` e `/prioridade` | `TRIADOR` |
| `POST /agendas` | `GESTOR_ACESSO` |
| `POST /marcacoes`, `PUT /marcacoes/*/realizacao`, `DELETE /marcacoes/*` | `ADMINISTRATIVO`, `DIRETOR_SERVICO` |
| `POST /episodios/*/saida` | `ADMINISTRATIVO`, `GESTOR_ACESSO` |
| `POST /regras` | `GESTOR_ACESSO` |
| `PUT` e `DELETE /demo/relogio` | `GESTOR_ACESSO` |
| **`PUT /casos/*/alerta-diretor`** | **`GESTOR_ACESSO`** |
| **`POST /casos/*/transferencias`** | **`GESTOR_ACESSO`** |
| **`PUT /transferencias/*/resposta`** | **`GESTOR_ACESSO`** |
| `POST /utentes`, `PUT /utentes/*/contactos` e `/consentimentos` | `ADMINISTRATIVO` |
| `POST /notificacoes/*/reenvio`, `PUT /modelos/*` | `ADMINISTRATIVO`, `GESTOR_ACESSO` |

Os `GET` (como `/incumprimentos` e `/indicadores/...`) não precisam de papel específico, mas **precisam do cabeçalho** `X-Utilizador` (excepto a landing).

### 5.4 Os testes (11)

`FiltrosGlobaisTest`: GET aberto a todos, landing sem cabeçalho, operação permitida com cabeçalho inalterado, variável no caminho, 401 sem cabeçalho, 401 papel desconhecido, 403 sem permissão, 403 método não listado, geração e passagem do `traceId`, uma só ocorrência do `traceId` na resposta e manutenção do `traceId` recebido.

---

## 6. O `servico-modelo` (o molde dos 8 serviços)

Um serviço **vazio mas completo**: o `pom.xml` diz *"copiar o módulo, mudar artifactId, pacote, nome e porta"*. Todos os serviços nascem daqui (o `gestao-acesso` e a `referenciacao` foram criados assim). Pacote: `pt.estg.mtsds.acesso.modelo`. Porta por omissão **8099**.

### 6.1 O `pom.xml`

`web`, `validation`, `data-jpa`, `amqp`, `actuator`, **Eureka client**, ModelMapper, **PostgreSQL** (runtime) e **H2** (runtime, para o perfil `dev` e testes). Sem OpenFeign: só a `referenciacao` o acrescenta.

### 6.2 O `application.yml` (perfis)

| Perfil | Base de dados | Quando |
|---|---|---|
| **`dev`** (por omissão) | H2 em memória | Desenvolvimento local e testes: **não precisa de Docker** |
| **`docker`** e **`k8s`** | PostgreSQL: `DB_URL`, `DB_USER`, `DB_PASSWORD` | Compose e Kubernetes |

Também: porta `${PORT:8099}`; `ddl-auto: update`; `open-in-view: false`; paginação com 20 por omissão e **máximo 100**; RabbitMQ em `${RABBITMQ_HOST:localhost}`, utilizador `acesso`, palavra-passe `Grupo5` por omissão; Eureka em `${EUREKA_URL:http://localhost:8761/eureka/}`; datas em JSON como texto ISO; Actuator só com `health` e `metrics` (com `show-details: always` e *probes*).

### 6.3 O que há em cada pacote

| Pacote | O que tem | Convenção (os `package-info.java`) |
|---|---|---|
| `controllers` | `LandingController` | *Recebem e devolvem só DTO; cada GET de lista é paginado (RNF12)* |
| `data` | (vazio) | *Repositórios Spring Data JPA. As listas usam `Pageable`; os indicadores usam `@Query`* |
| `dtos` | `ErroDTO`, `ErroCampoDTO`, `PaginaDTO` | *Os de entrada são validados com `@Valid`* |
| `models` | (vazio) | *Entidades JPA. Nunca saem do serviço (RNF10)* |
| `services` | (vazio) | *Regras de negócio e transações (`@Transactional`). Os controllers só recebem e respondem* |
| `listeners` | (vazio) | *Consumidores `@RabbitListener`. Devem ser **idempotentes***: processar duas vezes deixa o mesmo estado |
| `messages` | `MensagemBase`, `RoutingKeys` | *Eventos só com identificadores e datas* |
| `exceptions` | 6 exceções | Ver 6.4 |
| `handlers` | `ExceptionHandlers` | Ver 6.5 |
| `configuration` | `ModelMapperConfig`, `RabbitConfig`, `RelogioConfig`, `TraceIdFilter` | Ver 6.6 |
| `auditing` | `JpaAuditingConfiguration` | `@EnableJpaAuditing` |

### 6.4 As exceções

Todas estendem `ErroNegocioException` (`codigo` estável + `mensagem` descritiva, RNF11):

| Exceção | Código HTTP | Para quê |
|---|---|---|
| `NaoEncontradoException(codigo, mensagem)` | **404** | Recurso inexistente |
| `DadosInvalidosException(codigo, mensagem)` | **400** | Dados que a validação dos DTO não apanha |
| `RegraNegocioException(codigo, mensagem)` | **409** | Regra violada (vaga ocupada, caso fechado...) |
| `TransicaoInvalidaException(mensagem)` | **409** | Estende a anterior; código `TRANSICAO_INVALIDA` |
| `ServicoIndisponivelException(codigo, mensagem)` | **503** | Um serviço chamado por HTTP não respondeu |

### 6.5 `ExceptionHandlers` (`@RestControllerAdvice`)

Todas as respostas de erro usam o `ErroDTO` (`servico`, `estado`, `erro`, `mensagem`, `erros[]` opcional, `caminho`, `dataHora`, `traceId` do MDC). O detalhe técnico **só vai para o log**.

| Apanha | Código | `erro` |
|---|---|---|
| As 4 exceções do grupo | 404 / 400 / 409 / 503 | o `codigo` da exceção |
| `MethodArgumentNotValidException` (`@Valid`) | 400 | `DADOS_INVALIDOS`, com a lista `erros[]` por campo |
| `HttpMessageNotReadableException` | 400 | `JSON_INVALIDO` |
| `MethodArgumentTypeMismatchException` | 400 | `IDENTIFICADOR_INVALIDO` |
| `NoResourceFoundException` | 404 | `ROTA_NAO_ENCONTRADA` |
| `HttpRequestMethodNotSupportedException` | 405 | `METODO_NAO_SUPORTADO` |
| `Exception` (tudo o resto) | 500 | `ERRO_INTERNO`, com o `traceId` para dar ao suporte |

### 6.6 A configuração e as mensagens

| Classe | O que faz |
|---|---|
| `RabbitConfig` | Declara a exchange **`acesso.eventos`** (topic, durável) e o conversor JSON (`Jackson2JsonMessageConverter` com o `ObjectMapper` do Spring). *Cada serviço declara aqui só as filas que consome, ligadas por routing key.* **O molde não declara filas** |
| `RelogioConfig` | O bean `Clock` (a data real), para testar e simular o tempo |
| `TraceIdFilter` | Filtro servlet: lê `X-Trace-Id` (ou gera um), põe-no no **MDC** dos logs e na resposta |
| `ModelMapperConfig` | ModelMapper com estratégia **`STRICT`** |
| `MensagemBase` | O **envelope** de todos os eventos: `eventoId` (UUID, para idempotência), `traceId`, `autor` e `dataHoraEvento`. Cada evento estende-a com `@SuperBuilder` |
| `RoutingKeys` | **22 constantes** com as routing keys (`pedido.triado`, `prazo.ultrapassado`, `transferencia.aceite`...) |
| `PaginaDTO<T>` | `conteudo`, `pagina`, `tamanho`, `totalElementos`, `totalPaginas`, e o método **`PaginaDTO.de(Page, conversor)`** que converte uma página de entidades numa de DTO |

### 6.7 `LandingController` e o teste

O caminho usa o nome do serviço: **`GET /${spring.application.name}/landing`** (por isso a mesma classe serve todos os serviços). Responde `O serviço <nome> está a funcionar.` (o cabeçalho `X-Trace-Id` na resposta vem do `TraceIdFilter`). O `LandingControllerTest` confirma isto e o formato do erro 404 (`ROTA_NAO_ENCONTRADA`).

---

## 7. A `referenciacao`

**Hoje é uma cópia do molde com duas diferenças:**

1. Porta **8081**.
2. A dependência **`spring-cloud-starter-openfeign`**: serve para chamar o `utentes` por HTTP (a validação do utente antes de aceitar um pedido, uma das duas chamadas síncronas do sistema).

As classes de configuração (`RelogioConfig`, `TraceIdFilter`, `ModelMapperConfig`, `JpaAuditingConfiguration`, `RabbitConfig`) são **iguais** às do molde. Só tem o `LandingController` e o seu teste.

**Ainda não tem:** endpoints (`POST /pedidos`, `GET /pedidos`, `PUT .../triagem`, `PUT .../prioridade`), modelo de dados, serviços nem eventos. Segundo a proposta, a `referenciacao` vai **publicar** `pedido.referenciado`, `pedido.triado`, `prioridade.reavaliada` e `pedido.devolvido`, e **consumir** `consulta.falta-registada`.

---

## 8. O que ainda não existe

| Serviço | Dono | Estado |
|---|---|---|
| `agendamento` | Nuno (Parte 1) | ❌ Sem módulo |
| `lista-espera` | Avelino (Parte 2) | ❌ Sem módulo |
| `utentes`, `notificacoes` | Pedro (Parte 4) | ❌ Sem módulo  |
| `garantia-acesso`, `historico` | Alton (Parte 3) | ❌ Por criar (a tua parte) |

---

## 9. O que levar daqui para a minha parte

| # | Ponto | O que faço |
|---|---|---|
| 1 | `PaginaDTO.de(page, conversor)` já existe | Usá-lo no `GET /incumprimentos` |
| 2 | O `ExceptionHandlers` **não trata um parâmetro obrigatório em falta** (**confirmado nos testes**: `GET /instituicoes` sem `esp` dava 500) | `required = false` e validar no serviço; avisar o grupo para acrescentar o handler ao molde |
| 3 | A Gateway passa o `X-Utilizador` ao serviço sem o alterar, "para guardar o id como autor" | O `autor` dos meus eventos pode usar esse id |
| 4 | Os `GET` precisam do cabeçalho `X-Utilizador` (exceto a landing); as 3 escritas do `garantia-acesso` exigem `GESTOR_ACESSO` | No Postman: `X-Utilizador: 1;GESTOR_ACESSO` |
| 5 | A base do `garantia-acesso` tem utilizador `garantia-acesso` e palavra-passe `Grupo5` | Ajustar o perfil `docker` (`DB_URL`, `DB_USER`, `DB_PASSWORD`) |
| 6 | O molde usa o perfil `dev` com H2 | Testes e arranque local **não precisam de Docker** |

O Git (hook `pre-push`, PR, CI, JDK 17) está em [`../detalhe.md`](../detalhe.md); as tarefas em [`../plano-de-trabalho.md`](../plano-de-trabalho.md).
