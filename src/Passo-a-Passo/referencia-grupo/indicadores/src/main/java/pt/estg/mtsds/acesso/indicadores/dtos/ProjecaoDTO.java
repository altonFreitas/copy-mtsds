package pt.estg.mtsds.acesso.indicadores.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resposta de GET /indicadores/projecao: previsão do tamanho da lista.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjecaoDTO {

	/** Especialidade. */
	private String especialidade;

	/** Tamanho atual da lista. */
	private long tamanhoAtual;

	/** Média de entradas por semana. */
	private double mediaEntradasSemana;

	/** Média de saídas por semana. */
	private double mediaSaidasSemana;

	/** Quantas semanas à frente se projeta. */
	private int semanas;

	/**
	 * Tamanho previsto = atual + semanas x (média de entradas - média de saídas),
	 * nunca negativo.
	 */
	private double tamanhoProjetado;
}
