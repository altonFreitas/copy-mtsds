package pt.estg.mtsds.acesso.incumprimento.listeners;

import pt.estg.mtsds.acesso.incumprimento.messages.EventoMessage;
import pt.estg.mtsds.acesso.incumprimento.services.CasosService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

/**
 * O envelope do grupo não tem "tipo": o tipo é a routing key. O listener tem de
 * o copiar do cabeçalho da mensagem para o evento, senão o serviço de negócio
 * não sabe que evento recebeu.
 */
@ExtendWith(MockitoExtension.class)
public class EventosListenerTest {

	@Mock
	private CasosService servico;

	@InjectMocks
	private EventosListener listener;

	@Test
	void aRoutingKeyPassaATipoDoEvento() {
		EventoMessage evento = EventoMessage.builder().eventoId("e-1").pedidoId(5L).build();

		listener.onEvento(evento, "tmrg.incumprido");

		verify(servico)
				.tratarEvento(argThat(e -> "tmrg.incumprido".equals(e.getTipo()) && "e-1".equals(e.getEventoId())));
	}
}
