package pt.estg.mtsds.acesso.indicadores.configuration;

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
 * RabbitMQ (slide 05): uma única TopicExchange para todos os eventos do
 * sistema. Cada serviço declara aqui só as filas que consome, ligadas por
 * routing key. Este serviço tem uma fila com binding {@code #} (todos os
 * eventos) e uma Dead-Letter Queue para as mensagens que falham 3 tentativas.
 *
 * <p>
 * No repositório do grupo o {@code RabbitConfig} do {@code servico-modelo} já
 * declara a {@code DirectExchange} DLX e o
 * {@code rabbitListenerContainerFactory}; cada serviço só precisa de declarar a
 * sua fila, a DLQ e os bindings.
 */
@Configuration
public class RabbitConfig {

	public static final String EXCHANGE = "acesso.eventos";

	/**
	 * Dead-Letter Exchange: recebe as mensagens que não puderam ser processadas.
	 */
	public static final String DLX = "acesso.eventos.dlx";

	/** Fila deste serviço (uma por serviço, não por réplica). */
	public static final String QUEUE = "historico.eventos";

	/** Fila das mensagens mortas deste serviço. */
	public static final String DLQ = "historico.eventos.dlq";

	/** O historico ouve TODOS os eventos do sistema (Runbook v2.1, secção 9.6). */
	private static final String ROUTING_KEY_TUDO = "#";

	@Bean
	public TopicExchange acessoEventos() {
		return new TopicExchange(EXCHANGE, true, false);
	}

	/**
	 * Dead-Letter Exchange (direta: encaminha pela routing key igual ao nome da
	 * DLQ).
	 */
	@Bean
	public DirectExchange deadLetterExchange() {
		return new DirectExchange(DLX, true, false);
	}

	/**
	 * Fila principal. Uma mensagem rejeitada vai para a Dead-Letter Exchange, com a
	 * routing key igual ao nome da DLQ.
	 */
	@Bean
	public Queue filaEventos() {
		return QueueBuilder.durable(QUEUE).deadLetterExchange(DLX).deadLetterRoutingKey(DLQ).build();
	}

	@Bean
	public Queue filaMortos() {
		return QueueBuilder.durable(DLQ).build();
	}

	@Bean
	public Declarable ligacaoMortos() {
		return BindingBuilder.bind(filaMortos()).to(deadLetterExchange()).with(DLQ);
	}

	/** Um único binding com "#": a fila recebe todos os eventos do sistema. */
	@Bean
	public Declarables ligacoesEventos() {
		List<Declarable> ligacoes = new ArrayList<>();
		ligacoes.add(BindingBuilder.bind(filaEventos()).to(acessoEventos()).with(ROUTING_KEY_TUDO));
		return new Declarables(ligacoes);
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
	 * Mensagens em JSON; o Spring Boot usa este conversor no RabbitTemplate e nos
	 * listeners.
	 */
	@Bean
	public MessageConverter messageConverter(ObjectMapper objectMapper) {
		return new Jackson2JsonMessageConverter(objectMapper);
	}
}
