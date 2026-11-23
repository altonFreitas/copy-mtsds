package pt.estg.mtsds.acesso.gestaoacesso.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import pt.estg.mtsds.acesso.gestaoacesso.auditing.JpaAuditingConfiguration;
import pt.estg.mtsds.acesso.gestaoacesso.models.EventoProcessado;

/**
 * Teste do repositório dos eventos processados com H2 (@DataJpaTest).
 */
@DataJpaTest
@Import(JpaAuditingConfiguration.class)
class EventoProcessadoRepositoryTest {

	private static final String EVENTO_ID = "3f2c9a1e-7b4d-4e8a-9c21-5d6e7f8a9b0c";

	@Autowired
	private EventoProcessadoRepository repositorio;

	@Autowired
	private TestEntityManager em;

	private void gravarEvento() {
		repositorio.save(EventoProcessado.builder().eventoId(EVENTO_ID).tipo("pedido.triado").build());
		// Grava na base e limpa a cache, para a leitura vir mesmo da tabela
		em.flush();
		em.clear();
	}

	@Test
	void gravaEVoltaALerUmEvento() {
		gravarEvento();

		EventoProcessado lido = repositorio.findById(EVENTO_ID).orElseThrow();
		assertThat(lido.getTipo()).isEqualTo("pedido.triado");
		assertThat(lido.getProcessadoEm()).isNotNull();
	}

	@Test
	void existsByIdDistingueEventosGravadosDosQueNaoExistem() {
		gravarEvento();

		assertThat(repositorio.existsById(EVENTO_ID)).isTrue();
		assertThat(repositorio.existsById("evento-que-nao-existe")).isFalse();
	}
}
