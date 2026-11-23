package pt.estg.mtsds.acesso.incumprimento.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Um campo inválido num pedido, com a explicação do que está errado. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErroCampoDTO {

	private String campo;
	private String mensagem;
}
