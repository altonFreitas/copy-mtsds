package pt.estg.mtsds.acesso.gestaoacesso.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pt.estg.mtsds.acesso.gestaoacesso.data.EpisodioPrazoRepository;
import pt.estg.mtsds.acesso.gestaoacesso.models.EpisodioPrazo;
import pt.estg.mtsds.acesso.gestaoacesso.models.Prioridade;
import pt.estg.mtsds.acesso.gestaoacesso.models.SituacaoPrazo;

/**
 * Testes da verificação diária, com o repositório e o relógio simulados. O
 * repositório simulado devolve o que as pesquisas reais devolveriam para cada
 * caso.
 */
@ExtendWith(MockitoExtension.class)
class VerificacaoPrazosServiceTest {

	private static final long PEDIDO_ID = 1001L;

	// Pedido de 2026-01-01 com prazo de 30 dias: alarme a 23/01, limite a 31/01
	private static final LocalDate ALARME = LocalDate.of(2026, 1, 23);
	private static final LocalDate LIMITE = LocalDate.of(2026, 1, 31);

	@Mock
	private EpisodioPrazoRepository repositorio;

	@Mock
	private RelogioService relogioService;

	@InjectMocks
	private VerificacaoPrazosService servico;

	private static EpisodioPrazo episodio(SituacaoPrazo situacao, LocalDateTime alarmeRiscoEm) {
		return EpisodioPrazo.builder().pedidoId(PEDIDO_ID).especialidade("Cardiologia")
				.dataPedido(LocalDate.of(2026, 1, 1)).prioridade(Prioridade.MUITO_PRIORITARIA).regraVersao(1)
				.dataLimite(LIMITE).dataAlarme(ALARME).situacao(situacao).alarmeRiscoEm(alarmeRiscoEm).build();
	}

	private void quandoHojeE(LocalDate hoje, List<EpisodioPrazo> alarmeChegou, List<EpisodioPrazo> limitePassou) {
		when(relogioService.hoje()).thenReturn(hoje);
		when(repositorio.findByEncerradoEmIsNullAndAlarmeRiscoEmIsNullAndDataAlarmeLessThanEqual(hoje))
				.thenReturn(alarmeChegou);
		when(repositorio.findByEncerradoEmIsNullAndSituacaoNotAndDataLimiteBefore(SituacaoPrazo.INCUMPRIDO, hoje))
				.thenReturn(limitePassou);
	}

	@Test
	void noDiaDoAlarmePassaAEmRiscoEGuardaOAlarme() {
		EpisodioPrazo ep = episodio(SituacaoPrazo.DENTRO_PRAZO, null);
		quandoHojeE(ALARME, List.of(ep), List.of());

		ResultadoVerificacao resultado = servico.verificar();

		assertThat(resultado.passaramEmRisco()).containsExactly(PEDIDO_ID);
		assertThat(resultado.passaramIncumpridos()).isEmpty();
		assertThat(ep.getSituacao()).isEqualTo(SituacaoPrazo.EM_RISCO);
		assertThat(ep.getAlarmeRiscoEm()).isEqualTo(ALARME.atStartOfDay());
	}

	@Test
	void alarmeQueJaTocouNaoVoltaAEntrarNosEmRisco() {
		LocalDateTime tocou = ALARME.atStartOfDay();
		EpisodioPrazo ep = episodio(SituacaoPrazo.EM_RISCO, tocou);
		// Com o alarme já gravado e o limite no futuro, nenhuma pesquisa o devolve
		quandoHojeE(ALARME.plusDays(2), List.of(), List.of());

		ResultadoVerificacao resultado = servico.verificar();

		assertThat(resultado.passaramEmRisco()).isEmpty();
		assertThat(ep.getSituacao()).isEqualTo(SituacaoPrazo.EM_RISCO);
		assertThat(ep.getAlarmeRiscoEm()).isEqualTo(tocou);
	}

	@Test
	void depoisDaDataLimitePassaAIncumprido() {
		EpisodioPrazo ep = episodio(SituacaoPrazo.EM_RISCO, ALARME.atStartOfDay());
		quandoHojeE(LIMITE.plusDays(1), List.of(), List.of(ep));

		ResultadoVerificacao resultado = servico.verificar();

		assertThat(resultado.passaramIncumpridos()).containsExactly(PEDIDO_ID);
		assertThat(resultado.passaramEmRisco()).isEmpty();
		assertThat(ep.getSituacao()).isEqualTo(SituacaoPrazo.INCUMPRIDO);
	}

	@Test
	void episodioEncerradoEIgnorado() {
		EpisodioPrazo encerrado = episodio(SituacaoPrazo.DENTRO_PRAZO, null);
		encerrado.setEncerradoEm(LocalDateTime.of(2026, 1, 20, 10, 0));
		// As pesquisas só devolvem episódios abertos, por isso este não aparece
		quandoHojeE(LIMITE.plusDays(1), List.of(), List.of());

		ResultadoVerificacao resultado = servico.verificar();

		assertThat(resultado.passaramEmRisco()).isEmpty();
		assertThat(resultado.passaramIncumpridos()).isEmpty();
		assertThat(encerrado.getSituacao()).isEqualTo(SituacaoPrazo.DENTRO_PRAZO);
		assertThat(encerrado.getAlarmeRiscoEm()).isNull();
	}

	@Test
	void noProprioDiaLimiteContinuaEmRisco() {
		EpisodioPrazo ep = episodio(SituacaoPrazo.EM_RISCO, ALARME.atStartOfDay());
		// A pesquisa dos incumpridos só traz data-limite antes de hoje
		quandoHojeE(LIMITE, List.of(), List.of());

		ResultadoVerificacao resultado = servico.verificar();

		assertThat(resultado.passaramIncumpridos()).isEmpty();
		assertThat(ep.getSituacao()).isEqualTo(SituacaoPrazo.EM_RISCO);
	}

	@Test
	void quemPassaOAlarmeEOLimiteDeUmaVezFicaComAlarmeESoNosIncumpridos() {
		EpisodioPrazo ep = episodio(SituacaoPrazo.DENTRO_PRAZO, null);
		LocalDate hoje = LIMITE.plusDays(1);
		quandoHojeE(hoje, List.of(ep), List.of(ep));

		ResultadoVerificacao resultado = servico.verificar();

		assertThat(resultado.passaramEmRisco()).isEmpty();
		assertThat(resultado.passaramIncumpridos()).containsExactly(PEDIDO_ID);
		assertThat(ep.getSituacao()).isEqualTo(SituacaoPrazo.INCUMPRIDO);
		assertThat(ep.getAlarmeRiscoEm()).isEqualTo(hoje.atStartOfDay());
	}
}
