package pt.estg.mtsds.acesso.referenciacao.exceptions;

/** Um serviço chamado por HTTP não respondeu (503). */
public class ServicoIndisponivelException extends ErroNegocioException {

	public ServicoIndisponivelException(String codigo, String mensagem) {
		super(codigo, mensagem);
	}
}
