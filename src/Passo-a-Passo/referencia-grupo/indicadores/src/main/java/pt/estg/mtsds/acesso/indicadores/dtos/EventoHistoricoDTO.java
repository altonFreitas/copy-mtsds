package pt.estg.mtsds.acesso.indicadores.dtos;

import java.time.LocalDateTime;

/**
 * DTO de resposta para um evento do historico (RNF10: as entidades nunca saem
 * do serviço).
 */
public record EventoHistoricoDTO(
		String eventoId,
		String routingKey,
		Long episodioId,
		String traceId,
		String autor,
		LocalDateTime dataHoraEvento,
		String conteudo) {
}
