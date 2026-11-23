package pt.estg.mtsds.acesso.gestaoacesso.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Testes da atribuição de vagas (R8), sem Spring e com datas fixas. As listas
 * de entrada vêm fora de ordem, para confirmar que a ordem não depende delas.
 */
class AtribuidorVagasTest {

	private static final String CARDIOLOGIA = "Cardiologia";
	private static final String DERMATOLOGIA = "Dermatologia";

	private static VagaLivre vaga(long id, String especialidade, int dia) {
		return new VagaLivre(id, especialidade, LocalDateTime.of(2026, 12, dia, 9, 0));
	}

	private static EpisodioEmEspera episodio(long id, String especialidade, LocalDate limite, LocalDate pedido) {
		return new EpisodioEmEspera(id, especialidade, limite, pedido);
	}

	@Test
	void umaVagaVaiParaOEpisodioComOLimiteMaisProximo() {
		List<Atribuicao> resultado = AtribuidorVagas.atribuir(List.of(vaga(1, CARDIOLOGIA, 10)),
				List.of(episodio(20, CARDIOLOGIA, LocalDate.of(2027, 3, 1), LocalDate.of(2026, 11, 1)),
						episodio(10, CARDIOLOGIA, LocalDate.of(2027, 1, 15), LocalDate.of(2026, 11, 10))));

		assertThat(resultado).containsExactly(new Atribuicao(10L, 1L));
	}

	@Test
	void comAMesmaDataLimiteFicaOPedidoMaisAntigo() {
		LocalDate limite = LocalDate.of(2027, 1, 15);
		List<Atribuicao> resultado = AtribuidorVagas.atribuir(List.of(vaga(1, CARDIOLOGIA, 10)),
				List.of(episodio(20, CARDIOLOGIA, limite, LocalDate.of(2026, 11, 5)),
						episodio(10, CARDIOLOGIA, limite, LocalDate.of(2026, 11, 2))));

		assertThat(resultado).containsExactly(new Atribuicao(10L, 1L));
	}

	@Test
	void vagaDeOutraEspecialidadeNaoEMarcada() {
		List<Atribuicao> resultado = AtribuidorVagas.atribuir(List.of(vaga(1, DERMATOLOGIA, 10)),
				List.of(episodio(10, CARDIOLOGIA, LocalDate.of(2027, 1, 15), LocalDate.of(2026, 11, 2))));

		assertThat(resultado).isEmpty();
	}

	@Test
	void semEpisodiosDevolveListaVazia() {
		assertThat(AtribuidorVagas.atribuir(List.of(vaga(1, CARDIOLOGIA, 10), vaga(2, CARDIOLOGIA, 11)), List.of()))
				.isEmpty();
	}

	@Test
	void comMaisVagasDoQueEpisodiosSobramAsVagasMaisTardias() {
		List<Atribuicao> resultado = AtribuidorVagas.atribuir(
				List.of(vaga(3, CARDIOLOGIA, 20), vaga(1, CARDIOLOGIA, 10), vaga(2, CARDIOLOGIA, 15)),
				List.of(episodio(20, CARDIOLOGIA, LocalDate.of(2027, 3, 1), LocalDate.of(2026, 11, 1)),
						episodio(10, CARDIOLOGIA, LocalDate.of(2027, 1, 15), LocalDate.of(2026, 11, 10))));

		// A vaga mais cedo vai para o limite mais próximo; a vaga 3 sobra
		assertThat(resultado).containsExactly(new Atribuicao(10L, 1L), new Atribuicao(20L, 2L));
	}

	@Test
	void episodioJaUltrapassadoEMarcadoPrimeiro() {
		// Na data das vagas (dezembro de 2026), o episódio 30 já passou o limite
		List<Atribuicao> resultado = AtribuidorVagas.atribuir(
				List.of(vaga(2, CARDIOLOGIA, 15), vaga(1, CARDIOLOGIA, 10)),
				List.of(episodio(10, CARDIOLOGIA, LocalDate.of(2027, 1, 15), LocalDate.of(2026, 11, 10)),
						episodio(30, CARDIOLOGIA, LocalDate.of(2026, 11, 30), LocalDate.of(2026, 8, 1))));

		assertThat(resultado).containsExactly(new Atribuicao(30L, 1L), new Atribuicao(10L, 2L));
	}

	@Test
	void recusaIdsRepetidos() {
		assertThatThrownBy(
				() -> AtribuidorVagas.atribuir(List.of(vaga(1, CARDIOLOGIA, 10), vaga(1, CARDIOLOGIA, 11)), List.of()))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mais do que uma vez");
	}
}
