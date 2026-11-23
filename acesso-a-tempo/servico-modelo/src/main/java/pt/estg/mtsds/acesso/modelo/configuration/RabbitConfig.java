package pt.estg.mtsds.acesso.modelo.configuration;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * RabbitMQ (slide 05): uma única TopicExchange para todos os eventos do
 * sistema. Cada serviço declara aqui só as filas que consome, ligadas por
 * routing key.
 */
@Configuration
public class RabbitConfig {

	public static final String EXCHANGE = "acesso.eventos";

	@Bean
	public TopicExchange acessoEventos() {
		return new TopicExchange(EXCHANGE, true, false);
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
