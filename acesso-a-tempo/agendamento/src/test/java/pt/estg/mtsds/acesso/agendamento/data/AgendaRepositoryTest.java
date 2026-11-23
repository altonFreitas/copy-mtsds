package pt.estg.mtsds.acesso.agendamento.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import pt.estg.mtsds.acesso.agendamento.auditing.JpaAuditingConfiguration;
import pt.estg.mtsds.acesso.agendamento.models.Agenda;

/**
 * Teste do repositório das agendas com H2 (@DataJpaTest).
 */
@DataJpaTest
@Import(JpaAuditingConfiguration.class)
class AgendaRepositoryTest {

	@Autowired
	private AgendaRepository repositorio;

	@Autowired
	private TestEntityManager em;

	@Test
	void gravaEVoltaALerUmaAgenda() {
		Long id = repositorio.save(
				Agenda.builder().especialidade("Cardiologia").data(LocalDate.of(2026, 12, 10)).criadaPor("8").build())
				.getId();
		// Grava na base e limpa a cache, para a leitura vir mesmo da tabela
		em.flush();
		em.clear();

		Agenda lida = repositorio.findById(id).orElseThrow();
		assertThat(lida.getEspecialidade()).isEqualTo("Cardiologia");
		assertThat(lida.getData()).isEqualTo(LocalDate.of(2026, 12, 10));
		assertThat(lida.getCriadaPor()).isEqualTo("8");
		assertThat(lida.getCriadaEm()).isNotNull();
	}
}
