package pt.estg.mtsds.acesso.gestaoacesso.services;

import java.time.LocalDate;

/**
 * Episódio da lista de espera ainda sem consulta, usado pelo
 * {@link AtribuidorVagas}.
 *
 * @param episodioId
 *            identificador do episódio
 * @param especialidade
 *            especialidade pedida
 * @param dataLimite
 *            último dia dentro do prazo TMRG
 * @param dataPedido
 *            data do registo do pedido
 */
public record EpisodioEmEspera(Long episodioId, String especialidade, LocalDate dataLimite, LocalDate dataPedido) {
}
