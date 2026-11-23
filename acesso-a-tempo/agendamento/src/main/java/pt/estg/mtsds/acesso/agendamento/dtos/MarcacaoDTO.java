package pt.estg.mtsds.acesso.agendamento.dtos;

import java.time.LocalDateTime;

import pt.estg.mtsds.acesso.agendamento.models.EstadoMarcacao;
import pt.estg.mtsds.acesso.agendamento.models.OrigemMarcacao;

/**
 * Marcação, na resposta da API. A data e a especialidade vêm da vaga marcada.
 *
 * @param id
 *            identificador da marcação
 * @param episodioId
 *            episódio da lista de espera
 * @param vagaId
 *            vaga marcada
 * @param dataHora
 *            dia e hora da consulta
 * @param especialidade
 *            especialidade da consulta
 * @param estado
 *            estado da marcação
 * @param origem
 *            AUTOMATICA ou MANUAL
 */
public record MarcacaoDTO(Long id, Long episodioId, Long vagaId, LocalDateTime dataHora, String especialidade,
		EstadoMarcacao estado, OrigemMarcacao origem) {
}
