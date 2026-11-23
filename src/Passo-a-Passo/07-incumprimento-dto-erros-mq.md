# 07 — `garantia-acesso`: DTO, erros, ModelMapper e mensagens

> ⚠️ **v2.1 — Capítulo da versão antiga:** este capítulo mostra o código da `referencia/` (nomes antigos, `com.acessoatempo`). **A lógica é a mesma. Routing keys mudaram: `tmrg.risco-detetado` → `prazo.em-risco`; `tmrg.incumprido` → `prazo.ultrapassado`. Eventos publicados: `incumprimento.registado` → `caso.aberto`; `incumprimento.diretor-alertado` → `caso.diretor-alertado`; `incumprimento.resolvido` → `caso.resolvido`.** O **código correto para o repositório do grupo** está em `referencia-grupo/`. Ver `referencia-grupo/LEIA-ME.md` para as diferenças.

**Objetivo:** ter o contrato de entrada/saída (DTO), o formato de erro, o ModelMapper e a configuração do RabbitMQ.
**Baseado em:** aula 04 (DTO, `ModelMapper`, `@ControllerAdvice`) e aula 05 (`MQConfig`).

## 7.1 DTO

No package `dtos`. O `ErroDTO` é igual ao do `historico` (copia-o).

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/dtos/CasoDTO.java`**

```java
package com.acessoatempo.incumprimento.dtos;

import com.acessoatempo.incumprimento.enums.EstadoCaso;
import com.acessoatempo.incumprimento.enums.OrigemCaso;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Representação de um caso de incumprimento enviada ao cliente. Tem só o que o cliente precisa:
 * não expõe o campo técnico {@code versao} nem as datas de auditoria (RNF10: a entidade nunca sai do serviço).
 */
@Setter
@Getter
@NoArgsConstructor
public class CasoDTO {

    private Integer id;

    private Long episodioId;

    private String especialidade;

    private OrigemCaso origem;

    private EstadoCaso estado;

    /** 0 = em risco; 1, 2 ou 3 = nível de tratamento (R6). */
    private int nivel;

    private LocalDate dataDeteccao;

    /** Fim do prazo de 5 dias úteis do diretor (só a partir do nível 2). */
    private LocalDate dataLimiteDiretor;
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/dtos/TransferenciaDTO.java`**

```java
package com.acessoatempo.incumprimento.dtos;

import com.acessoatempo.incumprimento.enums.EstadoTransferencia;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Representação de uma proposta de transferência enviada ao cliente. */
@Setter
@Getter
@NoArgsConstructor
public class TransferenciaDTO {

    private Integer id;

    /** Vem de {@code transferencia.caso.id} (o ModelMapper faz a ligação pelo nome casoId). */
    private Integer casoId;

    private Integer instituicaoId;

    private String instituicaoNome;

    private EstadoTransferencia estado;

    private LocalDate dataProposta;

    private LocalDate respostaAte;

    private LocalDate dataResposta;
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/dtos/TransferenciaRequestDTO.java`**

```java
package com.acessoatempo.incumprimento.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Corpo de {@code POST /casos/{id}/transferencias}: a instituição a quem se propõe a consulta. */
@Setter
@Getter
@NoArgsConstructor
public class TransferenciaRequestDTO {

    /** {@code @NotNull}: se faltar, o Spring responde logo 400 (ver @Valid no controller). */
    @NotNull(message = "instituicaoId é obrigatório")
    private Integer instituicaoId;
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/dtos/RespostaTransferenciaDTO.java`**

```java
package com.acessoatempo.incumprimento.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Corpo de {@code PUT /transferencias/{id}/resposta}: a resposta da outra instituição. */
@Setter
@Getter
@NoArgsConstructor
public class RespostaTransferenciaDTO {

    /** true = a instituição aceitou a consulta; false = recusou. */
    @NotNull(message = "aceite é obrigatório (true ou false)")
    private Boolean aceite;
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/dtos/InstituicaoDTO.java`**

```java
package com.acessoatempo.incumprimento.dtos;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Instituição de destino devolvida por {@code GET /instituicoes}. */
@Setter
@Getter
@NoArgsConstructor
public class InstituicaoDTO {

    private Integer id;

    private String nome;

    private String especialidade;
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/dtos/PaginaDTO.java`**

```java
package com.acessoatempo.incumprimento.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Resposta paginada genérica (RNF12). Em vez de devolver o objeto {@code Page} do Spring (que traz muita
 * informação técnica e não tem formato JSON estável), devolve-se só o que o cliente precisa.
 *
 * @param <T> tipo dos elementos da página (aqui, {@code CasoDTO})
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaginaDTO<T> {

    private List<T> conteudo;

    /** Número da página, a começar em 0. */
    private int pagina;

    private int tamanho;

    private long totalElementos;

    private int totalPaginas;
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/dtos/ErroDTO.java`**

```java
package com.acessoatempo.incumprimento.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Formato de erro comum a todos os serviços (secção 4.4.1 da proposta). Se a Parte 4 entregar uma
 * classe comum, substitui esta cópia local.
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ErroDTO {

    /** Serviço onde o erro ocorreu (ajuda a seguir erros em cascata). */
    private String servico;

    /** Código HTTP (400, 404, 409...). */
    private int estado;

    /** Código estável do erro, por exemplo {@code PARAMETRO_INVALIDO}. */
    private String erro;

    /** Mensagem descritiva: o que falhou, porquê e o que fazer (RNF11). */
    private String mensagem;

    private String caminho;

    private LocalDateTime dataHora;

    /** Identificador gerado na Gateway para seguir o pedido nos logs de todos os serviços. */
    private String traceId;
}
```

**Para a defesa:**

- **`CasoDTO` não tem `versao`** (campo técnico do bloqueio otimista) nem as datas de auditoria: o cliente não precisa. O teste `getPorIdDevolveODto` verifica que `versao` **não** sai (RNF10).
- **`@NotNull` nos DTO de entrada** + **`@Valid`** no controller: se o corpo vier sem `instituicaoId` ou `aceite`, o Spring responde **400** sem chegar ao serviço.
- **`PaginaDTO<T>`** é uma classe **genérica**: serve para paginar qualquer DTO. Em vez de devolver o `Page` do Spring (formato técnico e instável em JSON), devolve só `conteudo`, `pagina`, `tamanho`, `totalElementos` e `totalPaginas`.
- **`TransferenciaDTO`** tem `casoId` e `instituicaoNome`, que **não existem** na entidade com esse nome (vêm de `caso.id` e `instituicao.nome`): ver 7.3.

## 7.2 Exceções e handler

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/exceptions/NegocioException.java`**

```java
package com.acessoatempo.incumprimento.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base das exceções de negócio que devem virar uma resposta HTTP. Cada exceção concreta diz qual o
 * código HTTP e o código de erro estável; o {@code ExceptionHandlers} faz o resto num só sítio.
 *
 * <p>Estende {@code RuntimeException} (como as exceções do {@code loan_proposals} da aula): não obriga
 * a declarar {@code throws} em todos os métodos.
 */
@Getter
public abstract class NegocioException extends RuntimeException {

    private final HttpStatus status;

    private final String codigo;

    protected NegocioException(HttpStatus status, String codigo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
    }
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/exceptions/CasoNaoEncontradoException.java`**

```java
package com.acessoatempo.incumprimento.exceptions;

import org.springframework.http.HttpStatus;

/** O caso pedido não existe: HTTP 404. */
public class CasoNaoEncontradoException extends NegocioException {

    public CasoNaoEncontradoException(Integer id) {
        super(HttpStatus.NOT_FOUND, "CASO_NAO_ENCONTRADO",
                "O caso de incumprimento " + id + " não existe. Consulte GET /incumprimentos para ver os casos.");
    }
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/exceptions/TransferenciaNaoEncontradaException.java`**

```java
package com.acessoatempo.incumprimento.exceptions;

import org.springframework.http.HttpStatus;

/** A transferência pedida não existe: HTTP 404. */
public class TransferenciaNaoEncontradaException extends NegocioException {

    public TransferenciaNaoEncontradaException(Integer id) {
        super(HttpStatus.NOT_FOUND, "TRANSFERENCIA_NAO_ENCONTRADA",
                "A transferência " + id + " não existe.");
    }
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/exceptions/InstituicaoNaoEncontradaException.java`**

```java
package com.acessoatempo.incumprimento.exceptions;

import org.springframework.http.HttpStatus;

/** A instituição de destino não existe: HTTP 404. */
public class InstituicaoNaoEncontradaException extends NegocioException {

    public InstituicaoNaoEncontradaException(Integer id) {
        super(HttpStatus.NOT_FOUND, "INSTITUICAO_NAO_ENCONTRADA",
                "A instituição " + id + " não existe. Consulte GET /instituicoes?esp=... para ver as disponíveis.");
    }
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/exceptions/TransicaoInvalidaException.java`**

```java
package com.acessoatempo.incumprimento.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Operação que viola uma regra de negócio (ex.: alertar o diretor de um caso já resolvido): HTTP 409 Conflict.
 * O pedido está bem formado, mas o estado atual do caso não o permite.
 */
public class TransicaoInvalidaException extends NegocioException {

    public TransicaoInvalidaException(String mensagem) {
        super(HttpStatus.CONFLICT, "TRANSICAO_INVALIDA", mensagem);
    }
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/exceptions/ParametroInvalidoException.java`**

```java
package com.acessoatempo.incumprimento.exceptions;

import org.springframework.http.HttpStatus;

/** Parâmetro do pedido em falta ou com valor inválido: HTTP 400. */
public class ParametroInvalidoException extends NegocioException {

    public ParametroInvalidoException(String mensagem) {
        super(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", mensagem);
    }
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/exceptions/EventoInvalidoException.java`**

```java
package com.acessoatempo.incumprimento.exceptions;

/**
 * Evento recebido com dados em falta ou mal formados. Não é um erro HTTP (ninguém está à espera de
 * resposta): a exceção sobe até ao listener, o RabbitMQ repete 3 vezes e, se continuar a falhar,
 * a mensagem vai para a Dead-Letter Queue em vez de bloquear a fila para sempre.
 */
public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/handlers/ExceptionHandlers.java`**

```java
package com.acessoatempo.incumprimento.handlers;

import com.acessoatempo.incumprimento.dtos.ErroDTO;
import com.acessoatempo.incumprimento.exceptions.NegocioException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;

/**
 * Tratamento global de exceções (aula {@code loan_proposals}: {@code @ControllerAdvice}).
 * Todas as respostas de erro do serviço saem no mesmo formato ({@link ErroDTO}).
 */
@ControllerAdvice
public class ExceptionHandlers {

    private static final Logger log = LoggerFactory.getLogger(ExceptionHandlers.class);

    /** Lê o nome do serviço do application.yml (spring.application.name). */
    @Value("${spring.application.name}")
    private String servico;

    /** Erros de negócio: o estado HTTP e o código vêm da própria exceção. */
    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ErroDTO> handleNegocio(NegocioException e, HttpServletRequest request) {
        return resposta(e.getStatus(), e.getCodigo(), e.getMessage(), request);
    }

    /** Parâmetro com tipo errado (ex.: semanas=abc) ou JSON mal formado: HTTP 400. */
    @ExceptionHandler({TypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErroDTO> handlePedidoInvalido(Exception e, HttpServletRequest request) {
        return resposta(HttpStatus.BAD_REQUEST, "PEDIDO_INVALIDO",
                "Os dados do pedido são inválidos ou estão mal formados. Verifique os parâmetros e o corpo enviado.",
                request);
    }

    /**
     * Qualquer outra exceção. As exceções do próprio Spring MVC (parâmetro em falta, URL inexistente,
     * método não permitido...) implementam {@code ErrorResponse} e já trazem o código HTTP certo.
     * O resto é um erro inesperado: HTTP 500 com mensagem genérica; o detalhe fica só no log.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroDTO> handleGenerica(Exception e, HttpServletRequest request) {
        if (e instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            return resposta(status, "PEDIDO_INVALIDO", er.getBody().getDetail(), request);
        }
        log.error("Erro inesperado em {}", request.getRequestURI(), e);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO",
                "Ocorreu um erro inesperado. Indique o traceId a quem der suporte.", request);
    }

    /** Constrói o corpo de erro comum. O traceId vem do cabeçalho que a Gateway acrescenta. */
    private ResponseEntity<ErroDTO> resposta(HttpStatus status, String codigo, String mensagem,
                                             HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        ErroDTO erro = new ErroDTO(servico, status.value(), codigo, mensagem,
                request.getRequestURI(), LocalDateTime.now(), traceId != null ? traceId : "n/a");
        return ResponseEntity.status(status).body(erro);
    }
}
```

**Os códigos HTTP deste serviço (e porquê):**

| Código | Quando | Exceção |
|---|---|---|
| **400** Bad Request | Parâmetro inválido (`size=500`, `nivel=9`), JSON mal formado, campo obrigatório em falta | `ParametroInvalidoException`, `@Valid` |
| **404** Not Found | Caso, transferência ou instituição inexistente | `*NaoEncontradaException` |
| **409** Conflict | **Regra de negócio violada**: p. ex. alertar o diretor de um caso já resolvido, ou propor transferência antes dos 5 dias úteis | `TransicaoInvalidaException` |
| **202** Accepted | Transferência proposta: **o processo continua por eventos** (não está concluído) | (resposta de sucesso) |
| **500** | Erro inesperado (mensagem genérica; detalhe no log) | handler geral |

> 💡 **Defesa — 409 vs 400:** o **400** significa "o pedido está mal feito"; o **409** significa "o pedido está bem feito mas o **estado atual** do caso não o permite" (secção 4.4.1 da proposta).

## 7.3 ModelMapper (configurado uma vez)

Nas aulas, cada controller fazia `new ModelMapper()` no construtor. Aqui há **3 controllers** com a mesma configuração, por isso define-se uma vez como `@Bean` (no package `config`) e injeta-se com `@Autowired`.

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/config/ModelMapperConfig.java`**

```java
package com.acessoatempo.incumprimento.config;

import com.acessoatempo.incumprimento.dtos.TransferenciaDTO;
import com.acessoatempo.incumprimento.models.Transferencia;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cria UM ModelMapper configurado para todo o serviço. Nas aulas fazia-se {@code new ModelMapper()} no
 * construtor de cada controller; aqui há três controllers com a mesma configuração, por isso define-se uma
 * vez como {@code @Bean} e injeta-se com {@code @Autowired}.
 */
@Configuration
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();

        // STRICT: só liga campos com o mesmo nome. Evita ligações erradas por ambiguidade
        // (ex.: o "estado" do DTO podia ser o estado da transferência OU o estado do caso).
        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);

        // Campos do DTO que vêm de objetos ligados (caso e instituição) têm de ser indicados explicitamente.
        modelMapper.typeMap(Transferencia.class, TransferenciaDTO.class).addMappings(mapper -> {
            mapper.map(origem -> origem.getCaso().getId(), TransferenciaDTO::setCasoId);
            mapper.map(origem -> origem.getInstituicao().getId(), TransferenciaDTO::setInstituicaoId);
            mapper.map(origem -> origem.getInstituicao().getNome(), TransferenciaDTO::setInstituicaoNome);
        });
        return modelMapper;
    }
}
```

**Porque `STRICT` e o mapeamento explícito:** com a estratégia por omissão, o campo `estado` do DTO podia corresponder ao `estado` da transferência **ou** ao `caso.estado` (ambíguo). A estratégia **`STRICT`** só liga campos com o **mesmo nome**; mas então o `casoId` já não é ligado automaticamente a `caso.id` e é preciso dizê-lo no `typeMap(...).addMappings(...)`. (Esta foi uma falha real encontrada nos testes: o `casoId` saía `null`.)

## 7.4 Mensagens e RabbitMQ

`EventoMessage` é a **mesma** classe do `historico` (copia-a; só muda o package). `MQConfig` é semelhante, mas com a fila `incumprimento.eventos` e **5 bindings** (os eventos que o serviço consome, Anexo A.5).

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/messages/EventoMessage.java`**

```java
package com.acessoatempo.incumprimento.messages;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Mensagem (evento) trocada através do RabbitMQ. Corresponde à classe {@code OrderConfirmationMessage}
 * da aula de RabbitMQ: é aquilo que o Producer envia e o Consumer recebe, convertido para JSON.
 *
 * <p>Regra do projeto: os eventos levam <b>só identificadores e datas</b>, nunca dados pessoais.
 *
 * <p>ATENÇÃO: este "envelope" tem de ser igual em todos os serviços. Se a Parte 4 já entregou o
 * envelope comum, usa o dela; esta classe é a cópia local usada até lá.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class EventoMessage {

    /** Identificador único do evento (UUID). Serve para o consumidor ignorar repetições (idempotência). */
    private String eventoId;

    /** Tipo do evento = routing key, por exemplo {@code episodio.inscrito}. */
    private String tipo;

    /** Identificador do episódio de espera a que o evento diz respeito. */
    private Long episodioId;

    /** Momento em que o facto aconteceu. */
    private LocalDateTime ocorridoEm;

    /** Dados específicos de cada tipo de evento (especialidade, datas...). Nunca dados pessoais. */
    private Map<String, Object> dados = new HashMap<>();
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/config/MQConfig.java`**

```java
package com.acessoatempo.incumprimento.config;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuração do RabbitMQ do serviço {@code incumprimento} (equivale à classe {@code MQConfig} da aula).
 *
 * <p>Este serviço é Producer E Consumer: publica 6 eventos na exchange do sistema e consome 5.
 * Aqui declara-se a exchange, a fila deste serviço, os bindings dos eventos que consome e a
 * Dead-Letter Exchange/Queue.
 */
@Configuration
public class MQConfig {

    /** Exchange comum a todo o sistema (TopicExchange). Todos os serviços publicam e ouvem nesta. */
    public static final String EXCHANGE = "acesso.eventos";

    /** Dead-Letter Exchange: recebe as mensagens que falharam 3 vezes. */
    public static final String DLX = "acesso.eventos.dlx";

    /** Fila deste serviço (uma fila por SERVIÇO, não por réplica). */
    public static final String QUEUE = "incumprimento.eventos";

    /** Fila das mensagens mortas deste serviço. */
    public static final String DLQ = "incumprimento.eventos.dlq";

    /** Os 5 eventos que o serviço consome (Anexo A.5 da proposta). */
    public static final String[] ROUTING_KEYS = {
            "tmrg.risco-detetado",
            "tmrg.incumprimento-previsto",
            "tmrg.incumprido",
            "consulta.agendada",
            "relogio.avancado"
    };

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX);
    }

    /** Fila principal: mensagens rejeitadas vão para a DLX com a routing key = nome da DLQ. */
    @Bean
    public Queue queue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Declarable deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DLQ);
    }

    /** Um binding por routing key: a fila só recebe os eventos que interessam a este serviço. */
    @Bean
    public Declarables eventBindings() {
        List<Declarable> bindings = new ArrayList<>();
        for (String key : ROUTING_KEYS) {
            bindings.add(BindingBuilder.bind(queue()).to(exchange()).with(key));
        }
        return new Declarables(bindings);
    }

    /** Converte objetos Java em JSON e vice-versa (Spring Boot 3.5 = Jackson 3). */
    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
```

Este serviço é **Producer e Consumer**:

| Consome (5) | Publica (6) |
|---|---|
| `prazo.em-risco` | `caso.aberto` |
| `prazo.ultrapassado-previsto` | `caso.diretor-alertado` |
| `prazo.ultrapassado` | `transferencia.proposta` |
| `consulta.agendada` | `transferencia.aceite` |
| `relogio.avancado` | `transferencia.recusada` |
| | `caso.resolvido` |

Os 6 que **publica** **não** precisam de binding **neste** serviço: publica-se para a exchange `acesso.eventos` com a routing key do evento, e quem os quiser (o `historico`, o `agendamento`, as `notificacoes`) liga a sua própria fila a essa routing key.

## ✅ Checklist do capítulo 07

- [ ] O módulo compila (7 DTO, 7 exceções, handler, `ModelMapperConfig`, `EventoMessage`, `MQConfig`)
- [ ] (A fila `incumprimento.eventos`, a DLQ e os 5 bindings só aparecem no RabbitMQ no capítulo 08, quando existir o listener que abre a ligação.)
- [ ] Sabes explicar: 400 vs 404 vs 409 vs 202, `@Valid`, DTO sem campos técnicos, `ModelMapper STRICT`
