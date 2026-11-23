package pt.estg.mtsds.acesso.incumprimento.configuration;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Configuração do RabbitMQ do serviço {@code garantia-acesso}.
 *
 * <p>
 * Este serviço é Producer E Consumer: publica 6 eventos na exchange do sistema
 * e consome 5. Declara a exchange, a fila, os bindings das routing keys que
 * consome, a Dead-Letter Exchange/Queue e o container factory com 3 tentativas.
 * * <p>
 * No repositório do grupo, o {@code RabbitConfig} do {@code servico-modelo} já
 * declara a {@code DirectExchange} DLX e o
 * {@code rabbitListenerContainerFactory}; cada serviço só precisa de declarar a
 * sua fila, a DLQ e os bindings.
 */
@Configuration
public class RabbitConfig {

	/**
	 * Exchange comum a todo o sistema (TopicExchange). Todos os serviços publicam e
	 * ouvem nesta.
	 */
	public static final String EXCHANGE = "acesso.eventos";

	/** Dead-Letter Exchange: recebe as mensagens que falharam 3 vezes. */
	public static final String DLX = "acesso.eventos.dlx";

	/** Fila deste serviço (uma fila por SERVIÇO, não por réplica). */
	public static final String QUEUE = "garantia-acesso.eventos";

	/** Fila das mensagens mortas deste serviço. */
	public static final String DLQ = "garantia-acesso.eventos.dlq";

	/** Os 5 eventos que o serviço consome (Runbook v2.1, secção 9.5). */
	public static final String[] ROUTING_KEYS = {"prazo.em-risco", "prazo.ultrapassado-previsto", "prazo.ultrapassado",
			"consulta.agendada", "relogio.avancado"};

	@Bean
	public TopicExchange exchange() {
		return new TopicExchange(EXCHANGE, true, false);
	}

	@Bean
	public DirectExchange deadLetterExchange() {
		return new DirectExchange(DLX, true, false);
	}

	/**
	 * Fila principal: mensagens rejeitadas vão para a DLX com a routing key = nome
	 * da DLQ.
	 */
	@Bean
	public Queue queue() {
		return QueueBuilder.durable(QUEUE).deadLetterExchange(DLX).deadLetterRoutingKey(DLQ).build();
	}

	@Bean
	public Queue deadLetterQueue() {
		return QueueBuilder.durable(DLQ).build();
	}

	@Bean
	public Declarable deadLetterBinding() {
		return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DLQ);
	}

	/**
	 * Um binding por routing key: a fila só recebe os eventos que interessam a este
	 * serviço.
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
	 * Container factory com 3 tentativas e backoff exponencial (1 s → 2 s → 4 s).
	 * Ao fim das 3 tentativas, a exceção propaga-se, o Spring AMQP envia nack sem
	 * requeue e o RabbitMQ encaminha a mensagem para a DLX → DLQ.
	 *
	 * <p>
	 * No repositório do grupo este bean está no {@code servico-modelo}; aqui
	 * repetimo-lo para o projeto pessoal ser autónomo.
	 */
	@Bean
	public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
			MessageConverter messageConverter) {

		SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3);

		ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
		backOff.setInitialInterval(1_000);
		backOff.setMultiplier(2.0);
		backOff.setMaxInterval(4_000);

		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setRetryPolicy(retryPolicy);
		retryTemplate.setBackOffPolicy(backOff);

		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setMessageConverter(messageConverter);
		factory.setDefaultRequeueRejected(false);
		factory.setRetryTemplate(retryTemplate);
		return factory;
	}

	/**
	 * Converte objetos Java em JSON e vice-versa (o ObjectMapper do Spring, com o
	 * módulo das datas).
	 */
	@Bean
	public MessageConverter messageConverter(ObjectMapper objectMapper) {
		return new Jackson2JsonMessageConverter(objectMapper);
	}
}
