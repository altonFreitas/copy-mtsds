package pt.estg.mtsds.acesso.agendamento.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import pt.estg.mtsds.acesso.agendamento.auditing.JpaAuditingConfiguration;
import pt.estg.mtsds.acesso.agendamento.data.MarcacaoRepository;
import pt.estg.mtsds.acesso.agendamento.data.VagaRepository;
import pt.estg.mtsds.acesso.agendamento.dtos.AgendaDTO;
import pt.estg.mtsds.acesso.agendamento.dtos.CriarAgendaDTO;
import pt.estg.mtsds.acesso.agendamento.dtos.MarcacaoDTO;
import pt.estg.mtsds.acesso.agendamento.exceptions.ErroNegocioException;
import pt.estg.mtsds.acesso.agendamento.exceptions.NaoEncontradoException;
import pt.estg.mtsds.acesso.agendamento.exceptions.RegraNegocioException;
import pt.estg.mtsds.acesso.agendamento.exceptions.TransicaoInvalidaException;
import pt.estg.mtsds.acesso.agendamento.models.EstadoMarcacao;
import pt.estg.mtsds.acesso.agendamento.models.EstadoVaga;
import pt.estg.mtsds.acesso.agendamento.models.Marcacao;
import pt.estg.mtsds.acesso.agendamento.models.OrigemMarcacao;

/**
 * Testes de integração do serviço de agendamento com H2 (@DataJpaTest).
 */
@DataJpaTest
@Import({JpaAuditingConfiguration.class, AgendamentoService.class})
class AgendamentoServiceTest {

	private static final String CARDIOLOGIA = "Cardiologia";
	private static final LocalDate DIA = LocalDate.of(2026, 12, 10);

	@Autowired
	private AgendamentoService servico;

	@Autowired
	private VagaRepository vagas;

	@Autowired
	private MarcacaoRepository marcacoes;

	@Autowired
	private TestEntityManager em;

	private AgendaDTO criarAgenda(LocalTime... horas) {
		return servico.criarAgenda(new CriarAgendaDTO(CARDIOLOGIA, DIA, List.of(horas)), "8");
	}

	private Long primeiraVaga() {
		return criarAgenda(LocalTime.of(9, 0)).vagas().get(0).id();
	}

	// Grava na base e limpa a cache, para as leituras virem mesmo das tabelas
	private void gravarELimpar() {
		em.flush();
		em.clear();
	}

	// Agendas

	@Test
	void criarAgendaCriaUmaVagaLivrePorHora() {
		AgendaDTO agenda = criarAgenda(LocalTime.of(11, 0), LocalTime.of(9, 0), LocalTime.of(10, 0));
		gravarELimpar();

		assertThat(agenda.especialidade()).isEqualTo(CARDIOLOGIA);
		assertThat(agenda.data()).isEqualTo(DIA);
		assertThat(agenda.vagas()).extracting(v -> v.dataHora().toLocalTime()).containsExactly(LocalTime.of(9, 0),
				LocalTime.of(10, 0), LocalTime.of(11, 0));
		assertThat(agenda.vagas()).allSatisfy(v -> assertThat(v.estado()).isEqualTo(EstadoVaga.LIVRE));
		assertThat(vagas.count()).isEqualTo(3);
	}

	@Test
	void criarAgendaComUmaVagaQueJaExisteDa409() {
		criarAgenda(LocalTime.of(9, 0));
		gravarELimpar();

		assertThatThrownBy(() -> criarAgenda(LocalTime.of(8, 0), LocalTime.of(9, 0)))
				.isInstanceOf(RegraNegocioException.class).extracting(e -> ((ErroNegocioException) e).getCodigo())
				.isEqualTo("VAGA_DUPLICADA");
		gravarELimpar();
		assertThat(vagas.count()).isEqualTo(1);
	}

	// Marcações

	@Test
	void marcarOcupaAVagaEGravaAMarcacao() {
		Long vagaId = primeiraVaga();

		MarcacaoDTO marcacao = servico.marcar(1001L, vagaId, OrigemMarcacao.AUTOMATICA, "gestao-acesso");
		gravarELimpar();

		assertThat(marcacao.episodioId()).isEqualTo(1001L);
		assertThat(marcacao.vagaId()).isEqualTo(vagaId);
		assertThat(marcacao.dataHora()).isEqualTo(LocalDateTime.of(2026, 12, 10, 9, 0));
		assertThat(marcacao.especialidade()).isEqualTo(CARDIOLOGIA);
		assertThat(marcacao.estado()).isEqualTo(EstadoMarcacao.AGENDADA);
		assertThat(vagas.findById(vagaId).orElseThrow().getEstado()).isEqualTo(EstadoVaga.OCUPADA);
		Marcacao gravada = marcacoes.findById(marcacao.id()).orElseThrow();
		assertThat(gravada.getOrigem()).isEqualTo(OrigemMarcacao.AUTOMATICA);
		assertThat(gravada.getAutor()).isEqualTo("gestao-acesso");
	}

	@Test
	void marcarUmaVagaQueNaoExisteDa404() {
		assertThatThrownBy(() -> servico.marcar(1001L, 999L, OrigemMarcacao.MANUAL, "5"))
				.isInstanceOf(NaoEncontradoException.class);
	}

	@Test
	void marcarUmaVagaOcupadaDa409() {
		Long vagaId = primeiraVaga();
		servico.marcar(1001L, vagaId, OrigemMarcacao.MANUAL, "5");
		gravarELimpar();

		assertThatThrownBy(() -> servico.marcar(2002L, vagaId, OrigemMarcacao.MANUAL, "5"))
				.isInstanceOf(RegraNegocioException.class).extracting(e -> ((ErroNegocioException) e).getCodigo())
				.isEqualTo("VAGA_OCUPADA");
	}

	@Test
	void marcarUmEpisodioJaMarcadoDa409EDeixaAVagaLivre() {
		AgendaDTO agenda = criarAgenda(LocalTime.of(9, 0), LocalTime.of(10, 0));
		servico.marcar(1001L, agenda.vagas().get(0).id(), OrigemMarcacao.MANUAL, "5");
		gravarELimpar();

		Long outraVaga = agenda.vagas().get(1).id();
		assertThatThrownBy(() -> servico.marcar(1001L, outraVaga, OrigemMarcacao.MANUAL, "5"))
				.isInstanceOf(RegraNegocioException.class).extracting(e -> ((ErroNegocioException) e).getCodigo())
				.isEqualTo("EPISODIO_JA_MARCADO");
		gravarELimpar();
		assertThat(vagas.findById(outraVaga).orElseThrow().getEstado()).isEqualTo(EstadoVaga.LIVRE);
	}

	// Realização

	@ParameterizedTest
	@EnumSource(value = EstadoMarcacao.class, names = {"REALIZADA", "FALTA"})
	void registarRealizacaoMudaOEstado(EstadoMarcacao resultado) {
		Long marcacaoId = servico.marcar(1001L, primeiraVaga(), OrigemMarcacao.MANUAL, "5").id();
		gravarELimpar();

		assertThat(servico.registarRealizacao(marcacaoId, resultado).estado()).isEqualTo(resultado);
		gravarELimpar();
		assertThat(marcacoes.findById(marcacaoId).orElseThrow().getEstado()).isEqualTo(resultado);
	}

	@Test
	void registarRealizacaoDeUmaMarcacaoQueNaoExisteDa404() {
		assertThatThrownBy(() -> servico.registarRealizacao(999L, EstadoMarcacao.REALIZADA))
				.isInstanceOf(NaoEncontradoException.class);
	}

	@Test
	void registarRealizacaoDeUmaMarcacaoJaFechadaDa409() {
		Long marcacaoId = servico.marcar(1001L, primeiraVaga(), OrigemMarcacao.MANUAL, "5").id();
		servico.registarRealizacao(marcacaoId, EstadoMarcacao.FALTA);
		gravarELimpar();

		assertThatThrownBy(() -> servico.registarRealizacao(marcacaoId, EstadoMarcacao.REALIZADA))
				.isInstanceOf(TransicaoInvalidaException.class);
	}

	// Leitura

	@Test
	void obterMarcacaoDevolveAMarcacao() {
		Long vagaId = primeiraVaga();
		Long marcacaoId = servico.marcar(1001L, vagaId, OrigemMarcacao.MANUAL, "5").id();
		gravarELimpar();

		MarcacaoDTO lida = servico.obterMarcacao(marcacaoId);
		assertThat(lida.vagaId()).isEqualTo(vagaId);
		assertThat(lida.origem()).isEqualTo(OrigemMarcacao.MANUAL);
	}

	@Test
	void obterMarcacaoQueNaoExisteDa404() {
		assertThatThrownBy(() -> servico.obterMarcacao(999L)).isInstanceOf(NaoEncontradoException.class);
	}
}
