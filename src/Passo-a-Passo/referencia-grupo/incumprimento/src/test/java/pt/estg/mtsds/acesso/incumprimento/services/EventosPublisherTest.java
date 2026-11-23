package pt.estg.mtsds.acesso.incumprimento.services;

import pt.estg.mtsds.acesso.incumprimento.messages.EventoMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** O publisher completa o envelope do grupo e usa o tipo como routing key. */
@ExtendWith(MockitoExtension.class)
public class EventosPublisherTest {

	@Mock
	private RabbitTemplate rabbitTemplate;

	@Mock
	private RelogioService relogio;

	@InjectMocks
	private EventosPublisher publisher;

	@AfterEach
	void limparMdc() {
		MDC.clear();
	}

	@Test
	void completaOEnvelopeEEnviaComATipoComoRoutingKey() {
		when(relogio.hoje()).thenReturn(LocalDate.of(2026, 12, 1));
		MDC.put("traceId", "trace-1");

		publisher.publicar("incumprimento.registado",
				EventoMessage.builder().episodioId(101L).especialidade("CARDIOLOGIA").casoId(7).build());

		ArgumentCaptor<Object> enviado = ArgumentCaptor.forClass(Object.class);
		verify(rabbitTemplate).convertAndSend(eq("acesso.eventos"), eq("incumprimento.registado"), enviado.capture());
		EventoMessage m = (EventoMessage) enviado.getValue();
		assertNotNull(m.getEventoId());
		assertEquals("trace-1", m.getTraceId()); // vem do MDC (filtro de traceId)
		assertEquals("incumprimento", m.getAutor()); // sem utilizador: o próprio sistema
		assertEquals(LocalDate.of(2026, 12, 1), m.getDataHoraEvento().toLocalDate()); // data do relógio
		assertEquals(101L, m.getEpisodioId());
		assertEquals(101L, m.getPedidoId()); // os dois nomes, o mesmo valor
	}

	@Test
	void cadaEventoTemUmEventoIdDiferente() {
		when(relogio.hoje()).thenReturn(LocalDate.of(2026, 12, 1));
		EventoMessage a = EventoMessage.builder().episodioId(1L).build();
		EventoMessage b = EventoMessage.builder().episodioId(1L).build();

		publisher.publicar("incumprimento.registado", a);
		publisher.publicar("incumprimento.registado", b);

		assertNotNull(a.getEventoId());
		assertNotNull(b.getEventoId());
		org.junit.jupiter.api.Assertions.assertNotEquals(a.getEventoId(), b.getEventoId());
	}
}
