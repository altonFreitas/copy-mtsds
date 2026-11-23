package pt.estg.mtsds.acesso.gestaoacesso.services;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import pt.estg.mtsds.acesso.gestaoacesso.data.EpisodioPrazoRepository;
import pt.estg.mtsds.acesso.gestaoacesso.dtos.PrazoDTO;
import pt.estg.mtsds.acesso.gestaoacesso.exceptions.NaoEncontradoException;
import pt.estg.mtsds.acesso.gestaoacesso.models.EpisodioPrazo;
import pt.estg.mtsds.acesso.gestaoacesso.models.SituacaoPrazo;

/**
 * Consulta do prazo TMRG de um episódio. Os dias consumidos e a situação são
 * calculados para a data de hoje do {@link RelogioService}, por isso acompanham
 * o relógio de demonstração.
 */
@Service
@RequiredArgsConstructor
public class PrazoService {

	private final EpisodioPrazoRepository repositorio;
	private final RelogioService relogioService;

	/**
	 * Lê o prazo de um episódio e calcula o consumo até hoje. A situação vem da
	 * {@link CalculadoraPrazos}, para estar certa mesmo antes da verificação diária
	 * correr. Um episódio encerrado conta até ao dia do fecho, e uma situação
	 * gravada como INCUMPRIDO mantém-se.
	 *
	 * @param episodioId
	 *            episódio (id do pedido)
	 * @return o prazo, com os dias e a percentagem consumidos
	 * @throws NaoEncontradoException
	 *             PRAZO_NAO_ENCONTRADO, se o episódio não existir
	 */
	@Transactional(readOnly = true)
	public PrazoDTO obter(Long episodioId) {
		EpisodioPrazo episodio = repositorio.findById(episodioId).orElseThrow(
				() -> new NaoEncontradoException("PRAZO_NAO_ENCONTRADO", "Não existe prazo para o episódio "
						+ episodioId + ". Confirme o id do pedido; o prazo só existe depois da triagem."));

		// Um episódio encerrado deixa de consumir prazo: conta até ao dia do fecho
		LocalDate referencia = episodio.getEncerradoEm() != null
				? episodio.getEncerradoEm().toLocalDate()
				: relogioService.hoje();
		// Antes da data do pedido não há dias consumidos
		long diasConsumidos = Math.max(0, ChronoUnit.DAYS.between(episodio.getDataPedido(), referencia));
		long diasPrazo = ChronoUnit.DAYS.between(episodio.getDataPedido(), episodio.getDataLimite());
		long percentagem = diasConsumidos * 100 / diasPrazo;
		// Um incumprimento gravado não volta atrás, mesmo com o relógio reposto
		SituacaoPrazo situacao = episodio.getSituacao() == SituacaoPrazo.INCUMPRIDO
				? SituacaoPrazo.INCUMPRIDO
				: CalculadoraPrazos.situacaoEm(episodio.getDataAlarme(), episodio.getDataLimite(), referencia);

		return new PrazoDTO(episodio.getPedidoId(), episodio.getEspecialidade(), episodio.getPrioridade(),
				episodio.getDataPedido(), episodio.getDataLimite(), episodio.getDataAlarme(), episodio.getRegraVersao(),
				diasConsumidos, diasPrazo, percentagem, situacao, episodio.getVagaId(), episodio.getDataConsulta());
	}
}
