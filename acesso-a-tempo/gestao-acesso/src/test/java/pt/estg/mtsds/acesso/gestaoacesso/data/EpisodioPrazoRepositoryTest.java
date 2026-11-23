package pt.estg.mtsds.acesso.gestaoacesso.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import pt.estg.mtsds.acesso.gestaoacesso.auditing.JpaAuditingConfiguration;
import pt.estg.mtsds.acesso.gestaoacesso.models.EpisodioPrazo;
import pt.estg.mtsds.acesso.gestaoacesso.models.Prioridade;
import pt.estg.mtsds.acesso.gestaoacesso.models.SituacaoPrazo;

/**
 * Teste do repositório com H2 (@DataJpaTest). A configuração de auditoria é
 * importada porque o @DataJpaTest só carrega os componentes JPA.
 */
@DataJpaTest
@Import(JpaAuditingConfiguration.class)
class EpisodioPrazoRepositoryTest {

	@Autowired
	private EpisodioPrazoRepository repositorio;

	@Autowired
	private TestEntityManager em;

	@Test
	void gravaEVoltaALerUmEpisodio() {
		EpisodioPrazo episodio = EpisodioPrazo.builder().pedidoId(1001L).especialidade("Cardiologia")
				.dataPedido(LocalDate.of(2026, 11, 2)).prioridade(Prioridade.PRIORITARIA).regraVersao(1)
				.dataLimite(LocalDate.of(2026, 12, 2)).dataAlarme(LocalDate.of(2026, 11, 25)).build();

		repositorio.save(episodio);
		// Grava na base e limpa a cache, para a leitura vir mesmo da tabela
		em.flush();
		em.clear();

		EpisodioPrazo lido = repositorio.findById(1001L).orElseThrow();
		assertThat(lido.getEspecialidade()).isEqualTo("Cardiologia");
		assertThat(lido.getDataPedido()).isEqualTo(LocalDate.of(2026, 11, 2));
		assertThat(lido.getPrioridade()).isEqualTo(Prioridade.PRIORITARIA);
		assertThat(lido.getRegraVersao()).isEqualTo(1);
		assertThat(lido.getDataLimite()).isEqualTo(LocalDate.of(2026, 12, 2));
		assertThat(lido.getDataAlarme()).isEqualTo(LocalDate.of(2026, 11, 25));
		assertThat(lido.getSituacao()).isEqualTo(SituacaoPrazo.DENTRO_PRAZO);
		assertThat(lido.getAlarmeRiscoEm()).isNull();
		assertThat(lido.getEncerradoEm()).isNull();
		assertThat(lido.getCriadoEm()).isNotNull();
		assertThat(lido.getAtualizadoEm()).isNotNull();
	}

	@Test
	void pesquisaDoAlarmeTrazSoOsAbertosSemAlarmeComOAlarmeJaChegado() {
		LocalDate hoje = LocalDate.of(2026, 11, 25);
		repositorio.save(episodio(1L, hoje, hoje.plusDays(8), SituacaoPrazo.DENTRO_PRAZO, null));
		EpisodioPrazo encerrado = episodio(2L, hoje, hoje.plusDays(8), SituacaoPrazo.DENTRO_PRAZO, null);
		encerrado.setEncerradoEm(LocalDateTime.of(2026, 11, 20, 10, 0));
		repositorio.save(encerrado);
		repositorio.save(episodio(3L, hoje, hoje.plusDays(8), SituacaoPrazo.EM_RISCO, hoje.atStartOfDay()));
		repositorio.save(episodio(4L, hoje.plusDays(1), hoje.plusDays(9), SituacaoPrazo.DENTRO_PRAZO, null));
		em.flush();
		em.clear();

		assertThat(repositorio.findByEncerradoEmIsNullAndAlarmeRiscoEmIsNullAndDataAlarmeLessThanEqual(hoje))
				.extracting(EpisodioPrazo::getPedidoId).containsExactly(1L);
	}

	@Test
	void pesquisaDosIncumpridosTrazSoOsAbertosComADataLimiteJaPassada() {
		LocalDate hoje = LocalDate.of(2026, 11, 25);
		LocalDateTime alarme = hoje.minusDays(9).atStartOfDay();
		repositorio.save(episodio(1L, hoje.minusDays(9), hoje.minusDays(1), SituacaoPrazo.EM_RISCO, alarme));
		repositorio.save(episodio(2L, hoje.minusDays(8), hoje, SituacaoPrazo.EM_RISCO, alarme));
		repositorio.save(episodio(3L, hoje, hoje.plusDays(8), SituacaoPrazo.EM_RISCO, alarme));
		repositorio.save(episodio(4L, hoje.minusDays(9), hoje.minusDays(1), SituacaoPrazo.INCUMPRIDO, alarme));
		EpisodioPrazo encerrado = episodio(5L, hoje.minusDays(9), hoje.minusDays(1), SituacaoPrazo.EM_RISCO, alarme);
		encerrado.setEncerradoEm(LocalDateTime.of(2026, 11, 20, 10, 0));
		repositorio.save(encerrado);
		em.flush();
		em.clear();

		// Ficam de fora o dia-limite hoje (2), a data-limite no futuro (3), o já
		// incumprido (4) e o encerrado (5)
		assertThat(repositorio.findByEncerradoEmIsNullAndSituacaoNotAndDataLimiteBefore(SituacaoPrazo.INCUMPRIDO, hoje))
				.extracting(EpisodioPrazo::getPedidoId).containsExactly(1L);
	}

	@Test
	void pesquisaDaMarcacaoTrazSoOsEpisodiosAEsperaDaEspecialidade() {
		LocalDate hoje = LocalDate.of(2026, 11, 25);
		EpisodioPrazo marcado = episodio(1L, hoje, hoje.plusDays(8), SituacaoPrazo.DENTRO_PRAZO, null);
		marcado.setVagaId(501L);
		marcado.setMarcadoEm(LocalDateTime.of(2026, 11, 24, 15, 0));
		repositorio.save(marcado);
		EpisodioPrazo encerrado = episodio(2L, hoje, hoje.plusDays(8), SituacaoPrazo.DENTRO_PRAZO, null);
		encerrado.setEncerradoEm(LocalDateTime.of(2026, 11, 20, 10, 0));
		repositorio.save(encerrado);
		repositorio.save(episodio(3L, hoje, hoje.plusDays(8), SituacaoPrazo.DENTRO_PRAZO, null));
		EpisodioPrazo outraEspecialidade = episodio(4L, hoje, hoje.plusDays(8), SituacaoPrazo.DENTRO_PRAZO, null);
		outraEspecialidade.setEspecialidade("Dermatologia");
		repositorio.save(outraEspecialidade);
		em.flush();
		em.clear();

		assertThat(repositorio.findByEspecialidadeAndVagaIdIsNullAndEncerradoEmIsNull("Cardiologia"))
				.extracting(EpisodioPrazo::getPedidoId).containsExactly(3L);
	}

	private static EpisodioPrazo episodio(Long pedidoId, LocalDate dataAlarme, LocalDate dataLimite,
			SituacaoPrazo situacao, LocalDateTime alarmeRiscoEm) {
		return EpisodioPrazo.builder().pedidoId(pedidoId).especialidade("Cardiologia")
				.dataPedido(dataLimite.minusDays(30)).prioridade(Prioridade.MUITO_PRIORITARIA).regraVersao(1)
				.dataLimite(dataLimite).dataAlarme(dataAlarme).situacao(situacao).alarmeRiscoEm(alarmeRiscoEm).build();
	}
}
