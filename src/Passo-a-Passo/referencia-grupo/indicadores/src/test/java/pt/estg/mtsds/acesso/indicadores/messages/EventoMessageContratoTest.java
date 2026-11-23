package pt.estg.mtsds.acesso.indicadores.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Teste de CONTRATO: o JSON que os outros serviços do grupo publicam (envelope
 * MensagemBase + campos do evento ao mesmo nível, sem tipo nem dados) tem de
 * ser lido por este serviço. Usa o mesmo conversor do RabbitConfig, sem
 * precisar de RabbitMQ.
 */
class EventoMessageContratoTest {

	private final Jackson2JsonMessageConverter conversor = new Jackson2JsonMessageConverter(
			Jackson2ObjectMapperBuilder.json().build());

	/**
	 * Faz o que o listener faz: o Spring indica o tipo esperado e converte o JSON.
	 */
	private EventoMessage ler(String json) {
		MessageProperties propriedades = new MessageProperties();
		propriedades.setContentType("application/json");
		propriedades.setInferredArgumentType(EventoMessage.class);
		return (EventoMessage) conversor.fromMessage(new Message(json.getBytes(StandardCharsets.UTF_8), propriedades));
	}

	@Test
	void leOEventoTmrgIncumpridoMesmoComCamposQueNaoConhece() {
		// Como o tmrg o publica: MensagemBase + campos do episódio. campoFuturo é
		// desconhecido e tem de ser ignorado.
		String json = """
				{"eventoId":"e-123","traceId":"abc123","autor":"sistema",
				 "dataHoraEvento":"2026-12-01T01:00:00.123456",
				 "episodioId":101,"especialidade":"CARDIOLOGIA","prioridade":"PRIORITARIA",
				 "dataPedido":"2026-10-01","dataLimite":"2026-11-30","campoFuturo":{"x":1}}
				""";

		EventoMessage evento = ler(json);

		assertEquals("e-123", evento.getEventoId());
		assertEquals("abc123", evento.getTraceId());
		assertEquals("sistema", evento.getAutor());
		assertNotNull(evento.getDataHoraEvento());
		assertEquals(101L, evento.identificador());
		assertEquals("CARDIOLOGIA", evento.getEspecialidade());
		assertEquals(LocalDate.of(2026, 11, 30), evento.getDataLimite());
		assertNull(evento.getTipo()); // o tipo não vem no JSON: é a routing key
	}

	@Test
	void identificadorAceitaPedidoIdOuEpisodioId() {
		assertEquals(7L, ler("{\"eventoId\":\"a\",\"pedidoId\":7}").identificador());
		assertEquals(8L, ler("{\"eventoId\":\"b\",\"episodioId\":8}").identificador());
	}
}
