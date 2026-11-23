package pt.estg.mtsds.acesso.agendamento.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import pt.estg.mtsds.acesso.agendamento.auditing.JpaAuditingConfiguration;
import pt.estg.mtsds.acesso.agendamento.models.Agenda;
import pt.estg.mtsds.acesso.agendamento.models.EstadoVaga;
import pt.estg.mtsds.acesso.agendamento.models.Vaga;

/**
 * Teste do repositório das vagas com H2 (@DataJpaTest): pesquisa das vagas
 * livres e bloqueio otimista.
 */
@DataJpaTest
@Import(JpaAuditingConfiguration.class)
class VagaRepositoryTest {

	private static final String CARDIOLOGIA = "Cardiologia";

	@Autowired
	private VagaRepository repositorio;

	@Autowired
	private TestEntityManager em;

	private Agenda agenda;

	@BeforeEach
	void criarAgenda() {
		agenda = em.persist(
				Agenda.builder().especialidade(CARDIOLOGIA).data(LocalDate.of(2026, 12, 10)).criadaPor("8").build());
	}

	private Vaga vaga(String especialidade, LocalDateTime dataHora, EstadoVaga estado) {
		return repositorio.save(
				Vaga.builder().agenda(agenda).especialidade(especialidade).dataHora(dataHora).estado(estado).build());
	}

	// Grava na base e limpa a cache, para as leituras virem mesmo da tabela
	private void gravarELimpar() {
		em.flush();
		em.clear();
	}

	@Test
	void gravaEVoltaALerUmaVaga() {
		Long id = vaga(CARDIOLOGIA, LocalDateTime.of(2026, 12, 10, 9, 0), EstadoVaga.LIVRE).getId();
		gravarELimpar();

		Vaga lida = repositorio.findById(id).orElseThrow();
		assertThat(lida.getEspecialidade()).isEqualTo(CARDIOLOGIA);
		assertThat(lida.getDataHora()).isEqualTo(LocalDateTime.of(2026, 12, 10, 9, 0));
		assertThat(lida.getEstado()).isEqualTo(EstadoVaga.LIVRE);
		assertThat(lida.getAgenda().getId()).isEqualTo(agenda.getId());
		assertThat(lida.getVersao()).isZero();
	}

	@Test
	void vagasLivresVemFiltradasEOrdenadas() {
		Long tarde = vaga(CARDIOLOGIA, LocalDateTime.of(2026, 12, 12, 23, 30), EstadoVaga.LIVRE).getId();
		Long cedo = vaga(CARDIOLOGIA, LocalDateTime.of(2026, 12, 10, 9, 0), EstadoVaga.LIVRE).getId();
		Long meio = vaga(CARDIOLOGIA, LocalDateTime.of(2026, 12, 11, 14, 0), EstadoVaga.LIVRE).getId();
		vaga(CARDIOLOGIA, LocalDateTime.of(2026, 12, 10, 10, 0), EstadoVaga.OCUPADA);
		vaga(CARDIOLOGIA, LocalDateTime.of(2026, 12, 13, 9, 0), EstadoVaga.LIVRE);
		vaga("Dermatologia", LocalDateTime.of(2026, 12, 10, 9, 0), EstadoVaga.LIVRE);
		gravarELimpar();

		// Ficam de fora a ocupada, a de depois do dia 12 e a de outra especialidade
		assertThat(repositorio.vagasLivresAte(CARDIOLOGIA, LocalDate.of(2026, 12, 12))).extracting(Vaga::getId)
				.containsExactly(cedo, meio, tarde);
	}

	@Test
	void segundaAlteracaoComAMesmaVersaoFalha() {
		Long id = vaga(CARDIOLOGIA, LocalDateTime.of(2026, 12, 10, 9, 0), EstadoVaga.LIVRE).getId();
		gravarELimpar();

		// Duas leituras da mesma vaga, ambas com a versão 0
		Vaga primeira = repositorio.findById(id).orElseThrow();
		em.detach(primeira);
		Vaga segunda = repositorio.findById(id).orElseThrow();
		em.detach(segunda);

		primeira.setEstado(EstadoVaga.OCUPADA);
		repositorio.saveAndFlush(primeira);

		segunda.setEstado(EstadoVaga.OCUPADA);
		assertThatThrownBy(() -> repositorio.saveAndFlush(segunda))
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}
}
