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

    /** Converte objetos Java em JSON e vice-versa (Spring Boot 4 = Jackson 3). */
    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
