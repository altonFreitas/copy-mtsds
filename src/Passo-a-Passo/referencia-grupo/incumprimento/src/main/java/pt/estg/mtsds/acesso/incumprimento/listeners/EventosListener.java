package pt.estg.mtsds.acesso.incumprimento.listeners;

import lombok.RequiredArgsConstructor;
import pt.estg.mtsds.acesso.incumprimento.configuration.RabbitConfig;
import pt.estg.mtsds.acesso.incumprimento.messages.EventoMessage;
import pt.estg.mtsds.acesso.incumprimento.services.CasosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumer do RabbitMQ (equivale ao {@code OrderConfirmationListener} da aula).
 * Recebe os 5 eventos a que o serviço está ligado (ver RabbitConfig) e
 * entrega-os ao serviço de negócio.
 *
 * <p>
 * O <b>tipo</b> do evento não vem no corpo da mensagem (envelope do grupo,
 * {@code MensagemBase}): é a <b>routing key</b>. Por isso o listener lê-a do
 * cabeçalho {@code amqp_receivedRoutingKey} e guarda-a em {@code evento.tipo},
 * que é o que o serviço de negócio usa para escolher o tratamento.
 *
 * <p>
 * Se o método terminar sem erro, o Spring envia o ACK. Se lançar exceção,
 * repete e depois a mensagem vai para a Dead-Letter Queue (ver
 * {@link RabbitConfig}).
 */
@Component
@RequiredArgsConstructor
public class EventosListener {

	private static final Logger log = LoggerFactory.getLogger(EventosListener.class);

	private final CasosService casosService;

	@RabbitListener(queues = RabbitConfig.QUEUE)
	public void onEvento(EventoMessage evento, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
		evento.setTipo(routingKey);
		log.info("Evento recebido: tipo={} episodio={} eventoId={}", evento.getTipo(), evento.identificador(),
				evento.getEventoId());
		casosService.tratarEvento(evento);
	}
}
