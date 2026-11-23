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
     * {@code Jackson2JsonMessageConverter}; no Spring Boot 4 o Jackson é a versão 3 e a classe
     * equivalente chama-se {@code JacksonJsonMessageConverter}.
     */
    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
