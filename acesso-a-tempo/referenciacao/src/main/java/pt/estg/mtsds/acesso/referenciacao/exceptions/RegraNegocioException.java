package pt.estg.mtsds.acesso.referenciacao.exceptions;

/**
 * Regra de negócio violada: vaga ocupada, vigência sobreposta, caso fechado
 * (409).
 */
public class RegraNegocioException extends ErroNegocioException {

	public RegraNegocioException(String codigo, String mensagem) {
		super(codigo, mensagem);
	}
}
