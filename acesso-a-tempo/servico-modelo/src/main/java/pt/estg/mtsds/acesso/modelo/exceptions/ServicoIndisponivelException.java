package pt.estg.mtsds.acesso.modelo.exceptions;

/** Um serviço chamado por HTTP não respondeu (503). */
public class ServicoIndisponivelException extends ErroNegocioException {

	public ServicoIndisponivelException(String codigo, String mensagem) {
		super(codigo, mensagem);
	}
}
