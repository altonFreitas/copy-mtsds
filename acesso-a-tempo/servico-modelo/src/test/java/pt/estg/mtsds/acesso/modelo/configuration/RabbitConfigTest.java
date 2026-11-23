package pt.estg.mtsds.acesso.modelo.configuration;

import static org.assertj.core.api.Assertions.assertThat; // assertThat(...) — verifica condições nos testes

import org.junit.jupiter.api.DisplayName; // @DisplayName — nome legível que aparece no relatório de testes
import org.junit.jupiter.api.Test;        // @Test — marca o método como um teste a correr pelo JUnit
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired; // @Autowired — o Spring injeta o bean automaticamente
import org.springframework.boot.test.context.SpringBootTest;  // @SpringBootTest — arranca o contexto Spring completo

/**
 * Testes dos beans de RabbitMQ declarados em {@link RabbitConfig}.
 *
 * <p><b>O que testa:</b> verifica que a exchange principal, a DLX,
 * o container factory e o conversor JSON existem e têm os valores certos.
 *
 * <p><b>Como corre sem RabbitMQ real:</b>
 * <ul>
 *   <li>{@code auto-startup=false} — impede que os listeners tentem ligar
 *       ao broker RabbitMQ durante os testes (não há broker nos testes).</li>
 *   <li>{@code eureka.client.enabled=false} — impede a ligação ao servidor
 *       Eureka de descoberta de serviços durante os testes.</li>
 * </ul>
 *
 * <p><b>Porquê estes testes são importantes:</b> o RabbitMQ usa os nomes
 * das exchanges e filas como strings literais. Um typo num nome compila
 * sem erros mas as mensagens não chegam ao destino certo — só se descobre
 * em runtime. Estes testes apanham esse tipo de erro antes da demo.
 */
@SpringBootTest(properties = {
		"spring.rabbitmq.listener.simple.auto-startup=false", // sem esta linha os testes falham por não haver broker
		"eureka.client.enabled=false"                         // sem esta linha os testes falham por não haver Eureka
})
class RabbitConfigTest {

	/**
	 * A única TopicExchange do sistema — onde todos os serviços publicam eventos.
	 * O Spring injeta o bean declarado em {@link RabbitConfig#acessoEventos()}.
	 */
	@Autowired
	private TopicExchange acessoEventos;

	/**
	 * Dead-Letter Exchange — para onde vão as mensagens que falharam 3 vezes.
	 * Tem de ser {@link DirectExchange} para encaminhar pela routing key exata.
	 * O Spring injeta o bean declarado em {@link RabbitConfig#deadLetterExchange()}.
	 */
	@Autowired
	private DirectExchange deadLetterExchange;

	/**
	 * Container factory com retry — todos os {@code @RabbitListener} do sistema
	 * herdam as 3 tentativas com backoff a partir deste bean.
	 * O Spring encontra-o pelo nome "rabbitListenerContainerFactory" (convenção).
	 */
	@Autowired
	private SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory;

	/**
	 * Conversor JSON — transforma objetos Java em JSON para o RabbitMQ e vice-versa.
	 * O Spring injeta o bean declarado em {@link RabbitConfig#messageConverter}.
	 */
	@Autowired
	private MessageConverter messageConverter;

	// -------------------------------------------------------------------------
	// TopicExchange — exchange principal
	// -------------------------------------------------------------------------

	/**
	 * O nome "acesso.eventos" é usado por todos os serviços para publicar.
	 * Se mudar aqui, nenhum serviço consegue publicar nem receber mensagens.
	 */
	@Test
	@DisplayName("Exchange principal tem o nome acesso.eventos")
	void exchangePrincipalTemNomeCerto() {
		assertThat(acessoEventos.getName()).isEqualTo(RabbitConfig.EXCHANGE); // compara com a constante
		assertThat(acessoEventos.getName()).isEqualTo("acesso.eventos");      // compara com o valor literal
	}

	/**
	 * durable=true — a exchange sobrevive a um reinício do RabbitMQ.
	 * autoDelete=false — não é apagada quando não há consumidores ligados.
	 */
	@Test
	@DisplayName("Exchange principal é durable e não autoDelete")
	void exchangePrincipalEDurable() {
		assertThat(acessoEventos.isDurable()).isTrue();    // sobrevive a reinicios
		assertThat(acessoEventos.isAutoDelete()).isFalse(); // não se apaga sozinha
	}

	// -------------------------------------------------------------------------
	// DirectExchange — Dead-Letter Exchange (DLX)
	// -------------------------------------------------------------------------

	/**
	 * O nome "acesso.eventos.dlx" é colocado por cada serviço em
	 * {@code QueueBuilder.durable(...).deadLetterExchange(RabbitConfig.DLX)}.
	 * Se não coincidir, as mensagens falhadas não vão para a DLQ.
	 */
	@Test
	@DisplayName("Dead-Letter Exchange tem o nome acesso.eventos.dlx")
	void dlxTemNomeCerto() {
		assertThat(deadLetterExchange.getName()).isEqualTo(RabbitConfig.DLX); // compara com a constante
		assertThat(deadLetterExchange.getName()).isEqualTo("acesso.eventos.dlx"); // compara com o valor literal
	}

	/**
	 * Tem de ser {@link DirectExchange} — encaminha pela routing key exata (= nome da DLQ).
	 * Se fosse TopicExchange podia encaminhar para filas erradas com padrões * e #.
	 */
	@Test
	@DisplayName("Dead-Letter Exchange é DirectExchange (não TopicExchange)")
	void dlxEDirectExchange() {
		assertThat(deadLetterExchange).isInstanceOf(DirectExchange.class);
	}

	/** durable=true — a DLX sobrevive a reinicios para não perder mensagens mortas. */
	@Test
	@DisplayName("Dead-Letter Exchange é durable")
	void dlxEDurable() {
		assertThat(deadLetterExchange.isDurable()).isTrue();
	}

	// -------------------------------------------------------------------------
	// SimpleRabbitListenerContainerFactory — retry + DLQ
	// -------------------------------------------------------------------------

	/**
	 * O bean tem de existir — sem ele os @RabbitListener usam o factory padrão
	 * do Spring Boot, que não tem retry nem DLQ configurados.
	 */
	@Test
	@DisplayName("Container factory está registado")
	void containerFactoryExiste() {
		assertThat(rabbitListenerContainerFactory).isNotNull(); // isNotNull() — verifica que o bean foi criado
	}

	/**
	 * O Spring AMQP procura um bean chamado "rabbitListenerContainerFactory"
	 * do tipo {@link SimpleRabbitListenerContainerFactory}.
	 * Se o tipo for outro, o Spring ignora-o e usa o factory padrão sem retry.
	 */
	@Test
	@DisplayName("Container factory é SimpleRabbitListenerContainerFactory")
	void containerFactoryETipoCorreto() {
		assertThat(rabbitListenerContainerFactory)
				.isInstanceOf(SimpleRabbitListenerContainerFactory.class); // isInstanceOf — verifica o tipo
	}

	// -------------------------------------------------------------------------
	// MessageConverter — serialização JSON
	// -------------------------------------------------------------------------

	/**
	 * Tem de ser {@link Jackson2JsonMessageConverter} para que as mensagens
	 * circulem em JSON entre serviços. Sem isto o Spring usa serialização Java
	 * binária e os outros serviços não conseguem ler as mensagens.
	 */
	@Test
	@DisplayName("MessageConverter é Jackson2JsonMessageConverter")
	void messageConverterEJackson() {
		assertThat(messageConverter).isInstanceOf(Jackson2JsonMessageConverter.class);
	}

	// -------------------------------------------------------------------------
	// Constantes públicas — usadas por todos os serviços
	// -------------------------------------------------------------------------

	/**
	 * {@link RabbitConfig#EXCHANGE} é importada por todos os serviços para publicar.
	 * Se mudar aqui, quebra a publicação em todo o sistema.
	 */
	@Test
	@DisplayName("Constante EXCHANGE tem o valor correto")
	void constanteExchange() {
		assertThat(RabbitConfig.EXCHANGE).isEqualTo("acesso.eventos");
	}

	/**
	 * {@link RabbitConfig#DLX} é usada por cada serviço em
	 * {@code QueueBuilder.durable(...).deadLetterExchange(DLX)}.
	 * Se mudar aqui, as filas mortas deixam de funcionar em todos os serviços.
	 */
	@Test
	@DisplayName("Constante DLX tem o valor correto")
	void constanteDlx() {
		assertThat(RabbitConfig.DLX).isEqualTo("acesso.eventos.dlx");
	}

	/**
	 * Convenção do sistema: todas as dead-letter exchanges terminam em ".dlx".
	 * Facilita identificar exchanges de filas mortas na consola do RabbitMQ.
	 */
	@Test
	@DisplayName("DLX termina em .dlx (convenção do sistema)")
	void dlxSegueCovencaoDeNome() {
		assertThat(RabbitConfig.DLX).endsWith(".dlx"); // endsWith — verifica o sufixo da string
	}
}
