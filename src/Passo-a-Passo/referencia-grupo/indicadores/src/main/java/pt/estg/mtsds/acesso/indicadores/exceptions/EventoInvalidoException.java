package pt.estg.mtsds.acesso.indicadores.exceptions;

/**
 * Evento recebido com dados em falta ou mal formados. Não é um erro HTTP
 * (ninguém está à espera de resposta): a exceção sobe até ao listener, o
 * RabbitMQ repete e, se continuar a falhar, a mensagem vai para a Dead-Letter
 * Queue em vez de bloquear a fila.
 */
public class EventoInvalidoException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * @param mensagem
	 *            o que está errado no evento
	 */
	public EventoInvalidoException(String mensagem) {
		super(mensagem);
	}
}
