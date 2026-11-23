package pt.estg.mtsds.acesso.gestaoacesso.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import pt.estg.mtsds.acesso.gestaoacesso.data.EpisodioPrazoRepository;
import pt.estg.mtsds.acesso.gestaoacesso.models.EpisodioPrazo;
import pt.estg.mtsds.acesso.gestaoacesso.models.SituacaoPrazo;

/**
 * Verificação diária dos prazos dos episódios abertos: toca o alarme de risco
 * aos 75% do prazo e atualiza a situação de cada episódio (R4). A data de hoje
 * vem sempre do {@link RelogioService}, para a demonstração poder avançar o
 * tempo.
 */
@Service
@RequiredArgsConstructor
public class VerificacaoPrazosService {

	private final EpisodioPrazoRepository repositorio;
	private final RelogioService relogioService;

	/**
	 * Faz a verificação para a data de hoje do relógio, com duas pesquisas: os
	 * episódios cujo alarme chegou e ainda não tocou (passam a EM_RISCO) e os que
	 * passaram a data-limite (passam a INCUMPRIDO). Os episódios encerrados ficam
	 * de fora, e os já incumpridos não são reavaliados.
	 *
	 * @return os pedidos que passaram a EM_RISCO e a INCUMPRIDO nesta verificação
	 */
	@Transactional
	public ResultadoVerificacao verificar() {
		LocalDate hoje = relogioService.hoje();

		List<Long> passaramEmRisco = new ArrayList<>();
		for (EpisodioPrazo episodio : repositorio
				.findByEncerradoEmIsNullAndAlarmeRiscoEmIsNullAndDataAlarmeLessThanEqual(hoje)) {
			episodio.setAlarmeRiscoEm(hoje.atStartOfDay());
			SituacaoPrazo nova = CalculadoraPrazos.situacaoEm(episodio.getDataAlarme(), episodio.getDataLimite(), hoje);
			// Se a data-limite também já passou, fica para a pesquisa dos incumpridos
			if (nova == SituacaoPrazo.EM_RISCO && episodio.getSituacao() != SituacaoPrazo.EM_RISCO) {
				episodio.setSituacao(nova);
				passaramEmRisco.add(episodio.getPedidoId());
			}
		}

		// Só quem já passou a data-limite pode passar a INCUMPRIDO (R4)
		List<Long> passaramIncumpridos = new ArrayList<>();
		for (EpisodioPrazo episodio : repositorio
				.findByEncerradoEmIsNullAndSituacaoNotAndDataLimiteBefore(SituacaoPrazo.INCUMPRIDO, hoje)) {
			episodio.setSituacao(SituacaoPrazo.INCUMPRIDO);
			passaramIncumpridos.add(episodio.getPedidoId());
		}
		// As entidades lidas estão geridas pela transação: as alterações são gravadas
		// no fim, sem chamar save()
		return new ResultadoVerificacao(passaramEmRisco, passaramIncumpridos);
	}
}
