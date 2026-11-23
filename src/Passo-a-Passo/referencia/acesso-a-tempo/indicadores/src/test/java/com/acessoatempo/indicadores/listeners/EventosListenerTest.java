package com.acessoatempo.indicadores.listeners;

import com.acessoatempo.indicadores.messages.EventoMessage;
import com.acessoatempo.indicadores.services.IndicadoresService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

/**
 * O envelope do grupo não tem "tipo": o tipo é a routing key. O listener tem de o copiar do cabeçalho da
 * mensagem para o evento, senão o serviço de negócio não sabe que evento recebeu.
 */
@ExtendWith(MockitoExtension.class)
public class EventosListenerTest {

    @Mock
    private IndicadoresService servico;

    @InjectMocks
    private EventosListener listener;

    @Test
    void aRoutingKeyPassaATipoDoEvento() {
        EventoMessage evento = EventoMessage.builder().eventoId("e-1").pedidoId(5L).build();

        listener.onEvento(evento, "episodio.inscrito");

        verify(servico).processar(argThat(e -> "episodio.inscrito".equals(e.getTipo()) && "e-1".equals(e.getEventoId())));
    }
}
