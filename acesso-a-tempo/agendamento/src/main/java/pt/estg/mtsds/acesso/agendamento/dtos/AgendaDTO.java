package pt.estg.mtsds.acesso.agendamento.dtos;

import java.time.LocalDate;
import java.util.List;

/**
 * Agenda com as suas vagas, na resposta da API.
 *
 * @param id
 *            identificador da agenda
 * @param especialidade
 *            especialidade das consultas
 * @param data
 *            dia da agenda
 * @param vagas
 *            vagas da agenda, por ordem de hora
 */
public record AgendaDTO(Long id, String especialidade, LocalDate data, List<VagaDTO> vagas) {
}
