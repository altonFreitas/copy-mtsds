# 03 — `historico`: dados, DTO e erros

> ⚠️ **v2.1 — Capítulo da versão antiga:** este capítulo mostra o código da `referencia/` (nomes antigos, `com.acessoatempo`). **O modelo de dados mudou completamente: em vez de `IndicadorSemanal` (contadores por semana), agora é `EventoHistorico` (uma linha por evento, append-only). Segue `referencia-grupo/indicadores/` para o novo modelo.** O **código correto para o repositório do grupo** está em `referencia-grupo/`. Ver `referencia-grupo/LEIA-ME.md` para as diferenças.

**Objetivo:** criar o modelo de dados (tabelas), os repositórios, os DTO e o tratamento global de erros.
**Baseado em:** aula 03 (`@Entity`, `JpaRepository`, `@Query`) e aula 04 (DTO, exceções, `@ControllerAdvice` do `loan_proposals`).

## 3.1 A ideia do serviço

O `historico` **não guarda episódios nem utentes**. Guarda só **contadores por especialidade e semana**, atualizados quando chegam eventos
(secção 4.5: *"os indicadores vão consumir 17 eventos e não vão publicar nenhum"*; RNF03: *"indicadores só com dados agregados"*).
Isto é um **modelo de leitura**: consultas pesadas aqui não atrasam as marcações (secção 4.2).

| Evento recebido | Contador que aumenta |
|---|---|
| `episodio.inscrito` | `entradas` |
| `episodio.saida` | `saidas` |
| `prazo.ultrapassado` | `incumpridos` |
| `pedido.triado` | `triagens` e `diasTriagemTotal` |
| os restantes 13 | nenhum (por agora; Fase 2) |

## 3.2 Modelos (entidades JPA)

Cria no package `models`. Anotações de JPA da aula 03: `@Entity`, `@Table`, `@Id`, `@GeneratedValue`, `@Column`.

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/models/IndicadorSemanal.java`**

```java
package com.acessoatempo.indicadores.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Modelo de leitura do serviço: uma linha por (especialidade, semana) com contadores agregados.
 *
 * <p>O serviço NÃO guarda episódios nem utentes: só contagens (princípio "indicadores só com dados
 * agregados", RNF03). Cada evento recebido incrementa um ou mais contadores.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "indicadores_semanais",
        uniqueConstraints = @UniqueConstraint(columnNames = {"especialidade", "semana"}))
public class IndicadorSemanal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Especialidade hospitalar (ex.: CARDIOLOGIA). */
    @Column(nullable = false)
    private String especialidade;

    /** Semana ISO no formato {@code 2026-W47}. */
    @Column(nullable = false, length = 8)
    private String semana;

    /** Episódios que entraram na lista nessa semana (evento episodio.inscrito). */
    @Column(nullable = false)
    private long entradas;

    /** Episódios que saíram da lista nessa semana (evento episodio.saida). */
    @Column(nullable = false)
    private long saidas;

    /** Episódios que ultrapassaram a data-limite (evento tmrg.incumprido). */
    @Column(nullable = false)
    private long incumpridos;

    /** Número de triagens feitas (evento pedido.triado). */
    @Column(nullable = false)
    private long triagens;

    /** Soma dos dias entre o pedido e a triagem; dividida por {@code triagens} dá o tempo médio. */
    @Column(nullable = false)
    private long diasTriagemTotal;

    /** Construtor de conveniência: linha nova, com todos os contadores a zero. */
    public IndicadorSemanal(String especialidade, String semana) {
        this.especialidade = especialidade;
        this.semana = semana;
    }
}
```

- **`@Entity` + `@Table(name=...)`** — esta classe passa a ser a tabela `indicadores_semanais`.
- **`@UniqueConstraint(especialidade, semana)`** — só pode haver **uma linha** por especialidade+semana; é a garantia de que os contadores não se duplicam.
- **`@GeneratedValue(IDENTITY)`** — o PostgreSQL gera o `id` automaticamente.
- **`@Data`** (Lombok) gera getters, setters, `equals`, `hashCode` e `toString`; **`@NoArgsConstructor`** é obrigatório para o JPA.

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/models/EventoProcessado.java`**

```java
package com.acessoatempo.indicadores.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Registo dos eventos já processados. É o que torna o consumidor <b>idempotente</b>: o RabbitMQ garante
 * entrega "pelo menos uma vez", por isso o mesmo evento pode chegar duas vezes. Se o {@code eventoId}
 * já estiver aqui, o evento é ignorado.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "eventos_processados")
public class EventoProcessado {

    /** O próprio identificador do evento é a chave primária (não pode haver dois iguais). */
    @Id
    private String eventoId;

    private String tipo;

    private LocalDateTime processadoEm;
}
```

> 💡 **Defesa — idempotência:** o RabbitMQ garante entrega **"pelo menos uma vez"**: o mesmo evento pode chegar duas vezes. Guardar o `eventoId` já processado (como chave primária) permite **ignorar repetições**. É o que a proposta pede na secção 4.3 (*"cada consumidor vai guardar o identificador dos eventos já processados"*).

## 3.3 Repositórios

No package `data`. Ao estender `JpaRepository<Entidade, TipoDoId>` ficamos com `save`, `findById`, `findAll`... sem escrever SQL (aula 03).

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/data/IndicadoresRepository.java`**

```java
package com.acessoatempo.indicadores.data;

import com.acessoatempo.indicadores.models.IndicadorSemanal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repositório JPA dos indicadores. Ao estender {@code JpaRepository} ficamos logo com
 * save, findById, findAll, delete... sem escrever SQL (aula 03).
 */
public interface IndicadoresRepository extends JpaRepository<IndicadorSemanal, Integer> {

    /** Query derivada do NOME do método: o JPA deduz o SQL a partir de "findBy" + campos. */
    Optional<IndicadorSemanal> findByEspecialidadeAndSemana(String especialidade, String semana);

    /** Todas as semanas de uma especialidade (para somar, calcular médias e projeções). */
    List<IndicadorSemanal> findByEspecialidade(String especialidade);

    /**
     * Query explícita em JPQL (aula 03: {@code @Query}). Agrega por especialidade todas as semanas
     * cujo identificador começa por {@code periodo} (ex.: "2026" apanha 2026-W01 ... 2026-W53).
     */
    @Query("select i.especialidade as especialidade, sum(i.entradas) as entradas, "
            + "sum(i.incumpridos) as incumpridos from IndicadorSemanal i "
            + "where i.semana like concat(:periodo, '%') group by i.especialidade order by i.especialidade")
    List<CumprimentoProjection> cumprimentoPorEspecialidade(@Param("periodo") String periodo);
}
```

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/data/CumprimentoProjection.java`**

```java
package com.acessoatempo.indicadores.data;

/**
 * "Projeção" Spring Data: interface cujos getters têm o mesmo nome dos aliases da query
 * ({@code as especialidade}, {@code as entradas}...). O Spring preenche-a automaticamente,
 * sem precisarmos de criar uma classe para um resultado que não é uma entidade.
 */
public interface CumprimentoProjection {

    String getEspecialidade();

    Long getEntradas();

    Long getIncumpridos();
}
```

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/data/EventosProcessadosRepository.java`**

```java
package com.acessoatempo.indicadores.data;

import com.acessoatempo.indicadores.models.EventoProcessado;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório dos eventos já processados. Só usamos {@code existsById} e {@code save},
 * que já vêm do {@code JpaRepository}.
 */
public interface EventosProcessadosRepository extends JpaRepository<EventoProcessado, String> {
}
```

- **Query derivada do nome** — `findByEspecialidadeAndSemana(...)`: o Spring Data lê o nome do método e gera o SQL (aula 03, `StudentsRepository`).
- **`@Query` com JPQL** — para o cumprimento precisamos de somar (`sum`) e agrupar (`group by`), que não se consegue só com o nome. JPQL usa **nomes de classes/campos Java**, não de tabelas (aula 03, tabela de annotations).
- **Projeção (`CumprimentoProjection`)** — uma interface com getters do mesmo nome dos aliases (`as entradas`). O Spring preenche-a; evita criar uma classe só para um resultado agregado.

## 3.4 DTO (Data Transfer Objects)

No package `dtos`. Os DTO existem para **nunca expor a entidade** (RNF10) e devolver só o que o cliente precisa (aula 04, *customers 4/12*).

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/dtos/ListaIndicadorDTO.java`**

```java
package com.acessoatempo.indicadores.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resposta de {@code GET /historico/episodios/{id}}: tamanho da lista e movimento (entradas/saídas).
 * É um DTO: só tem o que o cliente precisa, nunca a entidade JPA (RNF10).
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ListaIndicadorDTO {

    private String especialidade;

    /** Semana pedida (ex.: 2026-W47) ou {@code null} se o pedido foi sobre todo o período. */
    private String semana;

    private long entradas;

    private long saidas;

    /** Tamanho da lista = entradas - saídas (acumulado até à semana pedida). */
    private long tamanhoLista;
}
```

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/dtos/CumprimentoDTO.java`**

```java
package com.acessoatempo.indicadores.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Resposta de {@code GET /historico/eventos?tipo=prazo.ultrapassado}: taxa de cumprimento do TMRG por especialidade. */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CumprimentoDTO {

    private String especialidade;

    private long entradas;

    private long incumpridos;

    /** Percentagem de episódios que NÃO ultrapassaram a data-limite (0 a 100). */
    private double taxaCumprimento;
}
```

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/dtos/TriagemDTO.java`**

```java
package com.acessoatempo.indicadores.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Resposta de {@code GET /historico/eventos?tipo=pedido.triado}: tempo médio entre o pedido e a triagem. */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TriagemDTO {

    private String especialidade;

    private long triagens;

    /** Média de dias entre o pedido e a triagem (a triagem tardia come o prazo, R1/R7). */
    private double mediaDiasTriagem;
}
```

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/dtos/ProjecaoDTO.java`**

```java
package com.acessoatempo.indicadores.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Resposta de {@code GET /historico/eventos}: previsão do tamanho da lista. */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProjecaoDTO {

    private String especialidade;

    private long tamanhoAtual;

    private double mediaEntradasSemana;

    private double mediaSaidasSemana;

    /** Quantas semanas à frente se projeta. */
    private int semanas;

    /** Tamanho previsto = atual + semanas x (média de entradas - média de saídas). Nunca negativo. */
    private double tamanhoProjetado;
}
```

> Nota: nas aulas usa-se o **ModelMapper** para converter entidade ↔ DTO. Aqui os DTO são **resultados calculados** (somas, médias), não cópias de uma entidade, por isso são construídos diretamente no serviço. O ModelMapper aparece no `garantia-acesso` (capítulo 07 e 09).

## 3.5 Formato de erro comum

Todos os serviços devolvem os erros no **mesmo formato** (secção 4.4.1):

```json
{ "servico": "indicadores", "estado": 400, "erro": "PARAMETRO_INVALIDO", "mensagem": "...", "caminho": "/historico/episodios/{id}", "dataHora": "...", "traceId": "..." }
```

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/dtos/ErroDTO.java`**

```java
package com.acessoatempo.indicadores.dtos;

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

## 3.6 Exceções

No package `exceptions`. Estendem `RuntimeException` (como as do `loan_proposals`, aula 04, *4/14*): não obrigam a declarar `throws`.

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/exceptions/NegocioException.java`**

```java
package com.acessoatempo.indicadores.exceptions;

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

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/exceptions/ParametroInvalidoException.java`**

```java
package com.acessoatempo.indicadores.exceptions;

import org.springframework.http.HttpStatus;

/** Parâmetro do pedido em falta ou com valor inválido: resulta em HTTP 400. */
public class ParametroInvalidoException extends NegocioException {

    public ParametroInvalidoException(String mensagem) {
        super(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", mensagem);
    }
}
```

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/exceptions/EventoInvalidoException.java`**

```java
package com.acessoatempo.indicadores.exceptions;

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

- **`NegocioException`** é a base: cada exceção diz o **código HTTP** e um **código de erro estável**. Assim o `@ControllerAdvice` fica num único sítio e não precisa de um método por exceção.
- **Duas famílias de erro**, com tratamento diferente:
  - `ParametroInvalidoException` → o cliente errou → **HTTP 400**.
  - `EventoInvalidoException` → um **evento** mal formado; ninguém espera resposta HTTP. A exceção sobe até ao listener; o RabbitMQ repete e, se persistir, envia a mensagem para a **Dead-Letter Queue** (capítulo 04).

## 3.7 Tratamento global de erros

No package `handlers`. Equivale ao `ExceptionHandlers` do `loan_proposals` (aula 04, *5/14*): `@ControllerAdvice` aplica-se a **todos** os controllers do módulo.

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/handlers/ExceptionHandlers.java`**

```java
package com.acessoatempo.indicadores.handlers;

import com.acessoatempo.indicadores.dtos.ErroDTO;
import com.acessoatempo.indicadores.exceptions.NegocioException;
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

**Para a defesa:**

- Há **3 handlers**, do mais específico para o mais geral: erros de negócio (400/404/409...), pedido inválido (tipo errado/JSON mal formado → 400) e "tudo o resto".
- No handler geral, as exceções do próprio Spring MVC (parâmetro em falta, URL inexistente → 404, método não permitido → 405) **implementam `ErrorResponse`** e já trazem o código HTTP certo. Sem este tratamento, uma URL inexistente devolvia **500**, o que está errado (o teste `urlInexistenteDa404EnaoDa500` garante-o).
- Erros inesperados dão **HTTP 500 com mensagem genérica**; o detalhe (`stack trace`) vai **só para o log**. A aula avisa que enviar mensagens de erro técnicas ao cliente "não é recomendado" (aula 03, *PostgreSQL 12/20*).
- O `traceId` vem do cabeçalho `X-Trace-Id` que a Gateway acrescenta (🤝 confirmar o nome do cabeçalho com a Parte 4).

## 3.8 Correr e verificar

1. Arranca o serviço. No log, procura o `create table indicadores_semanais` e `create table eventos_processados` (o Hibernate cria-as; `show-sql: true`).
2. No **pgAdmin** (`http://localhost:9999`, `admin@admin.com` / `admin`): regista o servidor (host `postgres_server`, porta `5432`, utilizador `indicadores_user`, password `indicadores_pass`, BD `indicadores_db`) e confirma as duas tabelas. (Ou: `docker exec postgres_server psql -U indicadores_user -d indicadores_db -c '\dt'`.)

## ✅ Checklist do capítulo 03

- [ ] O serviço arranca e as **2 tabelas** existem em `indicadores_db`
- [ ] 4 DTO + `ErroDTO`, 3 exceções e o `ExceptionHandlers` escritos
- [ ] Sabes explicar: DTO vs entidade, query derivada vs `@Query`, o que é idempotência
