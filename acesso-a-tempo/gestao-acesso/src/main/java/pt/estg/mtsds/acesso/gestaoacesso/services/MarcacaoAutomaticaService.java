package pt.estg.mtsds.acesso.gestaoacesso.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import pt.estg.mtsds.acesso.gestaoacesso.data.EpisodioPrazoRepository;
import pt.estg.mtsds.acesso.gestaoacesso.data.VagaDisponivelRepository;
import pt.estg.mtsds.acesso.gestaoacesso.exceptions.DadosInvalidosException;
import pt.estg.mtsds.acesso.gestaoacesso.exceptions.NaoEncontradoException;
import pt.estg.mtsds.acesso.gestaoacesso.models.EpisodioPrazo;
import pt.estg.mtsds.acesso.gestaoacesso.models.VagaDisponivel;

/**
 * Marcação automática (R8): reserva as vagas livres para os episódios em espera
 * e confirma a marcação quando o agendamento a aceita.
 * <p>
 * Um episódio com vagaId e sem marcadoEm está reservado: a marcação foi
 * decidida aqui, mas o agendamento ainda não a confirmou. Com marcadoEm, está
 * marcado. As vagas reservadas saem da cópia local, para não serem atribuídas
 * duas vezes.
 */
@Service
@RequiredArgsConstructor
public class MarcacaoAutomaticaService {

	private final VagaDisponivelRepository vagaRepositorio;
	private final EpisodioPrazoRepository episodioRepositorio;
	private final RelogioService relogioService;

	/**
	 * Reserva as vagas livres de uma especialidade para os episódios à espera,
	 * segundo a R8 ({@link AtribuidorVagas}). Correr de novo sem vagas novas não
	 * muda nada, porque as vagas usadas saem da cópia e os episódios reservados
	 * deixam de estar à espera.
	 *
	 * @param especialidade
	 *            especialidade a marcar
	 * @return as atribuições feitas, para publicar como marcacao.decidida
	 */
	@Transactional
	public List<Atribuicao> marcar(String especialidade) {
		Map<Long, VagaDisponivel> vagas = porId(vagaRepositorio.findByEspecialidadeOrderByDataHoraAsc(especialidade),
				VagaDisponivel::getVagaId);
		Map<Long, EpisodioPrazo> episodios = porId(
				episodioRepositorio.findByEspecialidadeAndVagaIdIsNullAndEncerradoEmIsNull(especialidade),
				EpisodioPrazo::getPedidoId);

		List<Atribuicao> atribuicoes = AtribuidorVagas.atribuir(
				vagas.values().stream().map(v -> new VagaLivre(v.getVagaId(), v.getEspecialidade(), v.getDataHora()))
						.toList(),
				episodios.values().stream().map(e -> new EpisodioEmEspera(e.getPedidoId(), e.getEspecialidade(),
						e.getDataLimite(), e.getDataPedido())).toList());

		for (Atribuicao atribuicao : atribuicoes) {
			vagaRepositorio.delete(vagas.get(atribuicao.vagaId()));
			episodios.get(atribuicao.episodioId()).setVagaId(atribuicao.vagaId());
		}
		return atribuicoes;
	}

	/**
	 * Confirma a marcação quando chega consulta.agendada: o episódio fica marcado
	 * com a vaga e a data da consulta, a vaga sai da cópia e qualquer outro
	 * episódio que a tivesse reservada volta à fila. A data da marcação é o dia de
	 * hoje do {@link RelogioService}, ao início do dia.
	 *
	 * @param episodioId
	 *            episódio marcado (id do pedido)
	 * @param vagaId
	 *            vaga marcada pelo agendamento
	 * @param dataConsulta
	 *            dia e hora da consulta
	 * @throws DadosInvalidosException
	 *             se faltar algum dos dados
	 * @throws NaoEncontradoException
	 *             se o episódio não existir
	 */
	@Transactional
	public void confirmarMarcacao(Long episodioId, Long vagaId, LocalDateTime dataConsulta) {
		if (episodioId == null || vagaId == null || dataConsulta == null) {
			throw new DadosInvalidosException("MARCACAO_INCOMPLETA",
					"A confirmação da marcação tem de trazer episodioId, vagaId e dataConsulta.");
		}
		EpisodioPrazo episodio = episodioRepositorio.findById(episodioId)
				.orElseThrow(() -> new NaoEncontradoException("EPISODIO_NAO_ENCONTRADO", "Não existe o episódio "
						+ episodioId + ". Confirme o id do pedido enviado no evento consulta.agendada."));

		// O agendamento é o dono das marcações: a reserva feita aqui para outro
		// episódio deixa de valer
		for (EpisodioPrazo reservado : episodioRepositorio.findByVagaIdAndMarcadoEmIsNull(vagaId)) {
			if (!reservado.getPedidoId().equals(episodioId)) {
				reservado.setVagaId(null);
			}
		}
		episodio.setVagaId(vagaId);
		episodio.setMarcadoEm(relogioService.hoje().atStartOfDay());
		episodio.setDataConsulta(dataConsulta);
		vagaRepositorio.deleteById(vagaId);
	}

	private static <T> Map<Long, T> porId(List<T> elementos, Function<T, Long> id) {
		return elementos.stream().collect(Collectors.toMap(id, Function.identity()));
	}
}
