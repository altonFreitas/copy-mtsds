package pt.estg.mtsds.acesso.modelo.configuration;

import org.springframework.amqp.core.DirectExchange;
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
 * RabbitMQ (slide 05): uma única TopicExchange para todos os eventos do
 * sistema. Cada serviço declara aqui só as filas que consome, ligadas por
 * routing key.
 *
 * DLQ (Runbook v2.1 §7.4): após 3 tentativas falhadas o container envia nack
 * requeue=false → RabbitMQ encaminha para a DLX → DLQ do serviço.
 */
@Configuration
public class RabbitConfig {

	public static final String EXCHANGE = "acesso.eventos";
	public static final String DLX = "acesso.eventos.dlx";

	@Bean
	public TopicExchange acessoEventos() {
		return new TopicExchange(EXCHANGE, true, false);
	}

	/** DirectExchange que recebe mensagens após esgotar as 3 tentativas. */
	@Bean
	public DirectExchange deadLetterExchange() {
		return new DirectExchange(DLX, true, false);
	}

	/**
	 * Container factory com 3 tentativas e backoff exponencial (1 s → 2 s → 4 s). O
	 * nome "rabbitListenerContainerFactory" é a convenção do Spring AMQP: todos
	 * os @RabbitListener herdam este comportamento sem configuração extra.
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
	 * Mensagens em JSON; o Spring Boot usa este conversor no RabbitTemplate e nos
	 * listeners.
	 */
	@Bean
	public MessageConverter messageConverter(ObjectMapper objectMapper) {
		return new Jackson2JsonMessageConverter(objectMapper);
	}
}
