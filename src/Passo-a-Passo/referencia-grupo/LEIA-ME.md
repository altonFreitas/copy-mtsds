# `referencia-grupo/`: os dois módulos no estilo do repositório do grupo

**Versão 2 — alinhada com o Runbook v2.1 (19/11/2026).**

| Pasta aqui | Módulo no projeto real | Porta |
|---|---|---|
| `incumprimento/` | `garantia-acesso` | 8085 |
| `indicadores/` | `historico` | 8086 |

> **Ao criar os módulos no projeto real, usa os nomes corretos** (`garantia-acesso` e `historico`) — não copies as pastas com os nomes antigos.

Código **testado** com o toolchain do grupo: **JDK 17, Spring Boot 3.5.16, enforcer e Spotless**. A pasta `incumprimento/` (garantia-acesso) tem 37 testes; a `indicadores/` (historico) foi redesenhada para o novo modelo append-only e os testes antigos dos contadores deixaram de se aplicar. O repositório original **não foi tocado**.

## Como usar

1. Cria os módulos no IntelliJ a partir do `servico-modelo` (tarefas T1 e T2 de `../plano-de-trabalho.md`).
2. Escreve os ficheiros **por tarefa**, a partir daqui (`<modulo>/src/main/java/pt/estg/mtsds/acesso/<modulo>/...`).
3. Os `pom.xml` destas pastas só mudam `artifactId`, `name` e `description` face ao molde.
4. No `pom.xml` da raiz: `<module>incumprimento</module>` e `<module>indicadores</module>`.

## O que veio do molde (não escrevas, só copia/renomeia)

`IncumprimentoApplication`/`IndicadoresApplication`, `auditing/`, `configuration/` (`RelogioConfig`, `TraceIdFilter`, `ModelMapperConfig` no
`indicadores`), `controllers/LandingController`, `dtos/ErroDTO`, `ErroCampoDTO`, `PaginaDTO`, `exceptions/` (6), `handlers/ExceptionHandlers`,
`messages/MensagemBase` e `RoutingKeys`, e o `LandingControllerTest`.

## O que é teu (a escrever)

| Módulo (pasta) | Módulo real | Pacote | Ficheiros a escrever |
|---|---|---|---|
| `indicadores/` | **`historico`** | `models` | `EventoHistorico` (**novo**: substituiu `IndicadorSemanal`) |
| | | `data` | `EventoHistoricoRepository` (**novo**) |
| | | `dtos` | `EventoHistoricoDTO` (**novo**) |
| | | `exceptions` | `EventoInvalidoException` |
| | | `messages` | `EventoMessage` |
| | | `configuration` | `RabbitConfig` (fila `historico.eventos`, binding `#`, Dead-Letter) |
| | | `services` | `HistoricoService` (**novo**: append-only; substitui `IndicadoresService`) |
| | | `listeners` | `EventosListener` (atualizado: chama `HistoricoService.guardar`) |
| | | `controllers` | `HistoricoController` (**novo**: 3 endpoints `/historico/*`) |
| | | `application.yml` | `spring.application.name: historico` |
| `incumprimento/` | **`garantia-acesso`** | **`enums`** | `EstadoCaso`, `EstadoTransferencia`, `OrigemCaso` |
| | | `models` | `CasoIncumprimento`, `Transferencia`, `Instituicao`, `EventoProcessado` |
| | | `data` | `CasosRepository`, `TransferenciasRepository`, `InstituicoesRepository`, `EventosProcessadosRepository` |
| | | `dtos` | `CasoDTO`, `TransferenciaDTO`, `TransferenciaRequestDTO`, `RespostaTransferenciaDTO`, `InstituicaoDTO` |
| | | `exceptions` | `EventoInvalidoException` |
| | | `messages` | `EventoMessage`, `RoutingKeys` (atualizado: `prazo.*`, `caso.*`) |
| | | `configuration` | `RabbitConfig` (fila `garantia-acesso.eventos`, 5 bindings: `prazo.*`, `consulta.agendada`, `relogio.avancado`), `ModelMapperConfig`, `DadosIniciais` |
| | | `services` | `DiasUteisService`, `RelogioService`, `EventosPublisher`, `CasosService` (atualizado: routing keys `prazo.*` e `caso.*`), `TransferenciasScheduler` |
| | | `listeners` | `EventosListener` |
| | | `controllers` | `CasosController`, `TransferenciasController`, `InstituicoesController` |
| | | `application.yml` | `spring.application.name: garantia-acesso`; feriados e `prazo-resposta-dias` |

## Ficheiros antigos (obsoletos com o Runbook v2.1)

Na pasta `indicadores/`: `IndicadorSemanal.java`, `IndicadorSemanalRepository.java`, `CumprimentoProjection.java`, `IndicadoresService.java`, `IndicadoresController.java`, `ListaIndicadorDTO.java`, `CumprimentoDTO.java`, `TriagemDTO.java`, `ProjecaoDTO.java`. Mantêm-se em disco para referência histórica, mas **não os copias para o projeto real** — foram substituídos pelos ficheiros acima.

## Diferenças face a `../referencia/` (o que mudou para o estilo do grupo)

| Tema | Agora |
|---|---|
| Spring Boot | 3.5.16 (`Jackson2JsonMessageConverter(objectMapper)`, `AutoConfigureMockMvc` em `org.springframework.boot.test.autoconfigure.web.servlet`) |
| Pacote | `pt.estg.mtsds.acesso.<servico>`; `config` → `configuration`; `MQConfig` → `RabbitConfig` |
| Injeção | `@RequiredArgsConstructor` com campos `final` (os testes mantêm `@Autowired`) |
| Entidades | `@Getter @Setter` (já não `@Data`) |
| Enums | pacote `enums` próprio, **não** dentro de `models` |
| `EventoMessage` | estende `MensagemBase` (`@SuperBuilder`); sem os campos do envelope |
| Exceções | as do molde: `NaoEncontradoException(codigo, msg)`, `DadosInvalidosException("PARAMETRO_INVALIDO", msg)`, `TransicaoInvalidaException` |
| Paginação | `PaginaDTO.de(pagina, conversor)` |
| Parâmetros obrigatórios | `@RequestParam(required = false)` + validação no serviço (o handler do molde não trata `MissingServletRequestParameterException`: **o teste deu 500** em `GET /instituicoes` sem `esp`) |

## Pontos a saber

- **`historico` é append-only:** não há `UPDATE` nem `DELETE`. A idempotência é garantida pela PK (`eventoId`): `existsById` antes de `save`. O campo `conteudo` guarda o JSON completo do evento.
- **`garantia-acesso` routing keys v2.1:** `prazo.em-risco` (antes `tmrg.risco-detetado`), `prazo.ultrapassado-previsto` (antes `tmrg.incumprimento-previsto`), `prazo.ultrapassado` (antes `tmrg.incumprido`). Os eventos publicados: `caso.aberto` (antes `incumprimento.registado`), `caso.diretor-alertado` (antes `incumprimento.diretor-alertado`), `caso.resolvido` (antes `incumprimento.resolvido`).
- **DTO como classes com Lombok, não `record`:** o ModelMapper em modo `STRICT` mapeia por *setters*. O `EventoHistoricoDTO` do `historico` é `record` porque não usa ModelMapper.
- **Formatação:** o Spotless do grupo já formatou estes ficheiros (tabulações).
- **Testes sem RabbitMQ:** aparece um `ERROR ... Failed to check/redeclare auto-delete queue(s)` no log; é só o listener a tentar ligar-se (não há broker nos testes) e não falha nada.
- **Não testado aqui:** arranque com PostgreSQL/RabbitMQ reais e Eureka/Gateway (isso foi feito na `../referencia/`, ver capítulo 10).
