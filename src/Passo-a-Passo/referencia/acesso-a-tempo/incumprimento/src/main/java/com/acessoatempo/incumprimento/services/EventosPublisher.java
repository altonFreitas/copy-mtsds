package com.acessoatempo.incumprimento.services;

import com.acessoatempo.incumprimento.config.MQConfig;
import com.acessoatempo.incumprimento.messages.EventoMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Producer: publica eventos na exchange {@code acesso.eventos} (equivale ao {@code OrdersService} da aula,
 * que usava {@code template.convertAndSend}).
 *
 * <p>Quem chama só indica o <b>tipo</b> (routing key) e os <b>campos de negócio</b> do evento; o publisher
 * completa o <b>envelope do grupo</b> ({@code MensagemBase}): {@code eventoId}, {@code traceId}, {@code autor}
 * e {@code dataHoraEvento}. Assim todos os eventos saem com o mesmo formato.
 *
 * <p>Detalhe importante: se houver uma transação a decorrer, o evento só é enviado DEPOIS do commit
 * ({@code afterCommit}). Assim nunca se anuncia um facto que acabou por não ser gravado na base de dados
 * (rollback). O problema inverso (a BD grava e o RabbitMQ está em baixo) fica por resolver: a solução
 * completa seria o padrão Transactional Outbox (ver sugestões no explicacao.md).
 */
@Component
public class EventosPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventosPublisher.class);

    /** Autor dos eventos quando não há um utilizador conhecido (processos automáticos, eventos recebidos...). */
    static final String AUTOR_SISTEMA = "incumprimento";

    /** O Spring Boot cria este RabbitTemplate já com o MessageConverter (JSON) definido no MQConfig. */
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RelogioService relogio;

    /**
     * @param tipo  routing key do evento, ex.: {@code incumprimento.registado}
     * @param corpo campos de negócio do evento (episódio, especialidade, casoId...). Só identificadores e
     *              datas, nunca dados pessoais. O envelope (eventoId, traceId, autor, dataHoraEvento) é
     *              preenchido aqui.
     */
    public void publicar(String tipo, EventoMessage corpo) {
        corpo.setTipo(tipo);
        corpo.setEventoId(UUID.randomUUID().toString());
        // O filtro de traceId (quando existir) coloca o traceId do pedido HTTP no MDC dos logs.
        corpo.setTraceId(MDC.get("traceId"));
        corpo.setAutor(MDC.get("autor") != null ? MDC.get("autor") : AUTOR_SISTEMA);
        // Data do relógio do serviço (pode estar simulada) com a hora real.
        corpo.setDataHoraEvento(LocalDateTime.of(relogio.hoje(), LocalTime.now()));
        // O mesmo valor nos dois nomes: o tmrg usa "pedidoId" e a proposta fala em "episodioId".
        Long episodio = corpo.identificador();
        corpo.setEpisodioId(episodio);
        corpo.setPedidoId(episodio);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviar(corpo);
                }
            });
        } else {
            enviar(corpo);
        }
    }

    private void enviar(EventoMessage mensagem) {
        try {
            // exchange, routing key, objeto (convertido para JSON pelo MessageConverter)
            rabbitTemplate.convertAndSend(MQConfig.EXCHANGE, mensagem.getTipo(), mensagem);
            log.info("Evento publicado: tipo={} episodio={} eventoId={}",
                    mensagem.getTipo(), mensagem.identificador(), mensagem.getEventoId());
        } catch (Exception e) {
            // Já não se pode desfazer a transação. Regista-se o erro para o evento poder ser reenviado.
            log.error("FALHA a publicar o evento {} ({}): {}", mensagem.getTipo(), mensagem.getEventoId(),
                    e.getMessage());
        }
    }
}
