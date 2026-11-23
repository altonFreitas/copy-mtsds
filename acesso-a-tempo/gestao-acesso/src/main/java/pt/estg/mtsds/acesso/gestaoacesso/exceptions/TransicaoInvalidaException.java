package pt.estg.mtsds.acesso.gestaoacesso.exceptions;

/** Mudança de estado não permitida pelo diagrama de estados (409). */
public class TransicaoInvalidaException extends RegraNegocioException {

	public TransicaoInvalidaException(String mensagem) {
		super("TRANSICAO_INVALIDA", mensagem);
	}
}
