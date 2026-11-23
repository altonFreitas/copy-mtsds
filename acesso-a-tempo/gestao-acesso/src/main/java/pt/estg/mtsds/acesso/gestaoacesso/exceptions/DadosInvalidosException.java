package pt.estg.mtsds.acesso.gestaoacesso.exceptions;

/** Dados do pedido inválidos que a validação dos DTO não apanha (400). */
public class DadosInvalidosException extends ErroNegocioException {

	public DadosInvalidosException(String codigo, String mensagem) {
		super(codigo, mensagem);
	}
}
