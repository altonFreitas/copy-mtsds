package pt.estg.mtsds.acesso.indicadores.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resposta de GET /indicadores/cumprimento: taxa de cumprimento do TMRG por
 * especialidade.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CumprimentoDTO {

	/** Especialidade. */
	private String especialidade;

	/** Episódios que entraram no período. */
	private long entradas;

	/** Episódios que ultrapassaram a data-limite. */
	private long incumpridos;

	/** Percentagem de episódios que não ultrapassaram a data-limite (0 a 100). */
	private double taxaCumprimento;
}
