package pt.estg.mtsds.acesso.gestaoacesso.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import pt.estg.mtsds.acesso.gestaoacesso.auditing.JpaAuditingConfiguration;
import pt.estg.mtsds.acesso.gestaoacesso.data.EpisodioPrazoRepository;
import pt.estg.mtsds.acesso.gestaoacesso.data.VagaDisponivelRepository;
import pt.estg.mtsds.acesso.gestaoacesso.models.EpisodioPrazo;
import pt.estg.mtsds.acesso.gestaoacesso.models.Prioridade;
import pt.estg.mtsds.acesso.gestaoacesso.models.VagaDisponivel;

/**
 * Testes de integração da marcação automática com H2 (@DataJpaTest). Só o
 * relógio é simulado, para a data da marcação ser fixa.
 */
@DataJpaTest
@Import({JpaAuditingConfiguration.class, MarcacaoAutomaticaService.class})
class MarcacaoAutomaticaServiceTest {

	private static final String CARDIOLOGIA = "Cardiologia";
	private static final LocalDate HOJE = LocalDate.of(2026, 12, 1);

	@Autowired
	private MarcacaoAutomaticaService servico;

	@Autowired
	private VagaDisponivelRepository vagas;

	@Autowired
	private EpisodioPrazoRepository episodios;

	@Autowired
	private TestEntityManager em;

	@MockitoBean
	private RelogioService relogioService;

	private void gravarVaga(long vagaId, int dia) {
		vagas.save(VagaDisponivel.builder().vagaId(vagaId).especialidade(CARDIOLOGIA)
				.dataHora(LocalDateTime.of(2026, 12, dia, 9, 0)).build());
	}

	private void gravarEpisodio(long pedidoId, LocalDate dataLimite) {
		episodios.save(EpisodioPrazo.builder().pedidoId(pedidoId).especialidade(CARDIOLOGIA)
				.dataPedido(dataLimite.minusDays(30)).prioridade(Prioridade.MUITO_PRIORITARIA).regraVersao(1)
				.dataLimite(dataLimite).dataAlarme(dataLimite.minusDays(8)).build());
	}

	// Grava na base e limpa a cache, para as leituras virem mesmo das tabelas
	private void gravarELimpar() {
		em.flush();
		em.clear();
	}

	private EpisodioPrazo lerEpisodio(long pedidoId) {
		return episodios.findById(pedidoId).orElseThrow();
	}

	@Test
	void duasVagasTresEpisodiosReservaDoisEDeixaUmAEspera() {
		gravarVaga(501, 10);
		gravarVaga(502, 15);
		gravarEpisodio(1, LocalDate.of(2027, 2, 1));
		gravarEpisodio(2, LocalDate.of(2026, 12, 20));
		gravarEpisodio(3, LocalDate.of(2027, 1, 10));
		gravarELimpar();

		List<Atribuicao> atribuicoes = servico.marcar(CARDIOLOGIA);
		gravarELimpar();

		assertThat(atribuicoes).containsExactly(new Atribuicao(2L, 501L), new Atribuicao(3L, 502L));
		assertThat(lerEpisodio(2).getVagaId()).isEqualTo(501L);
		assertThat(lerEpisodio(2).getMarcadoEm()).isNull();
		assertThat(lerEpisodio(3).getVagaId()).isEqualTo(502L);
		assertThat(lerEpisodio(1).getVagaId()).isNull();
		assertThat(vagas.count()).isZero();
	}

	@Test
	void marcarDuasVezesSeguidasNaoReatribuiNada() {
		gravarVaga(501, 10);
		gravarEpisodio(1, LocalDate.of(2027, 2, 1));
		gravarEpisodio(2, LocalDate.of(2026, 12, 20));
		gravarELimpar();

		servico.marcar(CARDIOLOGIA);
		gravarELimpar();
		List<Atribuicao> segunda = servico.marcar(CARDIOLOGIA);
		gravarELimpar();

		assertThat(segunda).isEmpty();
		assertThat(lerEpisodio(2).getVagaId()).isEqualTo(501L);
		assertThat(lerEpisodio(1).getVagaId()).isNull();
	}

	@Test
	void confirmarMarcacaoPreencheOMarcadoEmADataDaConsultaEApagaAVaga() {
		when(relogioService.hoje()).thenReturn(HOJE);
		gravarVaga(501, 10);
		gravarEpisodio(1, LocalDate.of(2027, 2, 1));
		gravarELimpar();

		LocalDateTime dataConsulta = LocalDateTime.of(2026, 12, 10, 9, 0);
		servico.confirmarMarcacao(1L, 501L, dataConsulta);
		gravarELimpar();

		EpisodioPrazo marcado = lerEpisodio(1);
		assertThat(marcado.getVagaId()).isEqualTo(501L);
		assertThat(marcado.getMarcadoEm()).isEqualTo(HOJE.atStartOfDay());
		assertThat(marcado.getDataConsulta()).isEqualTo(dataConsulta);
		assertThat(vagas.existsById(501L)).isFalse();
	}

	@Test
	void confirmarUmaVagaReservadaParaOutroEpisodioLibertaOReservado() {
		when(relogioService.hoje()).thenReturn(HOJE);
		gravarVaga(501, 10);
		gravarEpisodio(1, LocalDate.of(2026, 12, 20));
		gravarEpisodio(2, LocalDate.of(2027, 2, 1));
		gravarELimpar();
		// A marcação automática reserva a vaga 501 para o episódio 1
		servico.marcar(CARDIOLOGIA);
		gravarELimpar();

		// O agendamento marca a vaga 501 para o episódio 2
		servico.confirmarMarcacao(2L, 501L, LocalDateTime.of(2026, 12, 10, 9, 0));
		gravarELimpar();

		assertThat(lerEpisodio(1).getVagaId()).isNull();
		assertThat(lerEpisodio(2).getVagaId()).isEqualTo(501L);
		assertThat(lerEpisodio(2).getMarcadoEm()).isEqualTo(HOJE.atStartOfDay());
	}
}
