package pt.estg.mtsds.acesso.agendamento.data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pt.estg.mtsds.acesso.agendamento.models.EstadoMarcacao;
import pt.estg.mtsds.acesso.agendamento.models.Marcacao;

/**
 * Acesso à tabela marcacao.
 */
public interface MarcacaoRepository extends JpaRepository<Marcacao, Long> {

	/**
	 * Marcações cujas vagas são de uma especialidade e ficam entre dois instantes.
	 *
	 * @param especialidade
	 *            especialidade da vaga
	 * @param desde
	 *            início do intervalo, inclusive
	 * @param antesDe
	 *            fim do intervalo, exclusivo
	 * @return as marcações encontradas
	 */
	List<Marcacao> findByVagaEspecialidadeAndVagaDataHoraGreaterThanEqualAndVagaDataHoraLessThan(String especialidade,
			LocalDateTime desde, LocalDateTime antesDe);

	/**
	 * Marcações de uma especialidade num dia, em qualquer estado.
	 *
	 * @param especialidade
	 *            especialidade da vaga
	 * @param dia
	 *            dia das consultas
	 * @return as marcações das vagas desse dia
	 */
	default List<Marcacao> marcacoesDoDia(String especialidade, LocalDate dia) {
		return findByVagaEspecialidadeAndVagaDataHoraGreaterThanEqualAndVagaDataHoraLessThan(especialidade,
				dia.atStartOfDay(), dia.plusDays(1).atStartOfDay());
	}

	/**
	 * Marcação de um episódio num estado.
	 *
	 * @param episodioId
	 *            episódio da lista de espera
	 * @param estado
	 *            estado da marcação
	 * @return a marcação, se existir
	 */
	Optional<Marcacao> findByEpisodioIdAndEstado(Long episodioId, EstadoMarcacao estado);

	/**
	 * Marcação ativa (AGENDADA) de um episódio.
	 *
	 * @param episodioId
	 *            episódio da lista de espera
	 * @return a marcação ativa, se existir
	 */
	default Optional<Marcacao> marcacaoAtiva(Long episodioId) {
		return findByEpisodioIdAndEstado(episodioId, EstadoMarcacao.AGENDADA);
	}
}
