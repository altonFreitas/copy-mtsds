package pt.estg.mtsds.acesso.gestaoacesso.dtos;

import java.time.LocalDate;
import java.time.LocalDateTime;

import pt.estg.mtsds.acesso.gestaoacesso.models.Prioridade;
import pt.estg.mtsds.acesso.gestaoacesso.models.SituacaoPrazo;

/**
 * Prazo TMRG de um episódio, visto na data de hoje do relógio do serviço.
 *
 * @param episodioId
 *            episódio (id do pedido)
 * @param especialidade
 *            especialidade pedida
 * @param prioridade
 *            prioridade da triagem
 * @param dataPedido
 *            data do registo do pedido, onde começa a contagem (R1)
 * @param dataLimite
 *            último dia dentro do prazo (R2)
 * @param dataAlarme
 *            primeiro dia em risco (75% do prazo)
 * @param versaoRegra
 *            versão das regras TMRG usada nas datas
 * @param diasConsumidos
 *            dias desde a data do pedido até hoje
 * @param diasPrazo
 *            dias do prazo, da data do pedido à data-limite
 * @param percentagemConsumida
 *            dias consumidos sobre o prazo, em percentagem arredondada para
 *            baixo; passa de 100 depois da data-limite
 * @param situacao
 *            situação do prazo hoje
 * @param vagaId
 *            vaga reservada ou marcada, se houver
 * @param dataConsulta
 *            dia e hora da consulta marcada, se houver
 */
public record PrazoDTO(Long episodioId, String especialidade, Prioridade prioridade, LocalDate dataPedido,
		LocalDate dataLimite, LocalDate dataAlarme, Integer versaoRegra, long diasConsumidos, long diasPrazo,
		long percentagemConsumida, SituacaoPrazo situacao, Long vagaId, LocalDateTime dataConsulta) {
}
