package pt.estg.mtsds.acesso.indicadores.exceptions;

import lombok.Getter;

/**
 * Base das exceções próprias. Cada uma leva um código estável (ex.:
 * EPISODIO_NAO_ENCONTRADO) e uma mensagem descritiva: o que falhou, porquê e o
 * que fazer (RNF11).
 */
@Getter
public abstract class ErroNegocioException extends RuntimeException {

	private final String codigo;

	protected ErroNegocioException(String codigo, String mensagem) {
		super(mensagem);
		this.codigo = codigo;
	}
}
