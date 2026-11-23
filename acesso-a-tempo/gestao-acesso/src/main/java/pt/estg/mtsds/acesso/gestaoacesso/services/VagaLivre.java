package pt.estg.mtsds.acesso.gestaoacesso.services;

import java.time.LocalDateTime;

/**
 * Vaga de consulta ainda por marcar, usada pelo {@link AtribuidorVagas}.
 *
 * @param vagaId
 *            identificador da vaga
 * @param especialidade
 *            especialidade da consulta
 * @param dataHora
 *            dia e hora da consulta
 */
public record VagaLivre(Long vagaId, String especialidade, LocalDateTime dataHora) {
}
