package pt.estg.mtsds.acesso.gestaoacesso.services;

import java.util.List;

/**
 * Resultado de uma verificação diária dos prazos: os ids dos pedidos que
 * mudaram de situação nessa verificação.
 *
 * @param passaramEmRisco
 *            pedidos que passaram a EM_RISCO
 * @param passaramIncumpridos
 *            pedidos que passaram a INCUMPRIDO
 */
public record ResultadoVerificacao(List<Long> passaramEmRisco, List<Long> passaramIncumpridos) {

	/** Guarda cópias imutáveis das listas. */
	public ResultadoVerificacao {
		passaramEmRisco = List.copyOf(passaramEmRisco);
		passaramIncumpridos = List.copyOf(passaramIncumpridos);
	}
}
