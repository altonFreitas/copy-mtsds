package pt.estg.mtsds.acesso.gestaoacesso.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import pt.estg.mtsds.acesso.gestaoacesso.auditing.JpaAuditingConfiguration;
import pt.estg.mtsds.acesso.gestaoacesso.models.VagaDisponivel;

/**
 * Teste do repositório das vagas disponíveis com H2 (@DataJpaTest).
 */
@DataJpaTest
@Import(JpaAuditingConfiguration.class)
class VagaDisponivelRepositoryTest {

	@Autowired
	private VagaDisponivelRepository repositorio;

	@Autowired
	private TestEntityManager em;

	private static VagaDisponivel vaga(long vagaId, String especialidade, LocalDateTime dataHora) {
		return VagaDisponivel.builder().vagaId(vagaId).especialidade(especialidade).dataHora(dataHora).build();
	}

	@Test
	void gravaEVoltaALerUmaVaga() {
		repositorio.save(vaga(501L, "Cardiologia", LocalDateTime.of(2026, 12, 10, 9, 30)));
		// Grava na base e limpa a cache, para a leitura vir mesmo da tabela
		em.flush();
		em.clear();

		VagaDisponivel lida = repositorio.findById(501L).orElseThrow();
		assertThat(lida.getEspecialidade()).isEqualTo("Cardiologia");
		assertThat(lida.getDataHora()).isEqualTo(LocalDateTime.of(2026, 12, 10, 9, 30));
		assertThat(lida.getRecebidaEm()).isNotNull();
	}

	@Test
	void devolveAsVagasDaEspecialidadePorOrdemDeDataHora() {
		repositorio.save(vaga(1L, "Cardiologia", LocalDateTime.of(2026, 12, 15, 9, 0)));
		repositorio.save(vaga(2L, "Cardiologia", LocalDateTime.of(2026, 12, 10, 14, 0)));
		repositorio.save(vaga(3L, "Cardiologia", LocalDateTime.of(2026, 12, 10, 9, 0)));
		repositorio.save(vaga(4L, "Dermatologia", LocalDateTime.of(2026, 12, 1, 9, 0)));
		em.flush();
		em.clear();

		assertThat(repositorio.findByEspecialidadeOrderByDataHoraAsc("Cardiologia"))
				.extracting(VagaDisponivel::getVagaId).containsExactly(3L, 2L, 1L);
	}
}
