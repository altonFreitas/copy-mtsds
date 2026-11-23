package com.acessoatempo.indicadores.listeners;

import com.acessoatempo.indicadores.config.MQConfig;
import com.acessoatempo.indicadores.messages.EventoMessage;
import com.acessoatempo.indicadores.services.IndicadoresService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumer do RabbitMQ (equivale ao {@code OrderConfirmationListener} da aula). Recebe os 17 eventos a que o serviço está ligado (ver MQConfig) e entrega-os ao serviço de negócio.
 *
 * <p>O <b>tipo</b> do evento não vem no corpo da mensagem (envelope do grupo, {@code MensagemBase}): é a
 * <b>routing key</b>. Por isso o listener lê-a do cabeçalho {@code amqp_receivedRoutingKey} e guarda-a em
 * {@code evento.tipo}, que é o que o serviço de negócio usa para escolher o tratamento.
 *
 * <p>Se o método terminar sem erro, o Spring envia o ACK. Se lançar exceção, repete e depois a mensagem vai
 * para a Dead-Letter Queue (ver {@link MQConfig}).
 */
@Component
public class EventosListener {

    private static final Logger log = LoggerFactory.getLogger(EventosListener.class);

    @Autowired
    private IndicadoresService indicadoresService;

    @RabbitListener(queues = MQConfig.QUEUE)
    public void onEvento(EventoMessage evento,
                         @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        evento.setTipo(routingKey);
        log.info("Evento recebido: tipo={} episodio={} eventoId={}",
                evento.getTipo(), evento.identificador(), evento.getEventoId());
        indicadoresService.processar(evento);
    }
}
