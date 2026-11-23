package pt.estg.mtsds.acesso.gestaoacesso.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import pt.estg.mtsds.acesso.gestaoacesso.auditing.JpaAuditingConfiguration;
import pt.estg.mtsds.acesso.gestaoacesso.models.Relogio;

/**
 * Teste do repositório do relógio com H2 (@DataJpaTest).
 */
@DataJpaTest
@Import(JpaAuditingConfiguration.class)
class RelogioRepositoryTest {

	@Autowired
	private RelogioRepository repositorio;

	@Autowired
	private TestEntityManager em;

	@Test
	void gravaEVoltaALerORelogio() {
		repositorio.save(Relogio.builder().dataAtual(LocalDate.of(2026, 11, 17)).build());
		// Grava na base e limpa a cache, para a leitura vir mesmo da tabela
		em.flush();
		em.clear();

		Relogio lido = repositorio.findById(Relogio.ID).orElseThrow();
		assertThat(lido.getId()).isEqualTo(Relogio.ID);
		assertThat(lido.getDataAtual()).isEqualTo(LocalDate.of(2026, 11, 17));
		assertThat(lido.getAtualizadoEm()).isNotNull();
	}
}
