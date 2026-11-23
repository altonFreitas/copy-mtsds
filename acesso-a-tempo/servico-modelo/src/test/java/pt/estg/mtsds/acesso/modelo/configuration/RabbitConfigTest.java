package pt.estg.mtsds.acesso.modelo.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Testa os beans de RabbitMQ declarados em {@link RabbitConfig}.
 *
 * <p>
 * Corre sem RabbitMQ real: {@code auto-startup=false} impede que os listeners
 * tentem ligar-se ao broker durante os testes. Verifica que a DLX, o container
 * factory e as constantes estão correctos — se qualquer um destes falhar, os
 * serviços ficam sem DLQ sem aviso.
 */
@SpringBootTest(properties = {"spring.rabbitmq.listener.simple.auto-startup=false", "eureka.client.enabled=false"})
class RabbitConfigTest {

	@Autowired
	private TopicExchange acessoEventos;

	@Autowired
	private DirectExchange deadLetterExchange;

	@Autowired
	private SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory;

	@Autowired
	private MessageConverter messageConverter;

	// --- TopicExchange ---

	@Test
	@DisplayName("Exchange principal tem o nome acesso.eventos")
	void exchangePrincipalTemNomeCerto() {
		assertThat(acessoEventos.getName()).isEqualTo(RabbitConfig.EXCHANGE);
		assertThat(acessoEventos.getName()).isEqualTo("acesso.eventos");
	}

	@Test
	@DisplayName("Exchange principal é durable e não autoDelete")
	void exchangePrincipalEDurable() {
		assertThat(acessoEventos.isDurable()).isTrue();
		assertThat(acessoEventos.isAutoDelete()).isFalse();
	}

	// --- Dead-Letter Exchange ---

	@Test
	@DisplayName("Dead-Letter Exchange tem o nome acesso.eventos.dlx")
	void dlxTemNomeCerto() {
		assertThat(deadLetterExchange.getName()).isEqualTo(RabbitConfig.DLX);
		assertThat(deadLetterExchange.getName()).isEqualTo("acesso.eventos.dlx");
	}

	@Test
	@DisplayName("Dead-Letter Exchange é DirectExchange (não TopicExchange)")
	void dlxEDirectExchange() {
		// DirectExchange encaminha pela routing key exata (= nome da DLQ).
		// Se fosse TopicExchange poderia encaminhar para filas erradas.
		assertThat(deadLetterExchange).isInstanceOf(DirectExchange.class);
	}

	@Test
	@DisplayName("Dead-Letter Exchange é durable")
	void dlxEDurable() {
		assertThat(deadLetterExchange.isDurable()).isTrue();
	}

	// --- Container Factory ---

	@Test
	@DisplayName("Container factory está registado")
	void containerFactoryExiste() {
		assertThat(rabbitListenerContainerFactory).isNotNull();
	}

	@Test
	@DisplayName("Container factory é SimpleRabbitListenerContainerFactory")
	void containerFactoryETipoCorreto() {
		// O Spring AMQP procura um bean "rabbitListenerContainerFactory" deste tipo.
		// Se não existir, os listeners arrancam com o factory padrão sem retry.
		assertThat(rabbitListenerContainerFactory).isInstanceOf(SimpleRabbitListenerContainerFactory.class);
	}

	// --- MessageConverter ---

	@Test
	@DisplayName("MessageConverter é Jackson2JsonMessageConverter")
	void messageConverterEJackson() {
		assertThat(messageConverter).isInstanceOf(Jackson2JsonMessageConverter.class);
	}

	// --- Constantes públicas ---

	@Test
	@DisplayName("Constante EXCHANGE tem o valor correto")
	void constanteExchange() {
		assertThat(RabbitConfig.EXCHANGE).isEqualTo("acesso.eventos");
	}

	@Test
	@DisplayName("Constante DLX tem o valor correto")
	void constanteDlx() {
		assertThat(RabbitConfig.DLX).isEqualTo("acesso.eventos.dlx");
	}

	@Test
	@DisplayName("DLX termina em .dlx (convenção do sistema)")
	void dlxSegueCovencaoDeNome() {
		assertThat(RabbitConfig.DLX).endsWith(".dlx");
	}
}
