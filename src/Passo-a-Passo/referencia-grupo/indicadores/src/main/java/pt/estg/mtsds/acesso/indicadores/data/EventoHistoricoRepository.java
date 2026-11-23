package pt.estg.mtsds.acesso.indicadores.data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.estg.mtsds.acesso.indicadores.models.EventoHistorico;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Acesso à tabela {@code evento_historico}. É uma tabela append-only: nunca há
 * updates nem deletes neste repositório.
 */
public interface EventoHistoricoRepository extends JpaRepository<EventoHistorico, String> {

	/** Todos os eventos de um episódio, por ordem cronológica. */
	List<EventoHistorico> findByEpisodioIdOrderByDataHoraEventoAsc(Long episodioId);

	/** Todos os eventos de um mesmo traceId, por ordem cronológica. */
	List<EventoHistorico> findByTraceIdOrderByDataHoraEventoAsc(String traceId);

	/** Paginação por routing key. */
	Page<EventoHistorico> findByRoutingKey(String routingKey, Pageable pageable);

	/** Paginação por período. */
	Page<EventoHistorico> findByDataHoraEventoBetween(LocalDateTime inicio, LocalDateTime fim, Pageable pageable);

	/** Paginação por routing key e período. */
	Page<EventoHistorico> findByRoutingKeyAndDataHoraEventoBetween(String routingKey, LocalDateTime inicio,
			LocalDateTime fim, Pageable pageable);
}
