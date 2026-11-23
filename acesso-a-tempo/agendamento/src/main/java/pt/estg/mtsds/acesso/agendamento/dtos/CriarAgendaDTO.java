package pt.estg.mtsds.acesso.agendamento.dtos;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Pedido de criação de uma agenda: uma vaga por cada hora indicada.
 *
 * @param especialidade
 *            especialidade das consultas
 * @param data
 *            dia da agenda (formato AAAA-MM-DD)
 * @param horas
 *            horas das vagas (formato HH:MM), sem repetidos
 */
public record CriarAgendaDTO(
		@NotBlank(message = "Indique a especialidade da agenda.") @Size(max = 60, message = "A especialidade tem no máximo 60 caracteres.") String especialidade,
		@NotNull(message = "Indique o dia da agenda, no formato AAAA-MM-DD.") LocalDate data,
		@NotEmpty(message = "Indique pelo menos uma hora de vaga, no formato HH:MM.") List<@NotNull(message = "As horas não podem vir vazias.") LocalTime> horas) {

	/**
	 * Confirma que nenhuma hora aparece duas vezes. Uma lista vazia ou nula fica
	 * para a validação de horas.
	 *
	 * @return true se as horas não tiverem repetidos
	 */
	@AssertTrue(message = "Há horas repetidas: cada hora só pode aparecer uma vez.")
	public boolean isHorasSemRepetidos() {
		return horas == null || new HashSet<>(horas).size() == horas.size();
	}
}
