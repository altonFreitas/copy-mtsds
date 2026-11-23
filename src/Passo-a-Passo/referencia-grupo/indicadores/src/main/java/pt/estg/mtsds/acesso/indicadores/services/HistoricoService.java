package pt.estg.mtsds.acesso.indicadores.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.estg.mtsds.acesso.indicadores.data.EventoHistoricoRepository;
import pt.estg.mtsds.acesso.indicadores.exceptions.EventoInvalidoException;
import pt.estg.mtsds.acesso.indicadores.exceptions.NaoEncontradoException;
import pt.estg.mtsds.acesso.indicadores.messages.EventoMessage;
import pt.estg.mtsds.acesso.indicadores.models.EventoHistorico;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Regras de negócio do serviço {@code historico}: guardar todos os eventos de
 * cada episódio (append-only) e disponibilizá-los para auditoria.
 *
 * <p>
 * Este serviço não publica eventos nem depende de outros serviços para
 * funcionar. Se cair, os eventos esperam na fila; quando voltar, processa-os
 * todos por ordem. Uma falha aqui nunca trava marcações (RNF02).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HistoricoService {

	private final EventoHistoricoRepository historicoRepository;

	private final ObjectMapper objectMapper;

	// ------------------------------------------------------------------------------------------
	// 1) Recebe e persiste eventos (chamado pelo EventosListener)
	// ------------------------------------------------------------------------------------------

	/**
	 * Persiste o evento no historico. Idempotente: se o {@code eventoId} já
	 * existir, ignora. A serialização do conteúdo para JSON fica aqui, não no
	 * listener.
	 */
	@Transactional
	public void guardar(EventoMessage evento, String routingKey) {
		if (evento == null || evento.getEventoId() == null) {
			throw new EventoInvalidoException("Evento sem eventoId — não pode ser guardado no historico");
		}

		if (historicoRepository.existsById(evento.getEventoId())) {
			log.info("Evento {} ({}) já no historico: ignorado", evento.getEventoId(), routingKey);
			return;
		}

		String conteudo;
		try {
			conteudo = objectMapper.writeValueAsString(evento);
		} catch (JsonProcessingException e) {
			throw new EventoInvalidoException(
					"Evento " + evento.getEventoId() + " não serializável: " + e.getMessage());
		}

		EventoHistorico linha = EventoHistorico.builder()
				.eventoId(evento.getEventoId())
				.routingKey(routingKey)
				.episodioId(evento.getEpisodioId())
				.traceId(evento.getTraceId())
				.autor(evento.getAutor())
				.dataHoraEvento(evento.getDataHoraEvento() != null ? evento.getDataHoraEvento() : LocalDateTime.now())
				.conteudo(conteudo)
				.build();

		historicoRepository.save(linha);
		log.info("Evento guardado: routingKey={} episodio={} eventoId={}", routingKey,
				evento.getEpisodioId(), evento.getEventoId());
	}

	// ------------------------------------------------------------------------------------------
	// 2) Consultas de auditoria (chamadas pelo HistoricoController)
	// ------------------------------------------------------------------------------------------

	/**
	 * Todos os eventos de um episódio, por ordem cronológica.
	 *
	 * @param episodioId
	 *            obrigatório
	 * @return lista de eventos; 404 se nenhum existir
	 */
	@Transactional(readOnly = true)
	public List<EventoHistorico> porEpisodio(Long episodioId) {
		List<EventoHistorico> eventos = historicoRepository.findByEpisodioIdOrderByDataHoraEventoAsc(episodioId);
		if (eventos.isEmpty()) {
			throw new NaoEncontradoException("EPISODIO_SEM_HISTORICO",
					"Nenhum evento encontrado para o episódio " + episodioId);
		}
		return eventos;
	}

	/**
	 * Eventos por tipo (routing key) e/ou período, paginados. Ambos os filtros são
	 * opcionais.
	 */
	@Transactional(readOnly = true)
	public Page<EventoHistorico> porTipoEPeriodo(String tipo, LocalDate de, LocalDate ate, Pageable pageable) {
		LocalDateTime inicio = de != null ? de.atStartOfDay() : null;
		LocalDateTime fim = ate != null ? ate.atTime(23, 59, 59) : null;

		if (tipo != null && !tipo.isBlank() && inicio != null) {
			return historicoRepository.findByRoutingKeyAndDataHoraEventoBetween(tipo, inicio, fim, pageable);
		}
		if (tipo != null && !tipo.isBlank()) {
			return historicoRepository.findByRoutingKey(tipo, pageable);
		}
		if (inicio != null) {
			return historicoRepository.findByDataHoraEventoBetween(inicio, fim, pageable);
		}
		return historicoRepository.findAll(pageable);
	}

	/**
	 * Todos os eventos de um mesmo pedido (mesmo {@code traceId}), por ordem
	 * cronológica.
	 *
	 * @param traceId
	 *            obrigatório
	 * @return lista de eventos; 404 se nenhum existir
	 */
	@Transactional(readOnly = true)
	public List<EventoHistorico> porTrace(String traceId) {
		List<EventoHistorico> eventos = historicoRepository.findByTraceIdOrderByDataHoraEventoAsc(traceId);
		if (eventos.isEmpty()) {
			throw new NaoEncontradoException("TRACE_SEM_HISTORICO",
					"Nenhum evento encontrado para o traceId " + traceId);
		}
		return eventos;
	}
}
