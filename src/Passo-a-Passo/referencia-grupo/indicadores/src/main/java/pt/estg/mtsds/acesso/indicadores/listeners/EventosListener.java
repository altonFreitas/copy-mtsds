package pt.estg.mtsds.acesso.indicadores.listeners;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pt.estg.mtsds.acesso.indicadores.configuration.RabbitConfig;
import pt.estg.mtsds.acesso.indicadores.messages.EventoMessage;
import pt.estg.mtsds.acesso.indicadores.services.HistoricoService;

/**
 * Consumidor do RabbitMQ do serviço {@code historico}. A fila está ligada com
 * {@code #} (todas as routing keys), por isso recebe TODOS os eventos do
 * sistema. Cada evento é entregue ao {@link HistoricoService} para ser
 * persistido.
 *
 * <p>
 * Se o método terminar sem erro, o Spring envia o ACK. Se lançar exceção,
 * repete e, esgotadas as 3 tentativas, a mensagem vai para a Dead-Letter Queue.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventosListener {

	private final HistoricoService historicoService;

	@RabbitListener(queues = RabbitConfig.QUEUE)
	public void onEvento(EventoMessage evento, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
		log.info("Evento recebido para o historico: routingKey={} episodio={} eventoId={}",
				routingKey, evento.getEpisodioId(), evento.getEventoId());
		historicoService.guardar(evento, routingKey);
	}
}
