package pt.estg.mtsds.acesso.indicadores.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resposta de GET /indicadores/lista: tamanho da lista e movimento (entradas e
 * saídas). É um DTO: a entidade nunca sai do serviço (RNF10).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ListaIndicadorDTO {

	/** Especialidade pedida. */
	private String especialidade;

	/** Semana pedida (2026-W47) ou nulo se o pedido foi sobre todo o período. */
	private String semana;

	/** Episódios que entraram. */
	private long entradas;

	/** Episódios que saíram. */
	private long saidas;

	/** Tamanho da lista = entradas - saídas (acumulado até à semana pedida). */
	private long tamanhoLista;
}
