package pt.estg.mtsds.acesso.referenciacao.exceptions;

/** Recurso inexistente (404). */
public class NaoEncontradoException extends ErroNegocioException {

	public NaoEncontradoException(String codigo, String mensagem) {
		super(codigo, mensagem);
	}
}
