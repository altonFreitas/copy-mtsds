# 10 — Juntar tudo: Docker, Eureka, Gateway e teste de ponta a ponta

> ⚠️ **v2.1 — Capítulo da versão antiga:** este capítulo mostra o código da `referencia/` (nomes antigos, `com.acessoatempo`). **Os nomes dos módulos no `docker-compose.yml` e nos logs são agora `garantia-acesso` (porta 8085) e `historico` (porta 8086). O `gestao-acesso` substitui o `tmrg`. Routing keys atualizadas para `prazo.*` e `caso.*`.** O **código correto para o repositório do grupo** está em `referencia-grupo/`. Ver `referencia-grupo/LEIA-ME.md` para as diferenças.

**Objetivo:** pôr os teus 2 serviços a conversar entre si pelo RabbitMQ, prepará-los para o Docker e saber o que pedir aos colegas.
**Baseado em:** aula 04 (Eureka, Gateway, Load Balancer) e aula 05 (RabbitMQ). O Kubernetes (aula 06) está no [capítulo 12](12-kubernetes-minikube.md).

## 10.1 Teste de ponta a ponta (os teus 2 serviços)

Com `docker compose up -d` e os dois serviços a correr (portas 8085 e 8086):

| Passo | Ação | Esperado |
|---|---|---|
| 1 | Publica 3× `episodio.inscrito` (episódios 101, 102, 103; `especialidade: CARDIOLOGIA`) e 1× `episodio.saida` (102) | `GET :8086/historico/episodios/{id}?esp=CARDIOLOGIA` → entradas 3, saídas 1, **tamanhoLista 2** |
| 2 | Publica `prazo.ultrapassado` (episódio 101) | `GET :8085/incumprimentos` → 1 caso `REGISTADO` (nível 1) |
| 3 | Publica **o mesmo** `prazo.ultrapassado` com o mesmo `eventoId` | Continua **1** caso (idempotência) |
| 4 | `PUT :8085/casos/1/alerta-diretor` | `DIRETOR_ALERTADO`, nível 2 |
| 5 | `POST :8085/casos/1/transferencias` (`{"instituicaoId":1}`) | **409** (prazo do diretor) |
| 6 | Publica `relogio.avancado` com `data` = daqui a 2 meses; repete o passo 5 | **202** |
| 7 | `PUT :8085/transferencias/1/resposta` `{"aceite":true}` | `ACEITE`; caso `RESOLVIDO` |
| 8 | Vê o log do `historico` | Recebeu `caso.aberto`, `transferencia.proposta`, `transferencia.aceite` e `caso.resolvido` publicados **pelo outro serviço** ✅ |
| 9 | Publica `episodio.inscrito` com `dados: {}` | Em ~7 s, 1 mensagem em `indicadores.eventos.dlq` |

O passo 8 é o importante: prova que **o que o `garantia-acesso` publica o `historico` consegue ler** (mesmo contrato JSON, serviços independentes).

> Estes passos foram executados com PostgreSQL e RabbitMQ reais: resultados conforme a tabela (o `caso.diretor-alertado` **não** chega ao `historico` porque não faz parte dos seus 17 eventos; chegam `agendamento` e `notificacoes`, dos colegas).

## 10.2 Dockerfile (um por serviço)

A proposta usa Docker Compose no desenvolvimento e Kubernetes na entrega. Ambos precisam de uma **imagem** de cada serviço. Cria um `Dockerfile` na pasta de cada módulo:

📄 **`incumprimento/Dockerfile`**

```
# Imagem do microserviço incumprimento. Pressupõe que o JAR já foi gerado com:  mvn -pl incumprimento -am package
# JRE 17 (a proposta usa JDK 17): só o necessário para executar, sem o compilador.
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copia o JAR executável gerado pelo spring-boot-maven-plugin (goal repackage).
COPY target/incumprimento-1.0-SNAPSHOT.jar app.jar

# Porta em que o serviço responde (documentação; quem publica a porta é o docker run / compose).
EXPOSE 8085

# Arranca a aplicação. As configurações (BD, RabbitMQ, Eureka) vêm de variáveis de ambiente.
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Para o `historico` é igual, mudando o nome do JAR e a porta (`8086`).

```bash
# na raiz do projeto (pasta com o pom pai)
mvn -pl garantia-acesso,historico -am package -DskipTests     # gera os JAR executáveis
docker build -t incumprimento:1.0 incumprimento
docker build -t indicadores:1.0 indicadores
```

- **`FROM eclipse-temurin:17-jre`** — só o JRE 17 (a proposta usa JDK 17); a imagem fica menor do que com o JDK completo.
- **`ENTRYPOINT ["java","-jar","app.jar"]`** — arranca o JAR executável (por isso o `repackage` do capítulo 01 é obrigatório).
- **Configuração por variáveis de ambiente** — o `application.yml` já lê `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `RABBIT_HOST`, `RABBIT_PORT`, `RABBIT_USER`, `RABBIT_PASSWORD`, `EUREKA_URL`. **Dentro de um contentor, `localhost` é o próprio contentor!** Para ligar ao PostgreSQL do Compose usa o **nome do serviço** (`DB_HOST=postgres`), não `localhost`.

Exemplo de entrada no `docker-compose.yml` (na rede do Compose, os serviços falam pelo nome):

```yaml
  incumprimento:
    image: incumprimento:1.0
    environment:
      DB_HOST: postgres
      RABBIT_HOST: rabbitmq
      EUREKA_URL: http://eureka:8761/eureka/
    ports:
      - "8085:8085"
    depends_on:
      - postgres
      - rabbitmq
```

> ✅ Verificado: o `docker build` das duas imagens funciona e os JAR arrancam com PostgreSQL e RabbitMQ reais. ⚠️ **Não testado:** correr os serviços *dentro* do Compose (precisa do Eureka/Gateway dos colegas).

## 10.3 O que a Gateway e o Eureka precisam de saber

**Eureka (já está ligado):** o `application.yml` tem `eureka.client.service-url.defaultZone` e `spring.application.name`. Quando o Eureka Server estiver a correr (`http://localhost:8761`), os teus serviços aparecem **registados como `INCUMPRIMENTO` e `INDICADORES`** (aula 04, *eureka_service 7/14*). Basta ter o Eureka Server ligado.

**Gateway:** quem fizer a Gateway (arranque, Parte 4/plataforma) tem de acrescentar estas rotas (aula 04, *api_gateway 3/4* e *eureka_service 4/14*, com `lb://` = *load balancer* pelo nome no Eureka):

| Path na Gateway | Destino |
|---|---|
| `/incumprimento/**`, `/casos/**`, `/transferencias/**`, `/instituicoes/**` | `lb://incumprimento` |
| `/indicadores/**` | `lb://indicadores` |

```java
// Exemplo no estilo da aula (GatewayConfig). NÃO testado aqui: é trabalho da Gateway.
.route("incumprimento-route", r -> r
        .path("/incumprimento/**", "/casos/**", "/transferencias/**", "/instituicoes/**")
        .uri("lb://incumprimento"))
.route("indicadores-route", r -> r.path("/indicadores/**").uri("lb://indicadores"))
```

> ⚠️ **Spring Cloud 2025.x:** o starter da Gateway que as aulas usam (`spring-cloud-starter-gateway`) foi **renomeado** nesta versão (`spring-cloud-starter-gateway-server-webflux`). Quem fizer a Gateway deve confirmar o nome certo no Maven Central.

**O que pedir aos colegas (Parte 4 e Parte 2):**

1. **Parte 4:** o **envelope comum** do evento e do erro (para eu usar o deles em vez da cópia local) e as rotas acima na Gateway.
2. **Parte 2 (`gestao-acesso`):** confirmar que os eventos `prazo.em-risco`, `prazo.ultrapassado-previsto`, `prazo.ultrapassado` e `relogio.avancado` levam em `dados` os campos `especialidade` e (no relógio) `data` (AAAA-MM-DD).
3. **Parte 1 e 2:** que `pedido.triado` leve `especialidade` e `dataPedido`, e que `episodio.inscrito`/`episodio.saida` levem `especialidade`.
4. **Parte 1 (`agendamento`):** vai consumir `caso.diretor-alertado` e `transferencia.aceite`: combinar o conteúdo de `dados`.

## 10.4 Documentar a API (secção "Documentação API" do relatório)

O repositório da disciplina tem a pasta `src/DocumentacaoAPI`. Para cada endpoint, a tabela do capítulo 05 (indicadores) e 09 (incumprimento) já tem **método, caminho, o que faz e códigos HTTP** (o formato do Anexo A da proposta). Acrescenta um **exemplo de pedido e de resposta** (copia-os do Postman).

## 10.5 O que falta (calendário)

| Tarefa | Quando (proposta, secção 5.4) | Estado |
|---|---|---|
| 8 serviços a correr na VM, integrados | 19/11 a 27/11 (Fase 1) | Os teus 2 ✅ (testados isolados) |
| `Deployment`, `ConfigMap`, `Secret`, réplicas e *health checks* no Minikube | 28/11 a 03/12 | ✅ Capítulo 12 (testado no Minikube) |
| Fase 2 (nível 2 completo, R5/R7, indicadores completos) | 28/11 a 03/12 | O nível 2 e os 4 indicadores já estão; falta afinar o cumprimento |
| Fase 3 (circuit breaker, tracing) | se houver tempo | — |

Para o Kubernetes, o que já deixaste preparado: **configuração por variáveis de ambiente** (vão para `ConfigMap`/`Secret`), **Actuator `/actuator/health`** (para as *probes*) e **imagem Docker** (10.2). Segue para o capítulo 12.

## 10.6 Perguntas para a defesa, com resposta

Responde **primeiro sem olhar**; depois confirma. As respostas com ✅ foram **confirmadas na execução** (PostgreSQL e RabbitMQ reais).

### 1. Como provaste que os teus dois serviços funcionam **juntos**?

✅ Com o teste de ponta a ponta da tabela 10.1: publiquei eventos no RabbitMQ e segui o resultado nos dois serviços. O passo decisivo é o **8**: os eventos que o `garantia-acesso` **publica** (`caso.aberto`, `transferencia.proposta`, `transferencia.aceite` e `caso.resolvido`) foram **lidos e consumidos pelo `historico`**, que é outro processo com outra base de dados. Isso prova que o **contrato JSON** dos eventos é partilhado e que os serviços só precisam de se entender pela mensagem, sem se conhecerem.

### 2. Porque é que os serviços comunicam por eventos e não por HTTP?

Porque quem publica **não precisa da resposta** e a falha de um serviço **não deve parar o outro** (RNF02). O `garantia-acesso` publica e segue em frente; se o `historico` estiver em baixo, as mensagens **ficam na fila** e são tratadas quando ele voltar. As únicas chamadas síncronas do sistema são as duas ao `utentes`.

### 3. O que faz cada linha do `Dockerfile`?

| Linha | O que faz |
|---|---|
| `FROM eclipse-temurin:17-jre` | Imagem base: Linux com o **JRE 17** (só para executar Java) |
| `WORKDIR /app` | Pasta de trabalho dentro do contentor |
| `COPY target/incumprimento-1.0-SNAPSHOT.jar app.jar` | Copia o JAR executável gerado pelo Maven para dentro da imagem |
| `EXPOSE 8085` | **Documenta** a porta do serviço (quem a publica é o `docker run`/compose) |
| `ENTRYPOINT ["java","-jar","app.jar"]` | O comando que arranca o serviço quando o contentor inicia |

(Aula 06: `FROM`, `EXPOSE`, `ADD/COPY` e `ENTRYPOINT`.)

### 4. Porque JRE e não JDK?

Para **executar** só é preciso o JRE; o JDK traz também o compilador e ferramentas de desenvolvimento. A imagem fica **mais pequena** (descarrega e arranca mais depressa) e com **menos superfície de ataque**. A compilação faz-se fora (Maven), antes de construir a imagem.

### 5. Porque é obrigatório o `repackage` no `pom.xml`?

✅ Sem o goal `repackage` do `spring-boot-maven-plugin`, o JAR gerado é um JAR **normal** (só com as nossas classes) e `java -jar` falha com *"no main manifest attribute"*. O `repackage` produz o **JAR executável** (*fat jar*), com as dependências e o servidor embebido. Descobri-o ao testar, porque não usamos o `spring-boot-starter-parent`, que o faz sozinho.

### 6. Porque não posso usar `localhost` para a base de dados dentro de um contentor?

Dentro de um contentor, **`localhost` é o próprio contentor**, não o teu computador nem o outro contentor. Para ligar ao PostgreSQL do Docker Compose usa-se o **nome do serviço** (`DB_HOST=postgres`) e, no Kubernetes, `host.minikube.internal` (capítulo 12). O erro típico é `Connection refused`.

### 7. Como é que o mesmo JAR funciona no computador, no Docker e no Kubernetes?

O `application.yml` usa **`${VARIÁVEL:valor-por-omissão}`**, por exemplo `${DB_HOST:localhost}`: se a variável de ambiente existir, usa-a; senão usa `localhost`. No computador não definimos nada (usa os valores por omissão); no Docker/Kubernetes definem-se as variáveis (compose, ConfigMap e Secret). **Código e configuração separados**: não se recompila para mudar de ambiente. ✅ Usei isto com portas diferentes nos testes (`DB_PORT`, `RABBIT_PORT`) sem tocar no código.

### 8. O que é o Eureka e o que acontece se não estiver a correr?

O **Eureka** é o **diretório de serviços** (*Discovery Service*): cada instância regista-se com o seu nome e envia *heartbeats* (a cada 30 s); se deixar de os enviar (90 s), é removida. A Gateway consulta-o para saber **onde estão** as instâncias e as balancear (aula 04).
✅ Sem Eureka a correr, o serviço **arranca e funciona na mesma**: só escreve avisos de `Connection refused`/`UnknownHostException` no log. Quando o Eureka aparecer, regista-se sozinho. Por isso o `application.yml` tem só o `defaultZone` e o `spring.application.name`.

### 9. O que é a Gateway e para que servem as rotas `lb://incumprimento`?

A **Gateway** é o **ponto de entrada único**: o cliente (o Postman) fala só com ela e ela encaminha cada pedido para o serviço certo, conforme o caminho (`/casos/**` → `garantia-acesso`). O prefixo **`lb://`** significa *load balanced*: o destino é o **nome do serviço no Eureka** e não um IP/porta fixos, por isso, com várias instâncias, os pedidos são repartidos entre elas (aula 04, *eureka_service 4/14*). Os serviços não precisam de saber nada da Gateway.

### 10. Porque é que o `caso.diretor-alertado` não chegou ao `historico`?

✅ Porque **não faz parte dos 17 eventos** que o `historico` consome (Anexo B da proposta). Esse evento é para o `agendamento` (reagendar) e as `notificacoes` (avisar o diretor). Um serviço só recebe os eventos a que **ligou a sua fila** por *binding*; é o desacoplamento: quem publica não escolhe os destinatários.

### 11. Que provas tens de idempotência e de Dead-Letter?

✅ **Idempotência** (passo 3): publiquei o **mesmo** `prazo.ultrapassado` duas vezes com o mesmo `eventoId` e ficou **um só** caso. **Dead-Letter** (passo 9): um `episodio.inscrito` sem `especialidade` falhou várias vezes (1 s, 2 s, 4 s entre tentativas), o log mostrou `Retries exhausted` e a mensagem ficou em `indicadores.eventos.dlq` (**1 mensagem**), sem bloquear a fila.

### 12. Para que serve o `relogio.avancado` no teste (passo 6)?

Para **simular a passagem do tempo**: sem ele, teria de esperar 5 dias úteis reais para demonstrar o nível 3. O `gestao-acesso` publica a nova data (só no perfil de demonstração) e o `garantia-acesso` passa a usá-la (`RelogioService`). É o **RNF07** (relógio configurável).

### 13. O que pedes aos colegas e porquê?

Tudo o que é **contrato partilhado**: o envelope comum dos eventos e dos erros (Parte 4), as rotas na Gateway, e os campos dentro de `dados` (`especialidade`, `dataPedido`, `data`). Sem acordo, cada serviço lê campos diferentes e **a integração falha em silêncio** (eventos inválidos acabam na DLQ). Por isso o capítulo 00 marca estes pontos com 🤝.

### 14. Docker Compose ou Kubernetes? O que está testado?

A proposta usa **Docker Compose no desenvolvimento** e **Kubernetes (Minikube) na entrega**. ✅ Testado: o `docker build` das duas imagens, os JAR a correr com PostgreSQL e RabbitMQ reais e (capítulo 12) os dois serviços no Minikube. **Não testado:** correr os teus serviços **dentro** do Compose (precisa do Eureka e da Gateway dos colegas).

---

## ✅ Checklist do capítulo 10

- [ ] Passos 1 a 9 da tabela 10.1 com o resultado esperado
- [ ] `docker build` das duas imagens a funcionar
- [ ] Pedidos feitos aos colegas (envelope, rotas, campos de `dados`)
