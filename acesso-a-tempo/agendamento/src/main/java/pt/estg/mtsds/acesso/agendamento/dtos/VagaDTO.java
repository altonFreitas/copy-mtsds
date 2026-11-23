package pt.estg.mtsds.acesso.agendamento.dtos;

import java.time.LocalDateTime;

import pt.estg.mtsds.acesso.agendamento.models.EstadoVaga;

/**
 * Vaga de uma agenda, na resposta da API.
 *
 * @param id
 *            identificador da vaga
 * @param dataHora
 *            dia e hora da consulta
 * @param estado
 *            LIVRE ou OCUPADA
 */
public record VagaDTO(Long id, LocalDateTime dataHora, EstadoVaga estado) {
}
