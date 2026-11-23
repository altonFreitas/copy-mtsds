# 06 — `garantia-acesso`: esqueleto e dados

> ⚠️ **v2.1 — Capítulo da versão antiga:** este capítulo mostra o código da `referencia/` (nomes antigos, `com.acessoatempo`). **A lógica é a mesma. Usa `spring.application.name: garantia-acesso`, porta 8085 e pacote `pt.estg.mtsds.acesso.garantia_acesso` (não `incumprimento`). Referência correta: `referencia-grupo/incumprimento/`.** O **código correto para o repositório do grupo** está em `referencia-grupo/`. Ver `referencia-grupo/LEIA-ME.md` para as diferenças.

**Objetivo:** o serviço arranca e tem as tabelas criadas no PostgreSQL.
**Baseado em:** os mesmos slides do capítulo 02 e 03, mais aula 03 (auditoria `@CreatedDate`, relações `@ManyToOne`, enums) e aula 04 (`customers 3/12`).

## 6.1 O que é este serviço

É o serviço **novo** (não existe no sistema atual) e o mais importante da tua parte. Trata cada incumprimento **em 3 níveis** (regra R6) e **orquestra** a transferência para outra instituição.

```text
tmrg.risco-detetado ─────► caso EM_RISCO (nível 0)
tmrg.incumprimento-previsto ┐
tmrg.incumprido ────────────┴► caso REGISTADO (nível 1)  ──publica incumprimento.registado
        │  PUT /casos/{id}/alerta-diretor
        ▼
   DIRETOR_ALERTADO (nível 2)  ──publica incumprimento.diretor-alertado   (5 dias úteis para reagendar)
        │  POST /casos/{id}/transferencias   (só depois dos 5 dias úteis)
        ▼
   TRANSFERENCIA_PROPOSTA (nível 3)  ──publica transferencia.proposta
        │  PUT /transferencias/{id}/resposta
        ├─ aceite ──► RESOLVIDO         ──publica transferencia.aceite + incumprimento.resolvido
        └─ recusada / sem resposta ──► SINALIZADO_DIRECAO_CLINICA ──publica transferencia.recusada  (compensação)
consulta.agendada (qualquer caso aberto) ──► RESOLVIDO ──publica incumprimento.resolvido
```

## 6.2 Módulo, `pom.xml`, configuração, banner e arranque

1. Já criaste o módulo `garantia-acesso` no capítulo 01. Escreve o `pom.xml` (é igual ao do `historico`; muda só o nome):

📄 **`incumprimento/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- Liga este módulo ao pom pai: herda propriedades, versões e dependências comuns. -->
    <parent>
        <groupId>com.acessoatempo</groupId>
        <artifactId>acesso-a-tempo</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>garantia-acesso</artifactId>
    <packaging>jar</packaging>
    <name>incumprimento</name>
    <description>Microserviço de tratamento do incumprimento (3 níveis e transferência para outra instituição)</description>

    <dependencies>
        <!-- JPA/Hibernate: acesso à base de dados sem escrever SQL. -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <!-- Driver do PostgreSQL (só é preciso em execução). -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <!-- RabbitMQ: o serviço CONSOME e PUBLICA eventos. -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-amqp</artifactId>
        </dependency>
        <!-- Validação dos DTO (@NotNull, ...). -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <!-- Actuator: /actuator/health e /actuator/metrics (RNF09). -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <!-- Eureka Client: o serviço regista-se no Eureka com o nome spring.application.name. -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- H2 só para os testes (BD em memória). -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Gera o JAR executável (necessário para o Dockerfile). -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

2. Cria os packages `com.acessoatempo.incumprimento.{auditing,config,controllers,data,dtos,enums,exceptions,handlers,listeners,messages,models,services}`.

3. `application.yml` (porta **8085**). Além do que já conheces do capítulo 02, tem um bloco **`incumprimento:`** com configuração **própria do serviço**: os feriados (R6) e o prazo de resposta da transferência.

📄 **`incumprimento/src/main/resources/application.yml`**

```yaml
server:
  port: 8085

spring:
  application:
    # Nome com que o serviço se regista no Eureka e é encontrado pela Gateway (lb://incumprimento).
    name: incumprimento

  datasource:
    # ${VARIAVEL:valor-por-omissao}: em Docker/Kubernetes a variável de ambiente sobrepõe-se ao valor local.
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:incumprimento_db}
    username: ${DB_USER:incumprimento_user}
    password: ${DB_PASSWORD:incumprimento_pass}
    driver-class-name: org.postgresql.Driver

  jpa:
    open-in-view: false
    hibernate:
      # update: o Hibernate cria/atualiza as tabelas a partir das classes @Entity (como nas aulas).
      ddl-auto: update
    show-sql: true

  rabbitmq:
    host: ${RABBIT_HOST:localhost}
    port: ${RABBIT_PORT:5672}
    username: ${RABBIT_USER:user}
    password: ${RABBIT_PASSWORD:123}
    listener:
      simple:
        acknowledge-mode: auto        # ack só depois de o listener terminar sem erro
        default-requeue-rejected: false
        retry:
          enabled: true
          max-attempts: 3             # 3 tentativas e depois Dead-Letter
          initial-interval: 1000
          multiplier: 2.0

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}

management:
  endpoints:
    web:
      exposure:
        include: health,metrics         # RNF09

# Configuração própria do serviço (lida com @Value nas classes de serviço).
incumprimento:
  # Feriados nacionais (formato AAAA-MM-DD, separados por vírgula): não contam como dias úteis (R6).
  feriados: 2026-12-01,2026-12-08,2026-12-25,2027-01-01
  transferencia:
    # Dias que a outra instituição tem para responder a uma proposta de transferência.
    prazo-resposta-dias: 5
```

📄 **`incumprimento/src/main/resources/banner.txt`**

```text
  ___ _   _  ____ _   _ __  __ ____  ____  ___ __  __ _____ _   _ _____ ___
 |_ _| \ | |/ ___| | | |  \/  |  _ \|  _ \|_ _|  \/  | ____| \ | |_   _/ _ \
  | ||  \| | |   | | | | |\/| | |_) | |_) || || |\/| |  _| |  \| | | || | | |
  | || |\  | |___| |_| | |  | |  __/|  _ < | || |  | | |___| |\  | | || |_| |
 |___|_| \_|\____|\___/|_|  |_|_|   |_| \_\___|_|  |_|_____|_| \_| |_| \___/
 :: Acesso a Tempo :: incumprimento (porta 8085)
```

4. Classe de arranque. Tem **`@EnableScheduling`** a mais: ativa as tarefas agendadas (`@Scheduled`), usadas para a expiração das propostas de transferência (capítulo 08).

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/Incumprimento.java`**

```java
package com.acessoatempo.incumprimento;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Classe de arranque do microserviço {@code incumprimento}.
 *
 * <p>{@code @EnableScheduling} ativa as tarefas agendadas ({@code @Scheduled}); é usada para
 * dar como recusadas as propostas de transferência a que a outra instituição não respondeu a tempo.
 */
@SpringBootApplication
@EnableScheduling
public class Incumprimento {

    public static void main(String[] args) {
        SpringApplication.run(Incumprimento.class, args);
    }
}
```

## 6.3 Enums

No package `enums` (aula 02: `enums.Category`). Em vez de strings soltas, os estados são **tipos**: o compilador impede valores inválidos.

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/enums/EstadoCaso.java`**

```java
package com.acessoatempo.incumprimento.enums;

import java.util.List;

/**
 * Estados de um caso de incumprimento.
 *
 * <pre>
 * EM_RISCO --(incumprimento previsto/incumprido)--> REGISTADO (nível 1)
 * REGISTADO --alerta-diretor--> DIRETOR_ALERTADO (nível 2)
 * DIRETOR_ALERTADO --propor transferência--> TRANSFERENCIA_PROPOSTA (nível 3)
 * TRANSFERENCIA_PROPOSTA --aceite--> RESOLVIDO | --recusada/sem resposta--> SINALIZADO_DIRECAO_CLINICA
 * qualquer estado aberto --consulta marcada--> RESOLVIDO
 * </pre>
 */
public enum EstadoCaso {
    EM_RISCO,
    REGISTADO,
    DIRETOR_ALERTADO,
    TRANSFERENCIA_PROPOSTA,
    SINALIZADO_DIRECAO_CLINICA,
    RESOLVIDO;

    /** Estados em que o caso ainda está aberto (só pode haver um caso aberto por episódio). */
    public static final List<EstadoCaso> ABERTOS = List.of(
            EM_RISCO, REGISTADO, DIRETOR_ALERTADO, TRANSFERENCIA_PROPOSTA, SINALIZADO_DIRECAO_CLINICA);
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/enums/OrigemCaso.java`**

```java
package com.acessoatempo.incumprimento.enums;

/** Evento do {@code tmrg} que deu origem ao caso. */
public enum OrigemCaso {
    /** tmrg.risco-detetado: 75% do prazo consumido (ainda não é incumprimento). */
    RISCO,
    /** tmrg.incumprimento-previsto: consulta marcada para depois da data-limite (R5). */
    INCUMPRIMENTO_PREVISTO,
    /** tmrg.incumprido: a data-limite passou sem consulta (R4). */
    INCUMPRIDO
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/enums/EstadoTransferencia.java`**

```java
package com.acessoatempo.incumprimento.enums;

/** Estados de uma proposta de transferência para outra instituição (nível 3). */
public enum EstadoTransferencia {
    PROPOSTA,
    ACEITE,
    RECUSADA
}
```

> `EstadoCaso.ABERTOS` é a lista de estados em que o caso ainda não terminou. Garante a regra **"só um caso aberto por episódio"**.

## 6.4 Auditoria JPA

Igual à aula 03/04 (`JpaAuditingConfiguration`): preenche automaticamente `creationDate` e `lastModifiedDate`.

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/auditing/JpaAuditingConfiguration.java`**

```java
package com.acessoatempo.incumprimento.auditing;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Ativa a auditoria do JPA (igual à aula 03/04). Com isto, os campos anotados com {@code @CreatedDate}
 * e {@code @LastModifiedDate} nas entidades são preenchidos automaticamente pelo Spring.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfiguration {
}
```

## 6.5 Modelos (entidades)

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/models/CasoIncumprimento.java`**

```java
package com.acessoatempo.incumprimento.models;

import com.acessoatempo.incumprimento.enums.EstadoCaso;
import com.acessoatempo.incumprimento.enums.OrigemCaso;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Um caso de incumprimento: acompanha um episódio de espera que está em risco ou já falhou o prazo e
 * regista em que nível de tratamento (1, 2 ou 3) vai. Este modelo é só do contexto {@code incumprimento}:
 * o "episódio" completo pertence à {@code lista-espera}; aqui guarda-se apenas o {@code episodioId}.
 */
@NoArgsConstructor
@Data
@Entity
@Table(name = "casos_incumprimento")
@EntityListeners(AuditingEntityListener.class)
public class CasoIncumprimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Identificador do episódio (vem dos eventos). Não é uma chave estrangeira: é outro serviço/BD. */
    @Column(nullable = false)
    private Long episodioId;

    @Column(nullable = false)
    private String especialidade;

    /** Guarda o NOME do enum ("INCUMPRIDO") e não o número da posição, que mudaria se reordenássemos. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigemCaso origem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCaso estado;

    /** 0 = em risco (ainda sem incumprimento); 1, 2 e 3 = níveis de tratamento da regra R6. */
    @Column(nullable = false)
    private int nivel;

    @Column(nullable = false)
    private LocalDate dataDeteccao;

    /** Fim do prazo de 5 dias úteis do diretor (preenchido no nível 2). */
    private LocalDate dataLimiteDiretor;

    @CreatedDate
    @Column(name = "creation_date", updatable = false)
    private LocalDateTime creationDate;

    @LastModifiedDate
    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;

    /**
     * Bloqueio otimista: o Hibernate incrementa este número em cada alteração. Se duas pessoas alterarem
     * o mesmo caso em simultâneo, a segunda falha em vez de apagar silenciosamente o trabalho da primeira.
     */
    @Version
    private Long versao;
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/models/Instituicao.java`**

```java
package com.acessoatempo.incumprimento.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Instituição de destino simulada, para onde se pode propor uma consulta quando o prazo falha (nível 3).
 * Numa instituição real isto seria um sistema externo; aqui é uma tabela simples (ver DadosIniciais).
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "instituicoes")
public class Instituicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String nome;

    /** Especialidade que a instituição aceita. */
    @Column(nullable = false)
    private String especialidade;

    public Instituicao(String nome, String especialidade) {
        this.nome = nome;
        this.especialidade = especialidade;
    }
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/models/Transferencia.java`**

```java
package com.acessoatempo.incumprimento.models;

import com.acessoatempo.incumprimento.enums.EstadoTransferencia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Proposta de transferência de um caso para outra instituição (nível 3). É um passo da "saga" de
 * transferência orquestrada por este serviço.
 *
 * <p>Usa {@code @Getter/@Setter} em vez de {@code @Data}: o {@code @Data} gera {@code equals/hashCode/toString}
 * com TODOS os campos, e com relações entre entidades isso pode dar ciclos infinitos.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "transferencias")
@EntityListeners(AuditingEntityListener.class)
public class Transferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Muitas transferências podem pertencer ao mesmo caso (ex.: uma recusada e outra nova). */
    @ManyToOne(optional = false)
    @JoinColumn(name = "caso_id")
    private CasoIncumprimento caso;

    @ManyToOne(optional = false)
    @JoinColumn(name = "instituicao_id")
    private Instituicao instituicao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoTransferencia estado;

    @Column(nullable = false)
    private LocalDate dataProposta;

    /** Último dia em que a instituição pode responder; depois disto conta como recusa. */
    @Column(nullable = false)
    private LocalDate respostaAte;

    private LocalDate dataResposta;

    @CreatedDate
    @Column(name = "creation_date", updatable = false)
    private LocalDateTime creationDate;

    @LastModifiedDate
    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/models/EventoProcessado.java`**

```java
package com.acessoatempo.incumprimento.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Registo dos eventos já processados (idempotência do consumidor). O RabbitMQ garante entrega "pelo menos
 * uma vez": se o mesmo {@code eventoId} chegar duas vezes, a segunda é ignorada.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "eventos_processados")
public class EventoProcessado {

    @Id
    private String eventoId;

    private String tipo;

    private LocalDateTime processadoEm;
}
```

**Para a defesa:**

- **`@Enumerated(EnumType.STRING)`** — grava o **nome** do estado (`"REGISTADO"`) e não o número da posição. Com `ORDINAL`, reordenar o enum **corromperia** os dados existentes.
- **`episodioId` não é chave estrangeira**: o episódio pertence à `lista-espera` (outra BD). Os contextos partilham **só o identificador** (secção 3.3 da proposta: *"não vai haver uma classe Episódio partilhada"*).
- **`@Version`** — bloqueio otimista: se duas pessoas alterarem o mesmo caso ao mesmo tempo, a segunda recebe erro em vez de apagar o trabalho da primeira.
- **`@ManyToOne` + `@JoinColumn`** (aula 03, *PostgreSQL 5/20*) em `Transferencia`: muitas transferências → um caso; a chave estrangeira é `caso_id`.
- **`@Data` vs `@Getter/@Setter`** — nas entidades **sem relações** usa-se `@Data`. Em `Transferencia`, que tem relações, o `@Data` gera `equals/hashCode/toString` com todos os campos e pode causar **ciclos infinitos**: por isso só `@Getter/@Setter`.
- **`@CreatedDate`/`@LastModifiedDate`** com `@EntityListeners(AuditingEntityListener.class)`: o Spring preenche as datas (aula 03, *PostgreSQL 4/20*).

## 6.6 Repositórios

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/data/CasosRepository.java`**

```java
package com.acessoatempo.incumprimento.data;

import com.acessoatempo.incumprimento.enums.EstadoCaso;
import com.acessoatempo.incumprimento.models.CasoIncumprimento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

/**
 * Repositório dos casos. Os métodos são "derivados do nome": o Spring Data JPA gera o SQL a partir de
 * {@code findBy...And...}. Os que recebem {@code Pageable} devolvem uma página (RNF12: listas paginadas).
 */
public interface CasosRepository extends JpaRepository<CasoIncumprimento, Integer> {

    /** Procura o caso ABERTO de um episódio (só pode existir um). */
    Optional<CasoIncumprimento> findFirstByEpisodioIdAndEstadoIn(Long episodioId, Collection<EstadoCaso> estados);

    Page<CasoIncumprimento> findByEspecialidade(String especialidade, Pageable pageable);

    Page<CasoIncumprimento> findByNivel(int nivel, Pageable pageable);

    Page<CasoIncumprimento> findByEspecialidadeAndNivel(String especialidade, int nivel, Pageable pageable);
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/data/TransferenciasRepository.java`**

```java
package com.acessoatempo.incumprimento.data;

import com.acessoatempo.incumprimento.enums.EstadoTransferencia;
import com.acessoatempo.incumprimento.models.Transferencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/** Repositório das propostas de transferência. */
public interface TransferenciasRepository extends JpaRepository<Transferencia, Integer> {

    /** Propostas ainda sem resposta cujo prazo já terminou (usado pela tarefa agendada). */
    List<Transferencia> findByEstadoAndRespostaAteBefore(EstadoTransferencia estado, LocalDate data);
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/data/InstituicoesRepository.java`**

```java
package com.acessoatempo.incumprimento.data;

import com.acessoatempo.incumprimento.models.Instituicao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Repositório das instituições de destino (lista pequena e fechada, por isso sem paginação). */
public interface InstituicoesRepository extends JpaRepository<Instituicao, Integer> {

    List<Instituicao> findByEspecialidade(String especialidade);
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/data/EventosProcessadosRepository.java`**

```java
package com.acessoatempo.incumprimento.data;

import com.acessoatempo.incumprimento.models.EventoProcessado;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositório dos eventos já processados (só se usa existsById e save). */
public interface EventosProcessadosRepository extends JpaRepository<EventoProcessado, String> {
}
```

- **`Pageable`/`Page`** — os métodos que recebem `Pageable` devolvem uma **página**: a BD só envia as linhas pedidas (RNF12: *"listas sempre paginadas"*).
- **`findFirstByEpisodioIdAndEstadoIn`** — "primeiro caso deste episódio cujo estado esteja nesta lista". Usa-se para saber se já há um caso aberto.
- **Há 3 variações de `findBy…` para a lista** (só especialidade, só nível, ambos) em vez de uma query com parâmetros opcionais (`:esp is null or ...`). É mais simples de perceber e evita problemas de tipos com `null` no PostgreSQL.
- **`findByEstadoAndRespostaAteBefore`** — as propostas por responder cujo prazo já passou.

## 6.7 Dados iniciais (instituições simuladas)

O `CommandLineRunner` (Spring Boot) corre **logo depois do arranque**. Cria as instituições de destino simuladas se a tabela estiver vazia.

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/config/DadosIniciais.java`**

```java
package com.acessoatempo.incumprimento.config;

import com.acessoatempo.incumprimento.data.InstituicoesRepository;
import com.acessoatempo.incumprimento.models.Instituicao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Carrega as instituições de destino simuladas no arranque, se a tabela estiver vazia.
 * {@code CommandLineRunner} é executado pelo Spring Boot logo depois de a aplicação arrancar.
 */
@Component
public class DadosIniciais implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DadosIniciais.class);

    @Autowired
    private InstituicoesRepository instituicoesRepository;

    @Override
    public void run(String... args) {
        if (instituicoesRepository.count() > 0) {
            return;
        }
        instituicoesRepository.saveAll(List.of(
                new Instituicao("Hospital Simulado Norte", "CARDIOLOGIA"),
                new Instituicao("Hospital Simulado Sul", "CARDIOLOGIA"),
                new Instituicao("Hospital Simulado Norte", "ORTOPEDIA"),
                new Instituicao("Clínica Simulada Centro", "ORTOPEDIA"),
                new Instituicao("Clínica Simulada Centro", "OFTALMOLOGIA"),
                new Instituicao("Hospital Simulado Sul", "UROLOGIA")));
        log.info("Instituições simuladas criadas: {}", instituicoesRepository.count());
    }
}
```

## 6.8 Landing e teste

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/controllers/LandingController.java`**

```java
package com.acessoatempo.incumprimento.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint {@code /incumprimento/landing}: confirma que o serviço está a responder (convenção comum). */
@RestController
@RequestMapping("/incumprimento")
public class LandingController {

    @GetMapping("/landing")
    public ResponseEntity<String> landing() {
        return ResponseEntity.status(HttpStatus.OK).body("Incumprimento service is working fine...");
    }
}
```

📄 **`incumprimento/src/test/resources/application.yml`**

```yaml
# Configuração usada SÓ nos testes (substitui o application.yml principal durante os testes).
spring:
  application:
    name: incumprimento
  datasource:
    # H2 em memória em modo PostgreSQL: não precisa de Docker para correr os testes.
    url: jdbc:h2:mem:incumprimentodb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
  rabbitmq:
    listener:
      simple:
        auto-startup: false   # não liga o listener ao RabbitMQ nos testes

eureka:
  client:
    enabled: false

incumprimento:
  feriados: 2026-12-08,2026-12-25
  transferencia:
    prazo-resposta-dias: 5
```

📄 **`incumprimento/src/test/java/com/acessoatempo/incumprimento/controllers/LandingControllerTest.java`**

```java
package com.acessoatempo.incumprimento.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Teste do endpoint landing (igual ao LandingControllerTest da aula 02). */
@SpringBootTest
@AutoConfigureMockMvc
public class LandingControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    public void getLanding() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/incumprimento/landing").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(content().string(equalTo("Incumprimento service is working fine...")));
    }
}
```

## 6.9 Correr e verificar

> O projeto ainda não compila por inteiro se saltares passos: o `CasosService` e os DTO vêm nos capítulos 07 e 08. Para já, **escreve só o que está neste capítulo**; o `DadosIniciais` precisa apenas do repositório e do modelo.

1. Corre `Incumprimento`: `Started Incumprimento` no log e `create table casos_incumprimento`, `transferencias`, `instituicoes`, `eventos_processados`.
2. `http://localhost:8085/incumprimento/landing` → *Incumprimento service is working fine...*
3. `docker exec postgres_server psql -U incumprimento_user -d incumprimento_db -c 'select * from instituicoes;'` → 6 instituições.

## ✅ Checklist do capítulo 06

- [ ] 4 tabelas criadas e 6 instituições carregadas
- [ ] `landing` responde; `LandingControllerTest` passa
- [ ] Sabes explicar: `EnumType.STRING`, `@Version`, `@ManyToOne`, auditoria, porque `episodioId` não é chave estrangeira
