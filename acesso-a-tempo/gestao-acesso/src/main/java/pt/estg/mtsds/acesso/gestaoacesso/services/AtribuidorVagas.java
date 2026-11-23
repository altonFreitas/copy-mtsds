package pt.estg.mtsds.acesso.gestaoacesso.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Atribuição das vagas livres aos episódios em espera (R8). Só funções puras:
 * não acede à base de dados nem ao relógio, e não altera as listas recebidas.
 * <p>
 * R8: por especialidade, as vagas mais cedo vão para os episódios com a
 * data-limite mais próxima; com a mesma data-limite, fica o pedido mais antigo.
 * Cada vaga e cada episódio entram no máximo numa atribuição, e uma vaga só
 * serve episódios da mesma especialidade.
 */
public final class AtribuidorVagas {

	// Desempates finais pelo id, para o resultado não depender da ordem da entrada
	private static final Comparator<VagaLivre> ORDEM_VAGAS = Comparator.comparing(VagaLivre::dataHora)
			.thenComparing(VagaLivre::vagaId);

	private static final Comparator<EpisodioEmEspera> ORDEM_EPISODIOS = Comparator
			.comparing(EpisodioEmEspera::dataLimite).thenComparing(EpisodioEmEspera::dataPedido)
			.thenComparing(EpisodioEmEspera::episodioId);

	private AtribuidorVagas() {
	}

	/**
	 * R8: emparelha, em cada especialidade, a vaga mais cedo com o episódio de
	 * data-limite mais próxima, a segunda vaga com o segundo episódio, e assim por
	 * diante. Um episódio com a data-limite já ultrapassada fica à frente dos
	 * outros, porque a sua data-limite é a mais antiga. As vagas ou os episódios
	 * que sobram ficam por atribuir.
	 *
	 * @param vagas
	 *            vagas livres, de qualquer especialidade
	 * @param episodios
	 *            episódios em espera, de qualquer especialidade
	 * @return as atribuições, por ordem alfabética da especialidade e, dentro de
	 *         cada uma, pela ordem das vagas; vazia se não houver pares possíveis
	 * @throws IllegalArgumentException
	 *             se uma lista ou um elemento for nulo, se faltar um campo, ou se
	 *             houver ids repetidos
	 */
	public static List<Atribuicao> atribuir(List<VagaLivre> vagas, List<EpisodioEmEspera> episodios) {
		exigirLista(vagas, "vagas");
		exigirLista(episodios, "episodios");
		vagas.forEach(AtribuidorVagas::validar);
		episodios.forEach(AtribuidorVagas::validar);
		exigirIdsUnicos(vagas, VagaLivre::vagaId, "vaga");
		exigirIdsUnicos(episodios, EpisodioEmEspera::episodioId, "episódio");

		Map<String, List<VagaLivre>> vagasPorEspecialidade = agrupar(vagas, VagaLivre::especialidade);
		Map<String, List<EpisodioEmEspera>> episodiosPorEspecialidade = agrupar(episodios,
				EpisodioEmEspera::especialidade);

		List<Atribuicao> atribuicoes = new ArrayList<>();
		vagasPorEspecialidade.forEach((especialidade, vagasDaEspecialidade) -> {
			List<EpisodioEmEspera> emEspera = episodiosPorEspecialidade.getOrDefault(especialidade, new ArrayList<>());
			vagasDaEspecialidade.sort(ORDEM_VAGAS);
			emEspera.sort(ORDEM_EPISODIOS);
			int pares = Math.min(vagasDaEspecialidade.size(), emEspera.size());
			for (int i = 0; i < pares; i++) {
				atribuicoes.add(new Atribuicao(emEspera.get(i).episodioId(), vagasDaEspecialidade.get(i).vagaId()));
			}
		});
		return List.copyOf(atribuicoes);
	}

	private static <T> Map<String, List<T>> agrupar(List<T> elementos, Function<T, String> especialidade) {
		Map<String, List<T>> grupos = new TreeMap<>();
		for (T elemento : elementos) {
			grupos.computeIfAbsent(especialidade.apply(elemento), e -> new ArrayList<>()).add(elemento);
		}
		return grupos;
	}

	private static void exigirLista(List<?> lista, String nome) {
		if (lista == null || lista.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("A lista " + nome + " é obrigatória e não pode ter elementos vazios.");
		}
	}

	private static void validar(VagaLivre vaga) {
		if (vaga.vagaId() == null || vaga.especialidade() == null || vaga.dataHora() == null) {
			throw new IllegalArgumentException(
					"A vaga " + vaga + " tem de ter vagaId, especialidade e dataHora preenchidos.");
		}
	}

	private static void validar(EpisodioEmEspera episodio) {
		if (episodio.episodioId() == null || episodio.especialidade() == null || episodio.dataLimite() == null
				|| episodio.dataPedido() == null) {
			throw new IllegalArgumentException("O episódio " + episodio
					+ " tem de ter episodioId, especialidade, dataLimite e dataPedido preenchidos.");
		}
	}

	private static <T> void exigirIdsUnicos(List<T> elementos, Function<T, Long> id, String nome) {
		Set<Long> vistos = new HashSet<>();
		for (T elemento : elementos) {
			if (!vistos.add(id.apply(elemento))) {
				throw new IllegalArgumentException(
						"O id de " + nome + " " + id.apply(elemento) + " aparece mais do que uma vez na lista.");
			}
		}
	}
}
