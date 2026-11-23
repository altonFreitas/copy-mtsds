package pt.estg.mtsds.acesso.agendamento.data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pt.estg.mtsds.acesso.agendamento.models.EstadoVaga;
import pt.estg.mtsds.acesso.agendamento.models.Vaga;

/**
 * Acesso à tabela vaga.
 */
public interface VagaRepository extends JpaRepository<Vaga, Long> {

	/**
	 * Vagas de uma especialidade num estado, com data e hora antes de um instante,
	 * da mais cedo para a mais tarde.
	 *
	 * @param especialidade
	 *            especialidade da consulta
	 * @param estado
	 *            estado das vagas
	 * @param antesDe
	 *            instante limite, exclusivo
	 * @return as vagas encontradas, ordenadas por data e hora
	 */
	List<Vaga> findByEspecialidadeAndEstadoAndDataHoraBeforeOrderByDataHoraAsc(String especialidade, EstadoVaga estado,
			LocalDateTime antesDe);

	/**
	 * Vagas livres de uma especialidade até um dia, inclusive, da mais cedo para a
	 * mais tarde.
	 *
	 * @param especialidade
	 *            especialidade da consulta
	 * @param ate
	 *            último dia a incluir
	 * @return as vagas livres até ao fim desse dia, ordenadas por data e hora
	 */
	default List<Vaga> vagasLivresAte(String especialidade, LocalDate ate) {
		return findByEspecialidadeAndEstadoAndDataHoraBeforeOrderByDataHoraAsc(especialidade, EstadoVaga.LIVRE,
				ate.plusDays(1).atStartOfDay());
	}

	/**
	 * Vagas de uma especialidade em qualquer dos instantes indicados.
	 *
	 * @param especialidade
	 *            especialidade da consulta
	 * @param datasHoras
	 *            instantes a procurar
	 * @return as vagas que já existem nesses instantes
	 */
	List<Vaga> findByEspecialidadeAndDataHoraIn(String especialidade, Collection<LocalDateTime> datasHoras);
}
