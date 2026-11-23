# 01 — Preparação: projeto multi-módulo e infraestrutura

**Objetivo:** ter o projeto Maven multi-módulo (pom pai + 2 módulos) e o PostgreSQL e RabbitMQ a correr.
**Baseado em:** aula 02 (*multi-módulos* 1/7 a 7/7), aula 03 (docker-compose do PostgreSQL), aula 05 (RabbitMQ em Docker).

## 1.1 Ferramentas

| Ferramenta | Para quê | Como confirmar |
|---|---|---|
| JDK 17 ou superior | Compilar (o projeto compila para a versão 17) | `java -version` |
| IntelliJ IDEA | IDE usada nas aulas | — |
| Maven | O IntelliJ traz um embutido; não precisas de instalar | — |
| Docker Desktop | PostgreSQL e RabbitMQ em contentores | `docker --version` e Docker Desktop **aberto** |
| Postman | Simular os atores (a proposta não tem interface gráfica) | — |

## 1.2 Criar o projeto Maven (pom pai)

Na aula 02 (*multi-módulos*) cada microserviço é um **módulo**; o projeto principal tem um `pom.xml` **pai** e cada módulo tem o seu `pom.xml` **filho**.

1. IntelliJ → **File | New | Project** → gerador **Maven** (sem *archetype*).
   - **Name:** `acesso-a-tempo` · **Location:** uma pasta **fora de `src/`** (a pasta `src/` do repositório da disciplina é do relatório LaTeX). Sugestão: `.../Projeto1/acesso-a-tempo`.
   - **GroupId:** `com.acessoatempo` · **ArtifactId:** `acesso-a-tempo`.
2. **Apaga a pasta `src`** do projeto: cada módulo terá a sua (slide 7 da aula 02).
3. Substitui o conteúdo do `pom.xml` pelo seguinte (o `<modules>` o IntelliJ preenche quando criares os módulos):

📄 **`pom.xml  (pom PAI, na raiz do projeto)`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- Identificação do projeto (pom PAI). Os módulos filhos herdam daqui. -->
    <groupId>com.acessoatempo</groupId>
    <artifactId>acesso-a-tempo</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>acesso-a-tempo</name>
    <description>Acesso a Tempo - lista de espera e TMRG da consulta externa hospitalar (Grupo 5)</description>

    <!-- Módulos (um por microserviço). O IntelliJ acrescenta aqui os que a Parte 1, 2 e 4 criarem. -->
    <modules>
        <module>garantia-acesso</module>
        <module>historico</module>
    </modules>

    <!-- Propriedades: versões definidas num só sítio, para todos os módulos usarem as mesmas. -->
    <properties>
        <java.version>17</java.version>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <spring.boot.version>4.1.2</spring.boot.version>
        <spring.cloud.version>2025.0.3</spring.cloud.version>
        <lombok.version>1.18.48</lombok.version>
        <modelmapper.version>3.2.8</modelmapper.version>
        <spring.boot.maven.plugin.version>${spring.boot.version}</spring.boot.maven.plugin.version>
    </properties>

    <!-- dependencyManagement: só FIXA VERSÕES. Os filhos declaram a dependência sem <version>. -->
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring.boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring.cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <!-- dependencies: aqui ficam as dependências que TODOS os módulos recebem. -->
    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>${lombok.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.modelmapper</groupId>
            <artifactId>modelmapper</artifactId>
            <version>${modelmapper.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring.boot.maven.plugin.version}</version>
                    <executions>
                        <execution>
                            <!-- Sem o spring-boot-starter-parent é preciso ligar o "repackage" ao build:
                                 transforma o JAR normal num JAR EXECUTÁVEL (java -jar), necessário no Docker. -->
                            <goals>
                                <goal>repackage</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <configuration>
                        <!-- Guarda o nome dos parâmetros nas classes: sem isto o Spring não sabe que
                             @PathVariable Integer id corresponde a /{id} (não usamos o spring-boot-starter-parent). -->
                        <parameters>true</parameters>
                        <annotationProcessorPaths>
                            <path>
                                <groupId>org.projectlombok</groupId>
                                <artifactId>lombok</artifactId>
                                <version>${lombok.version}</version>
                            </path>
                        </annotationProcessorPaths>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

### O que faz (bloco a bloco)

| Bloco | O que faz | Para a defesa |
|---|---|---|
| `<packaging>pom</packaging>` | Este projeto não gera código, só agrupa módulos | Um pom pai tem sempre `pom` |
| `<modules>` | Lista os módulos filhos (um por microserviço) | Cada módulo é uma aplicação Spring Boot independente |
| `<properties>` | Versões definidas **num só sítio** | Mudar de versão = mudar uma linha |
| `<dependencyManagement>` | **Só fixa versões** (não adiciona nada) | Os filhos declaram a dependência **sem `<version>`** |
| `<dependencies>` | Dependências que **todos** os módulos recebem | Lombok, web, ModelMapper e testes |
| `<pluginManagement>` | Configura plugins para os filhos | Ver as 3 armadilhas abaixo |

### As 3 armadilhas (não usamos o `spring-boot-starter-parent`)

As aulas usam um pom pai **próprio** (não o `spring-boot-starter-parent`). Isso obriga a configurar à mão o que o *starter parent* faz sozinho. Todas estas foram descobertas ao testar:

1. **`<parameters>true</parameters>`** — guarda os nomes dos parâmetros nas classes. Sem isto, o Spring (versão 7) não sabe que `@PathVariable Integer id` corresponde a `/{id}` e dá erro. (Por segurança, o guia também escreve `@PathVariable("id")` explicitamente.)
2. **`annotationProcessorPaths` com o Lombok** — garante que o Lombok gera os getters/setters ao compilar, mesmo em JDK recentes.
3. **`<goal>repackage</goal>`** — transforma o JAR normal num **JAR executável**. Sem isto, `java -jar` dá *"no main manifest attribute"* e o Docker não arranca o serviço.

> 💡 **Defesa:** *"Porque não usaram o `spring-boot-starter-parent`?"* — Seguimos o modelo das aulas (pom pai próprio com `dependencyManagement`), que dá controlo explícito das versões e permite importar o BOM do Spring Boot **e** do Spring Cloud no mesmo projeto.

## 1.3 Criar os dois módulos

Para cada um dos nomes `historico` e `garantia-acesso` (aula 02, slide *multi-módulos 4/7*):

1. Clica com o botão direito no projeto → **New | Module** (ou *File | New | Module*), gerador **Maven**, **sem archetype**.
2. **Name:** `historico` · **Parent:** `acesso-a-tempo` · **GroupId:** `com.acessoatempo`.
3. **Apaga a classe de testes e a classe `Main` gerada** (vais criar as tuas).
4. Verifica que o `pom.xml` pai ganhou o bloco `<modules>` com os dois módulos.

Estrutura final (as pastas `src` de cada módulo são criadas nos capítulos seguintes):

```text
acesso-a-tempo/
├── pom.xml                 <- pai
├── indicadores/
│   └── pom.xml
└── incumprimento/
    └── pom.xml
```

## 1.4 Infraestrutura: PostgreSQL, pgAdmin e RabbitMQ

A proposta (secção 4.1 e 5.1) pede **uma instância PostgreSQL com uma base de dados e um utilizador por serviço**, e RabbitMQ.
Ambos correm **fora** do Kubernetes, em Docker Compose (aula 03: `docker-compose.yml` do PostgreSQL; aula 05: RabbitMQ).

Cria uma pasta `infra/` ao lado do pom pai com estes dois ficheiros.

📄 **`infra/docker-compose.yml`**

```yaml
# Infraestrutura local para o desenvolvimento (aula 03: PostgreSQL + pgAdmin; aula 05: RabbitMQ).
# Arrancar:  docker compose up -d      Parar:  docker compose down      Apagar dados:  docker compose down -v
services:

  # PostgreSQL 17: uma única instância com UMA BASE DE DADOS e UM UTILIZADOR POR SERVIÇO (ver init-db.sql).
  postgres:
    image: postgres:17
    container_name: postgres_server
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      # Volume nomeado: os dados sobrevivem a "docker compose down" (só "down -v" os apaga).
      - pgdata:/var/lib/postgresql/data
      # Os scripts desta pasta correm UMA vez, quando o volume está vazio (primeiro arranque).
      - ./init-db.sql:/docker-entrypoint-initdb.d/init-db.sql:ro

  # pgAdmin: interface web para ver as tabelas (http://localhost:9999).
  pgadmin:
    image: dpage/pgadmin4
    container_name: pgadmin
    environment:
      PGADMIN_DEFAULT_EMAIL: admin@admin.com
      PGADMIN_DEFAULT_PASSWORD: admin
    ports:
      - "9999:80"
    depends_on:
      - postgres

  # RabbitMQ com interface de gestão (http://localhost:15672, utilizador user / password 123).
  rabbitmq:
    image: rabbitmq:3.12.9-management
    container_name: rabbit_container
    hostname: rabbit_server
    environment:
      RABBITMQ_DEFAULT_USER: user
      RABBITMQ_DEFAULT_PASS: "123"
    ports:
      - "5672:5672"      # AMQP (os serviços ligam aqui)
      - "15672:15672"    # interface de gestão
    volumes:
      - rabbitdata:/var/lib/rabbitmq

volumes:
  pgdata:
  rabbitdata:
```

📄 **`infra/init-db.sql`**

```sql
-- Cria uma base de dados e um utilizador por microserviço.
-- Cada utilizador só consegue ligar-se à SUA base de dados (database per service).

CREATE USER referenciacao_user WITH PASSWORD 'referenciacao_pass';
CREATE DATABASE referenciacao_db OWNER referenciacao_user;
REVOKE CONNECT ON DATABASE referenciacao_db FROM PUBLIC;
GRANT CONNECT ON DATABASE referenciacao_db TO referenciacao_user;

CREATE USER agendamento_user WITH PASSWORD 'agendamento_pass';
CREATE DATABASE agendamento_db OWNER agendamento_user;
REVOKE CONNECT ON DATABASE agendamento_db FROM PUBLIC;
GRANT CONNECT ON DATABASE agendamento_db TO agendamento_user;

CREATE USER lista_espera_user WITH PASSWORD 'lista_espera_pass';
CREATE DATABASE lista_espera_db OWNER lista_espera_user;
REVOKE CONNECT ON DATABASE lista_espera_db FROM PUBLIC;
GRANT CONNECT ON DATABASE lista_espera_db TO lista_espera_user;

CREATE USER tmrg_user WITH PASSWORD 'tmrg_pass';
CREATE DATABASE tmrg_db OWNER tmrg_user;
REVOKE CONNECT ON DATABASE tmrg_db FROM PUBLIC;
GRANT CONNECT ON DATABASE tmrg_db TO tmrg_user;

CREATE USER incumprimento_user WITH PASSWORD 'incumprimento_pass';
CREATE DATABASE incumprimento_db OWNER incumprimento_user;
REVOKE CONNECT ON DATABASE incumprimento_db FROM PUBLIC;
GRANT CONNECT ON DATABASE incumprimento_db TO incumprimento_user;

CREATE USER indicadores_user WITH PASSWORD 'indicadores_pass';
CREATE DATABASE indicadores_db OWNER indicadores_user;
REVOKE CONNECT ON DATABASE indicadores_db FROM PUBLIC;
GRANT CONNECT ON DATABASE indicadores_db TO indicadores_user;

CREATE USER utentes_user WITH PASSWORD 'utentes_pass';
CREATE DATABASE utentes_db OWNER utentes_user;
REVOKE CONNECT ON DATABASE utentes_db FROM PUBLIC;
GRANT CONNECT ON DATABASE utentes_db TO utentes_user;

CREATE USER notificacoes_user WITH PASSWORD 'notificacoes_pass';
CREATE DATABASE notificacoes_db OWNER notificacoes_user;
REVOKE CONNECT ON DATABASE notificacoes_db FROM PUBLIC;
GRANT CONNECT ON DATABASE notificacoes_db TO notificacoes_user;
```

### O que faz

- **`postgres`** — o servidor da aula 03, versão 17. Um **volume nomeado** (`pgdata`) mantém os dados quando o contentor é reiniciado.
- **`init-db.sql`** — corre **só no primeiro arranque** (quando o volume está vazio) e cria as 8 bases de dados/utilizadores. O `REVOKE CONNECT ... FROM PUBLIC` impede um serviço de ligar à BD de outro (*"sem permissões nas bases de dados dos outros"*, secção 4.1).
- **`pgadmin`** — interface web (como na aula 03) em `http://localhost:9999`.
- **`rabbitmq`** — imagem da aula 05, com interface de gestão em `http://localhost:15672` (utilizador `user`, password `123`). A porta **5672** é a AMQP, usada pelos serviços.

> ⚠️ **Já tens contentores das aulas?** Se já criaste `postgres_server`, `pgadmin` ou `rabbit_container` nas aulas, o `docker compose up` falha com *"container name is already in use"* (e as portas 5432/5672 podem estar ocupadas). Resolve com `docker stop postgres_server pgadmin rabbit_container` e **muda o `container_name`** neste ficheiro, ou apaga os antigos com `docker rm`. **Não apagues os volumes** se quiseres manter dados antigos.
>
> ⚠️ O `init-db.sql` só corre com o volume **vazio**. Se já tinhas um volume de PostgreSQL, usa outro nome de volume ou corre `docker compose down -v` (apaga os dados desse volume).

### Arrancar e verificar

```bash
cd infra
docker compose up -d          # arranca em segundo plano (aula 03)
docker compose ps             # os 3 contentores devem estar "running"
```

Verifica:

1. **PostgreSQL:** `docker exec postgres_server psql -U postgres -c '\l'` — deves ver `incumprimento_db` e `indicadores_db`.
2. **RabbitMQ:** abre `http://localhost:15672` (`user` / `123`). O separador *Overview* deve mostrar o nó a funcionar.

Comandos úteis (aula 03, slide 17): `docker compose logs`, `docker compose stop`, `docker compose start`, `docker compose down`.

## ✅ Checklist do capítulo 01

- [ ] `acesso-a-tempo/pom.xml` com as versões Boot 4.1.2 e Cloud 2025.0.3
- [ ] Módulos `historico` e `garantia-acesso` criados e listados em `<modules>`
- [ ] `docker compose ps` mostra postgres, pgadmin e rabbitmq a correr
- [ ] `incumprimento_db` e `indicadores_db` existem
- [ ] Interface do RabbitMQ acessível em `http://localhost:15672`
