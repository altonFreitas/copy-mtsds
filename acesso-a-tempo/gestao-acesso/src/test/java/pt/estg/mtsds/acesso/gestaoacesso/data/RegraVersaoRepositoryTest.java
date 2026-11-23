package pt.estg.mtsds.acesso.gestaoacesso.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import pt.estg.mtsds.acesso.gestaoacesso.auditing.JpaAuditingConfiguration;
import pt.estg.mtsds.acesso.gestaoacesso.models.Prioridade;
import pt.estg.mtsds.acesso.gestaoacesso.models.RegraVersao;

/**
 * Teste do repositório das regras com H2 (@DataJpaTest). O componente de
 * arranque não faz parte deste contexto, por isso a tabela começa vazia.
 */
@DataJpaTest
@Import(JpaAuditingConfiguration.class)
class RegraVersaoRepositoryTest {

	private static final LocalDate INICIO = LocalDate.of(2017, 5, 4);

	@Autowired
	private RegraVersaoRepository repositorio;

	@Autowired
	private TestEntityManager em;

	private static RegraVersao regra(int versao, Prioridade prioridade, int dias) {
		return RegraVersao.builder().versao(versao).prioridade(prioridade).dias(dias).inicioVigencia(INICIO).build();
	}

	@Test
	void gravaEVoltaALerUmaRegra() {
		Long id = repositorio.save(regra(1, Prioridade.NORMAL, 120)).getId();
		// Grava na base e limpa a cache, para a leitura vir mesmo da tabela
		em.flush();
		em.clear();

		RegraVersao lida = repositorio.findById(id).orElseThrow();
		assertThat(lida.getVersao()).isEqualTo(1);
		assertThat(lida.getPrioridade()).isEqualTo(Prioridade.NORMAL);
		assertThat(lida.getDias()).isEqualTo(120);
		assertThat(lida.getInicioVigencia()).isEqualTo(INICIO);
		assertThat(lida.getCriadoEm()).isNotNull();
	}

	@Test
	void recusaDuasRegrasComAMesmaVersaoEPrioridade() {
		repositorio.saveAndFlush(regra(1, Prioridade.PRIORITARIA, 60));

		assertThatThrownBy(() -> repositorio.saveAndFlush(regra(1, Prioridade.PRIORITARIA, 90)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
