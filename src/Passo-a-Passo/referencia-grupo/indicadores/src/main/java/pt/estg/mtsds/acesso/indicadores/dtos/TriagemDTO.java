package pt.estg.mtsds.acesso.indicadores.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resposta de GET /indicadores/triagem: tempo médio entre o pedido e a triagem.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TriagemDTO {

	/** Especialidade. */
	private String especialidade;

	/** Número de triagens. */
	private long triagens;

	/**
	 * Média de dias entre o pedido e a triagem (a triagem tardia consome prazo, R1
	 * e R7).
	 */
	private double mediaDiasTriagem;
}
