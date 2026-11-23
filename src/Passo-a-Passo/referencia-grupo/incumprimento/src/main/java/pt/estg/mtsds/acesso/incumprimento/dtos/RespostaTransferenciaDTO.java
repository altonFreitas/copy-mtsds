package pt.estg.mtsds.acesso.incumprimento.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Corpo de {@code PUT /transferencias/{id}/resposta}: a resposta da outra
 * instituição.
 */
@Setter
@Getter
@NoArgsConstructor
public class RespostaTransferenciaDTO {

	/** true = a instituição aceitou a consulta; false = recusou. */
	@NotNull(message = "aceite é obrigatório (true ou false)")
	private Boolean aceite;
}
