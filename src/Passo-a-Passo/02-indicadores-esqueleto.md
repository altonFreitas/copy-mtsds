# 02 — `historico`: esqueleto do serviço

> ⚠️ **v2.1 — Capítulo da versão antiga:** este capítulo mostra o código da `referencia/` (nomes antigos, `com.acessoatempo`). **O esqueleto é o mesmo. Usa `spring.application.name: historico`, porta 8086 e pacote `pt.estg.mtsds.acesso.historico` (não `indicadores`). Referência correta: `referencia-grupo/indicadores/`.** O **código correto para o repositório do grupo** está em `referencia-grupo/`. Ver `referencia-grupo/LEIA-ME.md` para as diferenças.

**Objetivo:** o serviço arranca, liga-se ao PostgreSQL e ao RabbitMQ e responde em `GET /indicadores/landing`.
**Baseado em:** aula 02 (*Starter App*: `application.yml`, `LandingController`, `LandingControllerTest`, Actuator, banner) e aula 04 (`customers 1/12`).

> Antes de começar: `docker compose ps` (capítulo 01) com tudo a correr.

## 2.1 O `pom.xml` do módulo

O pom filho só tem o que é **específico** deste serviço (o resto vem do pai). Repara que **não há `<version>`** nas dependências: vêm do `dependencyManagement` do pai.

📄 **`indicadores/pom.xml`**

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

    <artifactId>historico</artifactId>
    <packaging>jar</packaging>
    <name>indicadores</name>
    <description>Microserviço de indicadores (modelo de leitura construído a partir dos eventos)</description>

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
        <!-- RabbitMQ: o serviço CONSOME eventos. -->
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

| Dependência | Para quê |
|---|---|
| `spring-boot-starter-data-jpa` | JPA/Hibernate: acesso à BD sem escrever SQL (aula 03) |
| `postgresql` (`runtime`) | Driver do PostgreSQL; só é preciso ao executar |
| `spring-boot-starter-amqp` | RabbitMQ: `@RabbitListener`, `RabbitTemplate` (aula 05) |
| `spring-boot-starter-validation` | `@NotNull`, `@Valid` nos DTO |
| `spring-boot-starter-actuator` | `/actuator/health` e `/actuator/metrics` (RNF09; aula 02) |
| `spring-cloud-starter-netflix-eureka-client` | O serviço regista-se no Eureka (aula 04, *eureka_service 5/14*) |
| `h2` (`test`) | BD em memória **só para testes**, sem precisar de Docker |
| `spring-boot-maven-plugin` | Gera o JAR executável (usa o `repackage` do pai) |

Depois de editar, **recarrega o Maven** (botão *Reload* no IntelliJ). Se der erro: `mvn clean` e reload (aula 02, *IntelliJ 4/4*).

## 2.2 Estrutura de pastas e packages

Dentro de `indicadores/src/main/java`, cria o package `com.acessoatempo.indicadores` e, dentro dele, os sub-packages (aula 04, *customers 2/12*):

```text
com.acessoatempo.indicadores
├── config        <- configuração do RabbitMQ
├── controllers   <- endpoints REST
├── data          <- repositórios JPA
├── dtos          <- objetos que saem/entram na API
├── exceptions    <- exceções de negócio
├── handlers      <- tratamento global de erros
├── listeners     <- consumidores do RabbitMQ
├── messages      <- mensagens (eventos)
├── models        <- entidades (tabelas)
└── services      <- regras de negócio
```

E em `src/main/resources`: `application.yml` e `banner.txt`. Em `src/test/java`: o mesmo package. Em `src/test/resources`: `application.yml` (de testes).

## 2.3 Configuração: `application.yml`

📄 **`indicadores/src/main/resources/application.yml`**

```yaml
server:
  port: 8086

spring:
  application:
    # Nome com que o serviço se regista no Eureka e é encontrado pela Gateway (lb://indicadores).
    name: indicadores

  datasource:
    # ${VARIAVEL:valor-por-omissao}: em Docker/Kubernetes a variável de ambiente sobrepõe-se ao valor local.
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:indicadores_db}
    username: ${DB_USER:indicadores_user}
    password: ${DB_PASSWORD:indicadores_pass}
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
        # Confirmação (ack) automática DEPOIS de o método do listener terminar sem erro.
        acknowledge-mode: auto
        # Se a mensagem falhar, NÃO volta para a fila: vai para a Dead-Letter (ver MQConfig).
        default-requeue-rejected: false
        retry:
          enabled: true
          max-attempts: 3          # 3 tentativas e depois Dead-Letter
          initial-interval: 1000   # 1 segundo entre tentativas
          multiplier: 2.0

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}

management:
  endpoints:
    web:
      exposure:
        # RNF09: só /health e /metrics.
        include: health,metrics
```

**Pontos a perceber:**

- **`spring.application.name`** — é o nome com que o serviço se regista no Eureka e é encontrado pela Gateway (`lb://indicadores`). **Tem de ser único** (aula 04, *eureka_service 5/14*).
- **`${DB_HOST:localhost}`** — lê a variável de ambiente `DB_HOST` e, se não existir, usa `localhost`. Em Docker/Kubernetes basta definir a variável (ConfigMap/Secret), **sem mexer no código**.
- **`ddl-auto: update`** — o Hibernate cria/atualiza as tabelas a partir das classes `@Entity` (aula 03). Em produção real usar-se-iam migrações (Flyway), mas aqui segue-se a aula.
- **`open-in-view: false`** — evita consultas à BD fora das transações (boa prática).
- **Bloco `rabbitmq.listener`** — define a política de falhas: *ack* só depois de processar, **3 repetições** e depois **Dead-Letter** (secção 4.3 da proposta). Vais vê-lo a funcionar no capítulo 04.
- **`management`** — só expõe `health` e `metrics` (RNF09). Na aula expunha-se tudo (`"*"`), mas a própria aula avisa que em produção não é sensato.

## 2.4 O banner e a classe de arranque

O `banner.txt` identifica o serviço no log (aula 02, *multi-módulos 6/7*; gera-se em https://devops.datenkollektiv.de/banner.txt/index.html).

📄 **`indicadores/src/main/resources/banner.txt`**

```text
  ___ _   _ ____ ___ ____    _    ____   ___  ____  _____ ____
 |_ _| \ | |  _ \_ _/ ___|  / \  |  _ \ / _ \|  _ \| ____/ ___|
  | ||  \| | | | | | |     / _ \ | | | | | | | |_) |  _| \___ \
  | || |\  | |_| | | |___ / ___ \| |_| | |_| |  _ <| |___ ___) |
 |___|_| \_|____/___\____/_/   \_\____/ \___/|_| \_\_____|____/
 :: Acesso a Tempo :: indicadores (porta 8086)
```

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/Indicadores.java`**

```java
package com.acessoatempo.indicadores;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe de arranque do microserviço {@code indicadores}.
 *
 * <p>{@code @SpringBootApplication} junta três coisas: configuração automática (Spring Boot),
 * procura de componentes ({@code @Controller}, {@code @Service}, {@code @Component}...) neste
 * package e nos filhos, e a declaração de beans ({@code @Configuration}).
 */
@SpringBootApplication
public class Indicadores {

    public static void main(String[] args) {
        SpringApplication.run(Indicadores.class, args);
    }
}
```

> 💡 **Defesa:** `@SpringBootApplication` = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`. O Spring procura componentes neste package e nos filhos; por isso a classe de arranque tem de estar **no package-raiz**.

## 2.5 O primeiro endpoint: `landing`

Convenção comum a todos os serviços (secção 4.4.1): um endpoint `/landing` que confirma que o serviço responde. É o `LandingController` da aula 02.

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/controllers/LandingController.java`**

```java
package com.acessoatempo.indicadores.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint {@code /indicadores/landing}: confirma que o serviço está a responder (convenção comum). */
@RestController
@RequestMapping("/indicadores")
public class LandingController {

    @GetMapping("/landing")
    public ResponseEntity<String> landing() {
        return ResponseEntity.status(HttpStatus.OK).body("Indicadores service is working fine...");
    }
}
```

- `@RestController` — a classe recebe pedidos web e devolve o corpo da resposta.
- `@RequestMapping("/indicadores")` — prefixo comum a todos os endpoints do controller.
- `@GetMapping("/landing")` — este método responde a `GET /indicadores/landing`.
- `ResponseEntity` — representa a resposta HTTP completa (código, cabeçalhos, corpo), como na aula.

## 2.6 Configuração e teste automático

Em `src/test/resources/application.yml` (substitui o principal **durante os testes**): BD H2 em memória, listener do RabbitMQ desligado e Eureka desligado. Assim os testes não precisam de Docker.

📄 **`indicadores/src/test/resources/application.yml`**

```yaml
# Configuração usada SÓ nos testes (substitui o application.yml principal durante os testes).
spring:
  application:
    name: indicadores
  datasource:
    # H2 em memória em modo PostgreSQL: não precisa de Docker para correr os testes.
    url: jdbc:h2:mem:indicadoresdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
  rabbitmq:
    listener:
      simple:
        # Não liga o listener ao RabbitMQ nos testes (não há broker).
        auto-startup: false

eureka:
  client:
    enabled: false
```

📄 **`indicadores/src/test/java/com/acessoatempo/indicadores/controllers/LandingControllerTest.java`**

```java
package com.acessoatempo.indicadores.controllers;

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

/**
 * Teste do endpoint landing, igual ao {@code LandingControllerTest} da aula 02. O {@code MockMvc}
 * envia pedidos HTTP "de mentira" ao controller e permite fazer verificações (assertions) à resposta.
 *
 * <p>Nota Spring Boot 3.5: a annotation {@code @AutoConfigureMockMvc} mudou de package
 * (antes era {@code org.springframework.boot.test.autoconfigure.web.servlet}).
 */
@SpringBootTest
@AutoConfigureMockMvc
public class LandingControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    public void getLanding() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/indicadores/landing").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(content().string(equalTo("Indicadores service is working fine...")));
    }
}
```

- `@SpringBootTest` arranca o contexto da aplicação; `@AutoConfigureMockMvc` injeta o `MockMvc`, que envia pedidos HTTP "de mentira" e permite *assertions* à resposta (aula 02, *Starter App 9/11*).
- ⚠️ Em Spring Boot 3.5 o `import` do `AutoConfigureMockMvc` mudou (ver `00-LEIA-ME.md`).

## 2.7 Correr e verificar

1. Corre a classe `Indicadores` (triângulo verde). Procura no log **`Started Indicadores in ... seconds`**.
2. Abre `http://localhost:8086/indicadores/landing` → *Indicadores service is working fine...*
3. Abre `http://localhost:8086/actuator/health` → `"status":"UP"`.
4. Corre os testes (botão direito em `src/test` → *Run Tests*): 1 teste deve passar.

> Os avisos sobre o Eureka (`Connection refused ... 8761`) são normais enquanto o Eureka não estiver a correr (é trabalho de arranque do grupo). Não impedem o serviço de funcionar.

## ✅ Checklist do capítulo 02

- [ ] `Started Indicadores` no log, sem erros (os avisos do Eureka são normais)
- [ ] `/indicadores/landing` responde 200
- [ ] `/actuator/health` responde `UP`
- [ ] `LandingControllerTest` passa
