# Passo-a-Passo da Parte 3 — `garantia-acesso` e `historico`

> **Versão 2 — alinhada com o Runbook v2.1 (19/11/2026).** Nomes anteriores: `incumprimento` → `garantia-acesso`; `indicadores` → `historico`.

> Guia para **programares à mão** a tua parte do projeto *Acesso a Tempo* (Grupo 5), no mesmo estilo dos
> exercícios das aulas `2526-IPP-ESTG-MEI-MTSDS-02` a `06`, e para **saberes defender** cada linha.

## O que vais construir

| Serviço | Porta | O que faz | Endpoints | Eventos |
|---|---|---|---|---|
| **`historico`** | 8086 | Log append-only de todos os eventos do sistema; fonte de verdade para auditoria (Fase 1) e indicadores (Fase 2) | 3 (+ landing) | consome **todos** (binding `#`), publica 0 |
| **`garantia-acesso`** | 8085 | Trata cada incumprimento em 3 níveis (R6) e orquestra a transferência para outra instituição | 6 (+ landing) | consome 5, publica 6 |

Trabalho transversal da Parte 3: **orquestração da transferência** (nível 3, com compensação).
(Os números vêm da proposta, secção 5.2 e Anexos A.5, A.6 e B.)

## Como usar este guia

1. Segue os capítulos **por ordem**. Cada capítulo termina num ponto em que **o projeto compila e podes testar**.
2. Em cada passo: **escreve o código tu próprio** (não copies às cegas). Lê "O que faz" e "Para a defesa" antes de avançar.
3. Todo o código tem **documentação (Javadoc/comentários)**; escreve-a também: é o que te obriga a perceber.
4. Se algo não compilar, compara com a pasta [`referencia/`](referencia/) (projeto completo, **testado**).

| # | Capítulo | No fim consegues |
|---|---|---|
| 01 | [Preparação: projeto multi-módulo e infraestrutura](01-preparacao.md) | Ter o pom pai, os 2 módulos, PostgreSQL e RabbitMQ a correr |
| 02 | [`historico`: esqueleto](02-indicadores-esqueleto.md) | Arrancar o serviço e responder em `/historico/landing` |
| 03 | [`historico`: dados, DTO e erros](03-indicadores-dados.md) | Ter a tabela `evento_historico` criada no PostgreSQL |
| 04 | [`historico`: eventos (RabbitMQ)](04-indicadores-eventos.md) | Consumir todos os eventos (binding `#`) e guardar cada um |
| 05 | [`historico`: API e testes](05-indicadores-api-testes.md) | Ter os 3 endpoints `/historico/*` e os testes a passar |
| 06 | [`garantia-acesso`: esqueleto e dados](06-incumprimento-esqueleto-dados.md) | Serviço a arrancar com as tabelas criadas |
| 07 | [`garantia-acesso`: DTO, erros e mensagens](07-incumprimento-dto-erros-mq.md) | Ter o contrato dos eventos e dos erros |
| 08 | [`garantia-acesso`: as regras dos 3 níveis](08-incumprimento-regras.md) | Ter toda a lógica (relógio, dias úteis, eventos, transferência) |
| 09 | [`garantia-acesso`: API e testes](09-incumprimento-api-testes.md) | Ter os 6 endpoints e 26 testes a passar |
| 10 | [Juntar tudo: Docker, Eureka, Gateway e teste de ponta a ponta](10-integracao-ponta-a-ponta.md) | Ver o fluxo completo a funcionar |
| 11 | [Preparar a defesa](11-defesa.md) | Responder às perguntas prováveis |
| 12 | [Kubernetes e Minikube](12-kubernetes-minikube.md) | Ter os 2 serviços a correr no cluster (Deployment, ConfigMap, Secret, sondas) |

## Mapa dos documentos (cada informação está num só sítio)

| Pergunta | Ficheiro |
|---|---|
| **O que é o negócio da minha parte?** (regras, 3 níveis, exemplos, perguntas de defesa) | [`contexto.md`](contexto.md) |
| **O que é meu?** (requisitos, endpoints, eventos e contrato, dados, o que não é meu, decisões) | [`minha-parte.md`](minha-parte.md) |
| **Como e quando o faço?** (tarefas independentes/dependentes, branches, commits, plano por dias, integração com o Avelino) | [`plano-de-trabalho.md`](plano-de-trabalho.md) |
| **Como uso o Git e o POM do grupo?** | [`detalhe.md`](detalhe.md) |
| **Que código escrevo?** (no estilo do grupo, testado com o `verify` do grupo) | [`referencia-grupo/`](referencia-grupo/LEIA-ME.md) |
| **O que faz o código dos colegas?** (`gestao-acesso`, Gateway, Eureka, molde) e **bate certo comigo?** | [`referencia/`](referencia/LEIA-ME.md) |
| **Porquê cada ficheiro?** | Capítulos 01 a 12 (abaixo) |

**Ordem de leitura sugerida:** `contexto.md` → `minha-parte.md` → `plano-de-trabalho.md` → capítulo do guia + `referencia-grupo/` a cada tarefa.

> ⚠️ **Os capítulos 02 a 09 mostram o código da versão v1** (`referencia/`, Spring Boot 3.5, `com.acessoatempo`, nomes antigos `incumprimento`/`indicadores`). **A lógica do `garantia-acesso` é a mesma; o `historico` foi redesenhado** (ver `contexto.md`, secção 8). Para escrever no repositório do grupo, **o código a seguir é o de `referencia-grupo/`** (Boot 3.5.16, `pt.estg.mtsds.acesso`, nomes v2.1); as diferenças estão no `LEIA-ME` dessa pasta.

## Convenções usadas (as mesmas das aulas)

- **Camadas:** `controllers` → `services` → `data` (repositórios JPA), mais `models`, `dtos`, `exceptions`, `handlers`,
  `messages`, `listeners`, `config`, `auditing`, `enums` (aula 03 e 04).
- **Lombok** (`@Data`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`), **SLF4J + Logback** para logs,
  **ModelMapper** para DTO ↔ entidade, **Spring Data JPA** com PostgreSQL, **RabbitMQ** com `@RabbitListener`.
- **Injeção com `@Autowired`** nos campos (como nas aulas). Só a classe `DiasUteisService` usa injeção por construtor,
  para ser fácil de testar.
- Nomes de código em português, porque os termos do domínio (episódio, data-limite, incumprimento) são do negócio.

## Versões (do grupo, Runbook v2.1 secção 14)

| Tecnologia | Versão | Nota |
|---|---|---|
| Java | **17** (`maven.compiler.release=17`) | Podes ter um JDK mais recente instalado; o código compila para 17 |
| Spring Boot | **3.5.16** | As aulas usam 3.1.x; ver "O que mudou" abaixo |
| Spring Cloud | **2025.0.3** | Série *2025.0 (Northfields)* — suporta Spring Boot 3.5 |
| PostgreSQL | 17 | Imagem Docker `postgres:17` |
| RabbitMQ | 3.12.9 (imagem da aula 05) | `rabbitmq:3.12.9-management` |
| ModelMapper / Lombok | 3.2.8 / 1.18.48 | |

> ⚠️ **Versões definidas pelo grupo no Runbook v2.1** — não alteres sem combinar com a equipa.

## O que mudou face às aulas (Spring Boot 3.1 → 3.5)

As aulas foram dadas com Spring Boot 3.1. Com Spring Boot 3.5 mudam alguns nomes; o guia já usa os novos e assinala-os:

| Aulas (Boot 3.1) | Neste guia (Boot 3.5) |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-web` (igual; `webmvc` era Boot 4) |
| `Jackson2JsonMessageConverter` | `Jackson2JsonMessageConverter(objectMapper)` — passa o ObjectMapper explicitamente |
| `@MockBean` | `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`) a partir da 3.4 |
| `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` | igual (mantém-se em Boot 3.x) |
| nomes dos parâmetros descobertos automaticamente | é preciso compilar com `-parameters` (ou escrever `@PathVariable("id")`) |

## O que foi verificado

O código deste guia foi **compilado e testado**: 38 testes automáticos (12 + 26) a passar e um teste de ponta a ponta com
PostgreSQL e RabbitMQ reais (eventos publicados, consumidos, repetidos, inválidos para a Dead-Letter, e a escalada
completa dos 3 níveis). O capítulo 12 (Kubernetes) foi também executado num cluster Minikube real. **Não foi testado:** Eureka, Gateway e a VM da entrega (dependem do trabalho dos colegas).

## Decisões a fechar com o grupo

O que a proposta não define (identificador do episódio, formato de `especialidade`, `relogio.avancado`, Service no Kubernetes...) está em [`minha-parte.md`](minha-parte.md), secção 9, com o estado de cada uma. O **envelope** dos eventos já está decidido: é o `MensagemBase` do grupo (`eventoId`, `traceId`, `autor`, `dataHoraEvento`), com os campos do evento ao mesmo nível.
