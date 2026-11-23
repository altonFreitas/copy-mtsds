package pt.estg.mtsds.acesso.gestaoacesso.services;

/**
 * Par episódio e vaga a marcar, devolvido pelo {@link AtribuidorVagas}.
 *
 * @param episodioId
 *            identificador do episódio
 * @param vagaId
 *            identificador da vaga
 */
public record Atribuicao(Long episodioId, Long vagaId) {
}
