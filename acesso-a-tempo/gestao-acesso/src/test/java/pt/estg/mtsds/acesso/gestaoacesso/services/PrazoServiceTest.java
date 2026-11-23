package pt.estg.mtsds.acesso.gestaoacesso.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pt.estg.mtsds.acesso.gestaoacesso.data.EpisodioPrazoRepository;
import pt.estg.mtsds.acesso.gestaoacesso.dtos.PrazoDTO;
import pt.estg.mtsds.acesso.gestaoacesso.exceptions.NaoEncontradoException;
import pt.estg.mtsds.acesso.gestaoacesso.models.EpisodioPrazo;
import pt.estg.mtsds.acesso.gestaoacesso.models.Prioridade;
import pt.estg.mtsds.acesso.gestaoacesso.models.SituacaoPrazo;

/**
 * Testes do serviço de prazos, com o repositório e o relógio simulados.
 */
@ExtendWith(MockitoExtension.class)
class PrazoServiceTest {

	// Pedido de 2026-01-01 com prazo de 30 dias: alarme a 23/01, limite a 31/01
	private static final LocalDate PEDIDO = LocalDate.of(2026, 1, 1);

	@Mock
	private EpisodioPrazoRepository repositorio;

	@Mock
	private RelogioService relogioService;

	@InjectMocks
	private PrazoService servico;

	private static EpisodioPrazo episodio() {
		return EpisodioPrazo.builder().pedidoId(1001L).especialidade("Cardiologia").dataPedido(PEDIDO)
				.prioridade(Prioridade.MUITO_PRIORITARIA).regraVersao(1).dataLimite(LocalDate.of(2026, 1, 31))
				.dataAlarme(LocalDate.of(2026, 1, 23)).vagaId(70L).dataConsulta(LocalDateTime.of(2026, 1, 20, 9, 0))
				.build();
	}

	// O campo guardado fica DENTRO_PRAZO: a situação tem de vir do cálculo
	@ParameterizedTest(name = "hoje {0}: {1} dias, {2}%, {3}")
	@CsvSource({"2026-01-11, 10, 33, DENTRO_PRAZO", "2026-01-23, 22, 73, EM_RISCO", "2026-01-31, 30, 100, EM_RISCO",
			"2026-02-05, 35, 116, INCUMPRIDO"})
	void calculaDiasPercentagemESituacaoParaHoje(LocalDate hoje, long dias, long percentagem, SituacaoPrazo situacao) {
		when(repositorio.findById(1001L)).thenReturn(Optional.of(episodio()));
		when(relogioService.hoje()).thenReturn(hoje);

		PrazoDTO prazo = servico.obter(1001L);

		assertThat(prazo.diasPrazo()).isEqualTo(30);
		assertThat(prazo.diasConsumidos()).isEqualTo(dias);
		assertThat(prazo.percentagemConsumida()).isEqualTo(percentagem);
		assertThat(prazo.situacao()).isEqualTo(situacao);
	}

	@Test
	void copiaOsDadosDoEpisodio() {
		when(repositorio.findById(1001L)).thenReturn(Optional.of(episodio()));
		when(relogioService.hoje()).thenReturn(LocalDate.of(2026, 1, 11));

		PrazoDTO prazo = servico.obter(1001L);

		assertThat(prazo.episodioId()).isEqualTo(1001L);
		assertThat(prazo.especialidade()).isEqualTo("Cardiologia");
		assertThat(prazo.prioridade()).isEqualTo(Prioridade.MUITO_PRIORITARIA);
		assertThat(prazo.dataPedido()).isEqualTo(PEDIDO);
		assertThat(prazo.dataLimite()).isEqualTo(LocalDate.of(2026, 1, 31));
		assertThat(prazo.dataAlarme()).isEqualTo(LocalDate.of(2026, 1, 23));
		assertThat(prazo.versaoRegra()).isEqualTo(1);
		assertThat(prazo.vagaId()).isEqualTo(70L);
		assertThat(prazo.dataConsulta()).isEqualTo(LocalDateTime.of(2026, 1, 20, 9, 0));
	}

	@Test
	void episodioEncerradoContaAteAoDiaDoFechoEnaoAteHoje() {
		EpisodioPrazo encerrado = episodio();
		encerrado.setEncerradoEm(LocalDateTime.of(2026, 1, 16, 10, 0));
		when(repositorio.findById(1001L)).thenReturn(Optional.of(encerrado));

		PrazoDTO prazo = servico.obter(1001L);

		// Fechado ao dia 15 do prazo, antes do alarme; o relógio nem é lido
		assertThat(prazo.diasConsumidos()).isEqualTo(15);
		assertThat(prazo.percentagemConsumida()).isEqualTo(50);
		assertThat(prazo.situacao()).isEqualTo(SituacaoPrazo.DENTRO_PRAZO);
		verify(relogioService, never()).hoje();
	}

	@Test
	void incumprimentoGravadoMantemSeComORelogioReposto() {
		EpisodioPrazo incumprido = episodio();
		incumprido.setSituacao(SituacaoPrazo.INCUMPRIDO);
		when(repositorio.findById(1001L)).thenReturn(Optional.of(incumprido));
		// Data real antes do limite: o cálculo sozinho daria DENTRO_PRAZO
		when(relogioService.hoje()).thenReturn(LocalDate.of(2026, 1, 11));

		assertThat(servico.obter(1001L).situacao()).isEqualTo(SituacaoPrazo.INCUMPRIDO);
	}

	@Test
	void episodioQueNaoExisteDa404() {
		when(repositorio.findById(9L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.obter(9L)).isInstanceOf(NaoEncontradoException.class)
				.hasMessageContaining("9");
	}
}
