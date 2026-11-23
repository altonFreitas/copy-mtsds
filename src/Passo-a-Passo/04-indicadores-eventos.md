# 04 — `historico`: eventos (RabbitMQ)

> ⚠️ **v2.1 — Capítulo da versão antiga:** este capítulo mostra o código da `referencia/` (nomes antigos, `com.acessoatempo`). **O binding mudou: em vez de 17 routing keys específicas, usa `#` (todos os eventos). O listener chama `HistoricoService.guardar()` em vez de `IndicadoresService.processar()`. Routing keys: `prazo.em-risco` (antes `tmrg.risco-detetado`), `prazo.ultrapassado` (antes `tmrg.incumprido`).** O **código correto para o repositório do grupo** está em `referencia-grupo/`. Ver `referencia-grupo/LEIA-ME.md` para as diferenças.

**Objetivo:** o serviço **consome** os 17 eventos e atualiza os contadores.
**Baseado em:** aula 05 (RabbitMQ: `MQConfig`, `Queue`, `TopicExchange`, `Binding`, `@RabbitListener`) e aula 04 (`@Service`, `@Transactional`).

## 4.1 Conceitos (aula 05) aplicados ao projeto

| Conceito (aula 05) | No nosso sistema |
|---|---|
| **Producer** | Quem publica um evento (ex.: o `garantia-acesso`) |
| **Consumer** | Quem recebe (aqui: o `historico`) |
| **Exchange** | `acesso.eventos`, do tipo **Topic**: recebe todas as mensagens do sistema |
| **Routing key** | O tipo do evento, p.ex. `episodio.inscrito` |
| **Queue** | `indicadores.eventos`: uma fila **por serviço** (não por réplica) |
| **Binding** | Liga a fila à exchange para **cada** routing key que interessa ao serviço |
| **Ack** | Confirmação de que o evento foi processado |
| **DLX / DLQ** | *Dead-Letter Exchange/Queue*: onde ficam as mensagens que falharam |

```text
Producer ──routing key──► [Exchange acesso.eventos] ──binding──► [Fila indicadores.eventos] ──► Listener
                                                                         │ falhou 3 vezes
                                                                         ▼
                                                      [DLX acesso.eventos.dlx] ──► [indicadores.eventos.dlq]
```

## 4.2 A mensagem (evento)

No package `messages`. É o equivalente ao `OrderConfirmationMessage` da aula 05 (em vez de "DTO", chama-se **Message**).

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/messages/EventoMessage.java`**

```java
package com.acessoatempo.indicadores.messages;

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

> 🤝 **Contrato partilhado.** Esta classe tem de ser **igual em todos os serviços** (e a Parte 4 entrega o envelope comum). Se vires que a deles é diferente, usa a deles.
> Regra da proposta: **só identificadores e datas, nunca dados pessoais** (secção 4.5).

## 4.3 Configuração do RabbitMQ

No package `config`. Equivale ao `MQConfig` da aula 05, com duas diferenças: tem **vários bindings** (um por evento) e a **Dead-Letter**.

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/config/MQConfig.java`**

```java
package com.acessoatempo.indicadores.config;

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
 * Configuração do RabbitMQ do serviço {@code indicadores} (equivale à classe {@code MQConfig} da aula).
 *
 * <p>Cria (se não existirem): a exchange do sistema, a fila deste serviço, as ligações (bindings) por
 * routing key e a Dead-Letter Exchange/Queue onde ficam as mensagens que falharam 3 vezes.
 */
@Configuration
public class MQConfig {

    /** Exchange comum a todo o sistema: TopicExchange onde todos os serviços publicam. */
    public static final String EXCHANGE = "acesso.eventos";

    /** Dead-Letter Exchange: recebe as mensagens que não puderam ser processadas. */
    public static final String DLX = "acesso.eventos.dlx";

    /** Fila deste serviço (uma fila por SERVIÇO, não por réplica). */
    public static final String QUEUE = "indicadores.eventos";

    /** Fila das mensagens mortas deste serviço. */
    public static final String DLQ = "indicadores.eventos.dlq";

    /** Os 17 eventos que o serviço consome (Anexo B da proposta). */
    public static final String[] ROUTING_KEYS = {
            "pedido.referenciado", "pedido.triado", "prioridade.reavaliada", "pedido.devolvido",
            "episodio.inscrito",
            "consulta.agendada", "consulta.realizada", "consulta.falta-registada",
            "tmrg.incumprimento-previsto", "tmrg.risco-detetado", "tmrg.incumprido",
            "incumprimento.registado",
            "transferencia.proposta", "transferencia.aceite", "transferencia.recusada",
            "incumprimento.resolvido",
            "episodio.saida"
    };

    /** Exchange principal. Durável por omissão: sobrevive a um reinício do RabbitMQ. */
    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE);
    }

    /** Dead-Letter Exchange (direta: encaminha pela routing key = nome da DLQ). */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX);
    }

    /** Fila principal: se uma mensagem for rejeitada, vai para a DLX com a routing key = nome da DLQ. */
    @Bean
    public Queue queue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ)
                .build();
    }

    /** Fila das mensagens mortas. */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    /** Liga a DLQ à DLX. */
    @Bean
    public Declarable deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DLQ);
    }

    /**
     * Liga a fila à exchange uma vez por cada routing key. Na aula havia um único {@code Binding};
     * aqui usa-se {@code Declarables} para declarar vários de uma vez.
     */
    @Bean
    public Declarables eventBindings() {
        List<Declarable> bindings = new ArrayList<>();
        for (String key : ROUTING_KEYS) {
            bindings.add(BindingBuilder.bind(queue()).to(exchange()).with(key));
        }
        return new Declarables(bindings);
    }

    /**
     * Converte objetos Java em JSON (e vice-versa). Nas aulas (Spring Boot 3) usava-se
     * {@code Jackson2JsonMessageConverter}; no Spring Boot 3.5 o Jackson é a versão 3 e a classe
     * equivalente chama-se {@code JacksonJsonMessageConverter}.
     */
    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
```

**Para a defesa:**

- **Os `@Bean` declaram** a exchange, as filas e os bindings; o Spring cria-os no RabbitMQ **se não existirem** (aula 05: *"as Exchanges e Queues serão criadas automaticamente"*).
- **`TopicExchange`** permite ligar filas por routing key. É a escolha da proposta (secção 4.1) para cada serviço ouvir **só** os eventos que lhe interessam.
- **`Declarables`** — a aula declarava um só `Binding`; aqui há 17, por isso usa-se este agrupador.
- **Dead-Letter:** a fila principal diz `deadLetterExchange(DLX)`; uma mensagem **rejeitada** vai para a DLX e daí para a `…dlq`. A routing key da DLQ é o **nome da própria fila**, para uma DLQ **não** receber as falhas dos outros serviços.
- **`JacksonJsonMessageConverter`** converte o objeto Java em JSON e vice-versa. Na aula chama-se `Jackson2JsonMessageConverter`; em Spring Boot 3.5 mudou o nome porque o Jackson passou para a versão 3.
- O Spring Boot cria automaticamente o `RabbitTemplate` e o contentor do listener **usando este conversor**: não é preciso criar o `AmqpTemplate` à mão como na aula.

## 4.4 O serviço: processar os eventos

No package `services`. Aqui está a lógica: **o controller (e o listener) não decidem nada; decide o serviço** (aula 03, *PostgreSQL 9/20*).

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/services/IndicadoresService.java`**

```java
package com.acessoatempo.indicadores.services;

import com.acessoatempo.indicadores.data.CumprimentoProjection;
import com.acessoatempo.indicadores.data.EventosProcessadosRepository;
import com.acessoatempo.indicadores.data.IndicadoresRepository;
import com.acessoatempo.indicadores.dtos.CumprimentoDTO;
import com.acessoatempo.indicadores.dtos.ListaIndicadorDTO;
import com.acessoatempo.indicadores.dtos.ProjecaoDTO;
import com.acessoatempo.indicadores.dtos.TriagemDTO;
import com.acessoatempo.indicadores.exceptions.EventoInvalidoException;
import com.acessoatempo.indicadores.exceptions.ParametroInvalidoException;
import com.acessoatempo.indicadores.messages.EventoMessage;
import com.acessoatempo.indicadores.models.EventoProcessado;
import com.acessoatempo.indicadores.models.IndicadorSemanal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.List;

/**
 * Regras de negócio do serviço {@code indicadores}: (1) atualizar os contadores a partir dos eventos
 * e (2) calcular os indicadores pedidos pelos endpoints. Como na aula, o controller só recebe pedidos e
 * devolve respostas; a lógica fica aqui.
 */
@Service
public class IndicadoresService {

    private static final Logger log = LoggerFactory.getLogger(IndicadoresService.class);

    @Autowired
    private IndicadoresRepository indicadoresRepository;

    @Autowired
    private EventosProcessadosRepository eventosProcessadosRepository;

    // ------------------------------------------------------------------------------------------
    // 1) Processamento de eventos (chamado pelo EventosListener)
    // ------------------------------------------------------------------------------------------

    /**
     * Processa um evento. {@code @Transactional}: a atualização do contador e o registo do evento como
     * "processado" acontecem na mesma transação; ou ficam os dois gravados ou nenhum.
     */
    @Transactional
    public void processar(EventoMessage evento) {
        if (evento == null || evento.getEventoId() == null || evento.getTipo() == null
                || evento.getOcorridoEm() == null) {
            throw new EventoInvalidoException("Evento sem eventoId, tipo ou ocorridoEm");
        }

        // Idempotência: o RabbitMQ pode entregar o mesmo evento mais do que uma vez.
        if (eventosProcessadosRepository.existsById(evento.getEventoId())) {
            log.info("Evento {} ({}) já processado: ignorado", evento.getEventoId(), evento.getTipo());
            return;
        }

        switch (evento.getTipo()) {
            case "episodio.inscrito" -> {
                IndicadorSemanal linha = linhaDe(evento);
                linha.setEntradas(linha.getEntradas() + 1);
                indicadoresRepository.save(linha);
            }
            case "episodio.saida" -> {
                IndicadorSemanal linha = linhaDe(evento);
                linha.setSaidas(linha.getSaidas() + 1);
                indicadoresRepository.save(linha);
            }
            case "tmrg.incumprido" -> {
                IndicadorSemanal linha = linhaDe(evento);
                linha.setIncumpridos(linha.getIncumpridos() + 1);
                indicadoresRepository.save(linha);
            }
            case "pedido.triado" -> {
                IndicadorSemanal linha = linhaDe(evento);
                LocalDate dataPedido = dataDe(evento, "dataPedido");
                long dias = ChronoUnit.DAYS.between(dataPedido, evento.getOcorridoEm().toLocalDate());
                linha.setTriagens(linha.getTriagens() + 1);
                linha.setDiasTriagemTotal(linha.getDiasTriagemTotal() + Math.max(dias, 0));
                indicadoresRepository.save(linha);
            }
            default ->
                // Os restantes eventos (pedido.referenciado, consulta.*, transferencia.*...) fazem parte
                // do contrato dos 17 eventos, mas ainda não alteram nenhum contador (Fase 2).
                log.debug("Evento {} sem efeito nos contadores atuais", evento.getTipo());
        }

        eventosProcessadosRepository.save(
                new EventoProcessado(evento.getEventoId(), evento.getTipo(), LocalDateTime.now()));
    }

    /** Encontra (ou cria) a linha da especialidade e semana a que o evento diz respeito. */
    private IndicadorSemanal linhaDe(EventoMessage evento) {
        Object esp = evento.getDados().get("especialidade");
        if (esp == null || esp.toString().isBlank()) {
            throw new EventoInvalidoException(
                    "Evento " + evento.getTipo() + " sem 'especialidade' em dados");
        }
        String semana = semanaIso(evento.getOcorridoEm());
        return indicadoresRepository.findByEspecialidadeAndSemana(esp.toString(), semana)
                .orElseGet(() -> new IndicadorSemanal(esp.toString(), semana));
    }

    private LocalDate dataDe(EventoMessage evento, String campo) {
        Object valor = evento.getDados().get(campo);
        if (valor == null) {
            throw new EventoInvalidoException("Evento " + evento.getTipo() + " sem '" + campo + "' em dados");
        }
        try {
            return LocalDate.parse(valor.toString());
        } catch (Exception e) {
            throw new EventoInvalidoException("Data inválida em '" + campo + "': " + valor);
        }
    }

    /** Converte uma data na semana ISO, no formato 2026-W47. */
    static String semanaIso(LocalDateTime data) {
        int ano = data.get(IsoFields.WEEK_BASED_YEAR);
        int semana = data.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%d-W%02d", ano, semana);
    }

    // ------------------------------------------------------------------------------------------
    // 2) Cálculo dos indicadores (chamado pelo IndicadoresController)
    // ------------------------------------------------------------------------------------------

    /** Tamanho da lista, entradas e saídas. Sem {@code semana}, usa todo o período. */
    @Transactional(readOnly = true)
    public ListaIndicadorDTO lista(String especialidade, String semana) {
        exigirEspecialidade(especialidade);
        if (semana != null && !semana.matches("\\d{4}-W\\d{2}")) {
            throw new ParametroInvalidoException("Semana inválida: use o formato 2026-W47");
        }
        List<IndicadorSemanal> linhas = indicadoresRepository.findByEspecialidade(especialidade);

        long entradas = 0;
        long saidas = 0;
        long entradasTotal = 0;
        long saidasTotal = 0;
        for (IndicadorSemanal l : linhas) {
            // Comparar texto "2026-W05" funciona porque o formato é ordenável alfabeticamente.
            if (semana == null || l.getSemana().compareTo(semana) <= 0) {
                entradasTotal += l.getEntradas();
                saidasTotal += l.getSaidas();
            }
            if (semana == null || l.getSemana().equals(semana)) {
                entradas += l.getEntradas();
                saidas += l.getSaidas();
            }
        }
        return new ListaIndicadorDTO(especialidade, semana, entradas, saidas, entradasTotal - saidasTotal);
    }

    /** Taxa de cumprimento por especialidade num período (ano "2026" ou semana "2026-W47"). */
    @Transactional(readOnly = true)
    public List<CumprimentoDTO> cumprimento(String periodo) {
        if (periodo == null || !periodo.matches("\\d{4}(-W\\d{2})?")) {
            throw new ParametroInvalidoException("Período inválido: use um ano (2026) ou uma semana (2026-W47)");
        }
        return indicadoresRepository.cumprimentoPorEspecialidade(periodo).stream()
                .map(this::paraCumprimentoDTO)
                .toList();
    }

    private CumprimentoDTO paraCumprimentoDTO(CumprimentoProjection p) {
        long entradas = p.getEntradas() == null ? 0 : p.getEntradas();
        long incumpridos = p.getIncumpridos() == null ? 0 : p.getIncumpridos();
        double taxa = entradas == 0 ? 100.0 : 100.0 * (entradas - incumpridos) / entradas;
        return new CumprimentoDTO(p.getEspecialidade(), entradas, incumpridos, arredondar(taxa));
    }

    /** Tempo médio entre o pedido e a triagem, por especialidade. */
    @Transactional(readOnly = true)
    public TriagemDTO triagem(String especialidade) {
        exigirEspecialidade(especialidade);
        long triagens = 0;
        long dias = 0;
        for (IndicadorSemanal l : indicadoresRepository.findByEspecialidade(especialidade)) {
            triagens += l.getTriagens();
            dias += l.getDiasTriagemTotal();
        }
        double media = triagens == 0 ? 0.0 : (double) dias / triagens;
        return new TriagemDTO(especialidade, triagens, arredondar(media));
    }

    /** Projeção do tamanho da lista daqui a {@code semanas} semanas, com base nas médias semanais. */
    @Transactional(readOnly = true)
    public ProjecaoDTO projecao(String especialidade, int semanas) {
        exigirEspecialidade(especialidade);
        if (semanas < 1 || semanas > 52) {
            throw new ParametroInvalidoException("O número de semanas deve estar entre 1 e 52");
        }
        List<IndicadorSemanal> linhas = indicadoresRepository.findByEspecialidade(especialidade);
        long entradas = linhas.stream().mapToLong(IndicadorSemanal::getEntradas).sum();
        long saidas = linhas.stream().mapToLong(IndicadorSemanal::getSaidas).sum();
        long tamanhoAtual = entradas - saidas;
        double mediaEntradas = linhas.isEmpty() ? 0.0 : (double) entradas / linhas.size();
        double mediaSaidas = linhas.isEmpty() ? 0.0 : (double) saidas / linhas.size();
        double projetado = Math.max(0.0, tamanhoAtual + semanas * (mediaEntradas - mediaSaidas));
        return new ProjecaoDTO(especialidade, tamanhoAtual, arredondar(mediaEntradas),
                arredondar(mediaSaidas), semanas, arredondar(projetado));
    }

    private void exigirEspecialidade(String especialidade) {
        if (especialidade == null || especialidade.isBlank()) {
            throw new ParametroInvalidoException("O parâmetro 'esp' (especialidade) é obrigatório");
        }
    }

    private double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
```

O ficheiro tem **duas metades**: *1) Processamento de eventos* (agora) e *2) Cálculo dos indicadores* (usado no capítulo 05). Escreve-o todo agora; só entenderás a segunda metade ao ver o controller.

**O método `processar`, passo a passo:**

1. **Valida** o evento (sem `eventoId`, `tipo` ou `ocorridoEm` → `EventoInvalidoException`).
2. **Idempotência:** se o `eventoId` já foi processado, **ignora**.
3. **`switch` pelo tipo:** soma 1 ao contador certo, na linha da (especialidade, semana).
4. Grava o `eventoId` como processado.

**`@Transactional`** (aula 03, *PostgreSQL 9/20*): o contador e o registo "evento processado" são gravados **na mesma transação**; ou ficam os dois ou nenhum. Sem isto, uma falha a meio contaria o evento e depois repetiria.

**A semana ISO** (`2026-W47`): `IsoFields.WEEK_BASED_YEAR` e `WEEK_OF_WEEK_BASED_YEAR`. Atenção: 29/12/2025 já pertence à semana 1 de **2026**; por isso se usa o "ano da semana" e não `getYear()`.

**Evento inválido ≠ erro HTTP:** `linhaDe(...)` lança `EventoInvalidoException` se faltar `especialidade`. Isso faz o listener falhar → o RabbitMQ repete → vai para a DLQ. É preferível a ignorar o erro em silêncio (perderia dados sem ninguém saber).

## 4.5 O listener (Consumer)

No package `listeners`. É o `OrderConfirmationListener` da aula 05.

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/listeners/EventosListener.java`**

```java
package com.acessoatempo.indicadores.listeners;

import com.acessoatempo.indicadores.config.MQConfig;
import com.acessoatempo.indicadores.messages.EventoMessage;
import com.acessoatempo.indicadores.services.IndicadoresService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Consumer do RabbitMQ (equivale ao {@code OrderConfirmationListener} da aula). O método anotado com
 * {@code @RabbitListener} é executado sempre que chega uma mensagem à fila indicada.
 *
 * <p>Se o método terminar normalmente, o Spring envia o ACK ao RabbitMQ. Se lançar uma exceção, o Spring
 * tenta de novo (3 vezes, ver application.yml) e depois a mensagem vai para a Dead-Letter Queue.
 */
@Component
public class EventosListener {

    private static final Logger log = LoggerFactory.getLogger(EventosListener.class);

    @Autowired
    private IndicadoresService indicadoresService;

    @RabbitListener(queues = MQConfig.QUEUE)
    public void onEvento(EventoMessage evento) {
        log.info("Evento recebido: tipo={} episodioId={} eventoId={}",
                evento.getTipo(), evento.getEpisodioId(), evento.getEventoId());
        indicadoresService.processar(evento);
    }
}
```

- **`@Component`** regista a classe como bean do Spring.
- **`@RabbitListener(queues = ...)`** — este método corre sempre que chega uma mensagem à fila.
- O Spring **converte o JSON em `EventoMessage`** usando o `MessageConverter` e o **tipo do parâmetro** do método. Por isso um serviço pode receber mensagens produzidas por outros, mesmo que a classe do produtor esteja noutro package.
- **Ack automático:** se o método termina sem erro → ack. Se lança exceção → repete → DLQ (configurado no `application.yml`).

## 4.6 Testar com o RabbitMQ

1. Arranca o serviço. Na interface (`http://localhost:15672`) → **Queues**: aparecem `indicadores.eventos` e `indicadores.eventos.dlq`; em **Exchanges** aparece `acesso.eventos` (tipo *topic*).
2. Em **Exchanges → acesso.eventos → Publish message**:
   - *Routing key:* `episodio.inscrito`
   - *Properties:* `content_type` = `application/json`
   - *Payload:*
     ```json
     {"eventoId":"teste-001","tipo":"episodio.inscrito","episodioId":1,"ocorridoEm":"2026-11-17T10:00:00","dados":{"especialidade":"CARDIOLOGIA"}}
     ```
3. No log aparece `Evento recebido: tipo=episodio.inscrito ...`. Publica o **mesmo** payload outra vez: aparece `já processado: ignorado`.
4. **Teste da Dead-Letter:** publica `episodio.inscrito` com `"dados":{}` e `eventoId` novo. O log mostra várias tentativas espaçadas (1 s, 2 s, 4 s) e `Retries exhausted`; a mensagem fica em **`indicadores.eventos.dlq`**.

> ℹ️ **Observação (testada):** com Spring Boot 3.5, `max-attempts: 3` resultou em **1 tentativa inicial + 3 repetições = 4 execuções** do listener (a Boot 4 passou a usar o *retry* do Spring Framework 7, onde o valor conta repetições). Se quiseres exatamente 3 execuções no total, usa `max-attempts: 2` e confirma no log.

Os dados vão para a BD; podes ver as linhas no pgAdmin ou com `docker exec postgres_server psql -U indicadores_user -d indicadores_db -c 'select * from indicadores_semanais;'`.

## ✅ Checklist do capítulo 04

- [ ] Fila `indicadores.eventos`, DLQ e exchange `acesso.eventos` visíveis no RabbitMQ
- [ ] Um evento publicado manualmente atualiza a tabela `indicadores_semanais`
- [ ] O mesmo evento repetido é ignorado
- [ ] Um evento inválido acaba na `indicadores.eventos.dlq`
- [ ] Sabes explicar: exchange/queue/binding/routing key, ack, DLX, idempotência, `@Transactional`
