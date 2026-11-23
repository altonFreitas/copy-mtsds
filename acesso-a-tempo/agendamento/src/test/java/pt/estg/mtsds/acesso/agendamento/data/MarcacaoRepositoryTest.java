package pt.estg.mtsds.acesso.agendamento.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import pt.estg.mtsds.acesso.agendamento.auditing.JpaAuditingConfiguration;
import pt.estg.mtsds.acesso.agendamento.models.Agenda;
import pt.estg.mtsds.acesso.agendamento.models.EstadoMarcacao;
import pt.estg.mtsds.acesso.agendamento.models.EstadoVaga;
import pt.estg.mtsds.acesso.agendamento.models.Marcacao;
import pt.estg.mtsds.acesso.agendamento.models.OrigemMarcacao;
import pt.estg.mtsds.acesso.agendamento.models.Vaga;

/**
 * Teste do repositório das marcações com H2 (@DataJpaTest).
 */
@DataJpaTest
@Import(JpaAuditingConfiguration.class)
class MarcacaoRepositoryTest {

	private static final String CARDIOLOGIA = "Cardiologia";
	private static final LocalDate DIA = LocalDate.of(2026, 12, 10);

	@Autowired
	private MarcacaoRepository repositorio;

	@Autowired
	private TestEntityManager em;

	private Agenda agenda;

	@BeforeEach
	void criarAgenda() {
		agenda = em.persist(Agenda.builder().especialidade(CARDIOLOGIA).data(DIA).criadaPor("8").build());
	}

	private Vaga vaga(String especialidade, LocalDateTime dataHora) {
		return em.persist(Vaga.builder().agenda(agenda).especialidade(especialidade).dataHora(dataHora)
				.estado(EstadoVaga.OCUPADA).build());
	}

	private Marcacao marcacao(long episodioId, Vaga vaga, EstadoMarcacao estado) {
		return repositorio.save(Marcacao.builder().episodioId(episodioId).vaga(vaga).estado(estado)
				.origem(OrigemMarcacao.AUTOMATICA).autor("gestao-acesso").build());
	}

	// Grava na base e limpa a cache, para as leituras virem mesmo da tabela
	private void gravarELimpar() {
		em.flush();
		em.clear();
	}

	@Test
	void gravaEVoltaALerUmaMarcacao() {
		Vaga vaga = vaga(CARDIOLOGIA, DIA.atTime(9, 0));
		Long id = marcacao(1001L, vaga, EstadoMarcacao.AGENDADA).getId();
		gravarELimpar();

		Marcacao lida = repositorio.findById(id).orElseThrow();
		assertThat(lida.getEpisodioId()).isEqualTo(1001L);
		assertThat(lida.getVaga().getId()).isEqualTo(vaga.getId());
		assertThat(lida.getEstado()).isEqualTo(EstadoMarcacao.AGENDADA);
		assertThat(lida.getOrigem()).isEqualTo(OrigemMarcacao.AUTOMATICA);
		assertThat(lida.getAutor()).isEqualTo("gestao-acesso");
		assertThat(lida.getCriadaEm()).isNotNull();
	}

	@Test
	void marcacoesDoDiaSoTrazemAsDessaEspecialidadeNesseDia() {
		Long cedo = marcacao(1L, vaga(CARDIOLOGIA, DIA.atStartOfDay()), EstadoMarcacao.AGENDADA).getId();
		Long tarde = marcacao(2L, vaga(CARDIOLOGIA, DIA.atTime(23, 59)), EstadoMarcacao.REALIZADA).getId();
		marcacao(3L, vaga(CARDIOLOGIA, DIA.plusDays(1).atStartOfDay()), EstadoMarcacao.AGENDADA);
		marcacao(4L, vaga("Dermatologia", DIA.atTime(9, 0)), EstadoMarcacao.AGENDADA);
		gravarELimpar();

		assertThat(repositorio.marcacoesDoDia(CARDIOLOGIA, DIA)).extracting(Marcacao::getId)
				.containsExactlyInAnyOrder(cedo, tarde);
	}

	@Test
	void marcacaoAtivaIgnoraAsQueNaoEstaoAgendadas() {
		marcacao(1001L, vaga(CARDIOLOGIA, DIA.atTime(9, 0)), EstadoMarcacao.CANCELADA);
		Long ativa = marcacao(1001L, vaga(CARDIOLOGIA, DIA.atTime(10, 0)), EstadoMarcacao.AGENDADA).getId();
		gravarELimpar();

		assertThat(repositorio.marcacaoAtiva(1001L)).map(Marcacao::getId).contains(ativa);
		assertThat(repositorio.marcacaoAtiva(2002L)).isEmpty();
	}
}
