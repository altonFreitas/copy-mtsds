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
 * Configuração central do RabbitMQ, partilhada por todos os serviços do
 * sistema.
 *
 * <h2>O que declara</h2>
 * <ol>
 * <li><b>TopicExchange</b> {@code acesso.eventos} — a única exchange do
 * sistema; todos os serviços publicam aqui e cada um declara a sua própria fila
 * ligada só às routing keys que quer receber.</li>
 * <li><b>DirectExchange</b> {@code acesso.eventos.dlx} (Dead-Letter Exchange) —
 * recebe as mensagens que falharam todas as tentativas; encaminha-as para a
 * fila morta ({@code .dlq}) de cada serviço pela routing key igual ao nome da
 * DLQ.</li>
 * <li><b>RabbitListenerContainerFactory</b> com 3 tentativas — bean com o nome
 * que o Spring AMQP procura por convenção; todos os {@code @RabbitListener}
 * herdam o comportamento sem código adicional.</li>
 * </ol>
 *
 * <h2>O que CADA serviço ainda tem de declarar (no seu próprio
 * RabbitConfig)</h2>
 *
 * <pre>
 * // 1. Fila principal — diz ao RabbitMQ para onde mandar mensagens falhadas
 * Queue fila = QueueBuilder.durable("garantia-acesso.eventos").deadLetterExchange(RabbitConfig.DLX)
 * 		.deadLetterRoutingKey("garantia-acesso.eventos.dlq").build();
 *
 * // 2. Fila morta — guarda as mensagens que falharam 3 vezes
 * Queue dlq = QueueBuilder.durable("garantia-acesso.eventos.dlq").build();
 *
 * // 3. Ligação DLQ → DLX (routing key = nome exato da DLQ)
 * Binding dlqBinding = BindingBuilder.bind(dlq).to(deadLetterExchange).with("garantia-acesso.eventos.dlq");
 *
 * // 4. Bindings da fila principal → routing keys que o serviço consome
 * </pre>
 *
 * <h2>Como funciona o retry (Runbook v2.1, secção 7.4)</h2>
 * <ol>
 * <li>O {@code @RabbitListener} recebe a mensagem e chama o serviço de
 * negócio.</li>
 * <li>Se lançar exceção, o Spring Retry volta a tentar (backoff: 1 s → 2 s → 4
 * s).</li>
 * <li>Após 3 tentativas falhadas, a exceção propaga-se para o container.</li>
 * <li>O container envia {@code nack} com {@code requeue=false}.</li>
 * <li>O RabbitMQ vê o cabeçalho {@code x-dead-letter-exchange} na fila e
 * reencaminha para a DLX com a routing key
 * {@code x-dead-letter-routing-key}.</li>
 * <li>A DLX (DirectExchange) envia a mensagem para a DLQ do serviço.</li>
 * <li>A mensagem fica na DLQ até ser reenviada à mão pela consola
 * (localhost:15672).</li>
 * </ol>
 */
@Configuration
public class RabbitConfig {

	/**
	 * Nome da TopicExchange do sistema. Todos os serviços usam esta constante para
	 * publicar e para declarar os seus bindings de filas.
	 */
	public static final String EXCHANGE = "acesso.eventos";

	/**
	 * Nome da Dead-Letter Exchange (DirectExchange). Cada serviço usa esta
	 * constante ao construir a sua fila principal:
	 * {@code QueueBuilder.durable(QUEUE).deadLetterExchange(DLX)...}
	 */
	public static final String DLX = "acesso.eventos.dlx";

	/**
	 * A única TopicExchange do sistema. {@code durable=true} — sobrevive a
	 * reinicios do RabbitMQ. {@code autoDelete=false} — não apaga quando não há
	 * consumidores.
	 */
	@Bean
	public TopicExchange acessoEventos() {
		return new TopicExchange(EXCHANGE, true, false);
	}

	/**
	 * Dead-Letter Exchange (DirectExchange).
	 *
	 * <p>
	 * Porquê DirectExchange? Encaminha pela routing key exata. O RabbitMQ usa o
	 * {@code x-dead-letter-routing-key} configurado na fila (= nome da DLQ). Não
	 * são precisos padrões ({@code *}, {@code #}) como na TopicExchange.
	 */
	@Bean
	public DirectExchange deadLetterExchange() {
		return new DirectExchange(DLX, true, false);
	}

	/**
	 * Container factory com 3 tentativas e backoff exponencial.
	 *
	 * <p>
	 * O nome do bean {@code rabbitListenerContainerFactory} é a convenção que o
	 * Spring AMQP procura para todos os {@code @RabbitListener}. Não é preciso
	 * escrever {@code containerFactory="..."} em nenhum listener.
	 *
	 * <p>
	 * Parâmetros do retry:
	 * <ul>
	 * <li>{@code maxAttempts = 3} — 1.ª tentativa + 2 repetições</li>
	 * <li>{@code initialInterval = 1 000 ms} — espera antes da 2.ª tentativa</li>
	 * <li>{@code multiplier = 2.0} — cada espera duplica (1 s → 2 s → 4 s)</li>
	 * <li>{@code maxInterval = 4 000 ms} — limite máximo de espera</li>
	 * </ul>
	 *
	 * <p>
	 * {@code setDefaultRequeueRejected(false)} — após esgotar as tentativas, o
	 * container envia {@code nack requeue=false}; o RabbitMQ encaminha para a DLX e
	 * daí para a DLQ do serviço.
	 *
	 * @param connectionFactory
	 *            injetada pelo Spring Boot (properties {@code spring.rabbitmq.*})
	 * @param messageConverter
	 *            o bean {@link #messageConverter(ObjectMapper)} desta classe
	 */
	@Bean
	public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
			MessageConverter messageConverter) {

		// 3 tentativas no total (1.ª + 2 repetições)
		SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3);

		// Backoff exponencial: 1 s → 2 s → 4 s
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
		// após 3 tentativas: nack sem requeue → RabbitMQ envia para DLX → DLQ
		factory.setDefaultRequeueRejected(false);
		factory.setRetryTemplate(retryTemplate);
		return factory;
	}

	/**
	 * Converte objetos Java em JSON e vice-versa.
	 *
	 * <p>
	 * O {@code ObjectMapper} do Spring já tem o {@code JavaTimeModule} registado
	 * pelo Boot — necessário para serializar {@code LocalDateTime} em ISO-8601.
	 * Passar o objectMapper explicitamente garante que o mesmo módulo é usado no
	 * RabbitTemplate e nos listeners.
	 */
	@Bean
	public MessageConverter messageConverter(ObjectMapper objectMapper) {
		return new Jackson2JsonMessageConverter(objectMapper);
	}
}
