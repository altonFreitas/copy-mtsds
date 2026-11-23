package pt.estg.mtsds.acesso.gestaoacesso.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import pt.estg.mtsds.acesso.gestaoacesso.models.SituacaoPrazo;

/**
 * Testes da calculadora de prazos, sem Spring e com datas fixas. As datas
 * esperadas estão escritas à mão, para não repetirem o cálculo que se testa.
 */
class CalculadoraPrazosTest {

	private static final LocalDate PEDIDO = LocalDate.of(2026, 1, 1);

	// Prazo de 30 dias a partir de PEDIDO: alarme ao dia 22, limite ao dia 30
	private static final LocalDate ALARME_30 = LocalDate.of(2026, 1, 23);
	private static final LocalDate LIMITE_30 = LocalDate.of(2026, 1, 31);

	// R1, R2 e alerta aos 75%

	@ParameterizedTest(name = "{0} dias: limite {1}, alarme {2}")
	@CsvSource({"30, 2026-01-31, 2026-01-23", "60, 2026-03-02, 2026-02-15", "120, 2026-05-01, 2026-04-01"})
	void calculaDataLimiteEDataDoAlarme(int dias, LocalDate limite, LocalDate alarme) {
		assertThat(CalculadoraPrazos.dataLimite(PEDIDO, dias)).isEqualTo(limite);
		assertThat(CalculadoraPrazos.dataAlarme(PEDIDO, dias)).isEqualTo(alarme);
	}

	// R4: situação nos dias de fronteira

	@Test
	void naVesperaDoAlarmeEstaDentroDoPrazo() {
		assertThat(CalculadoraPrazos.situacaoEm(ALARME_30, LIMITE_30, LocalDate.of(2026, 1, 22)))
				.isEqualTo(SituacaoPrazo.DENTRO_PRAZO);
	}

	@Test
	void noDiaDoAlarmeFicaEmRisco() {
		assertThat(CalculadoraPrazos.situacaoEm(ALARME_30, LIMITE_30, ALARME_30)).isEqualTo(SituacaoPrazo.EM_RISCO);
	}

	@Test
	void noDiaLimiteAindaEstaEmRiscoENaoIncumprido() {
		assertThat(CalculadoraPrazos.situacaoEm(ALARME_30, LIMITE_30, LIMITE_30)).isEqualTo(SituacaoPrazo.EM_RISCO);
	}

	@Test
	void noDiaSeguinteAoLimiteFicaIncumprido() {
		assertThat(CalculadoraPrazos.situacaoEm(ALARME_30, LIMITE_30, LocalDate.of(2026, 2, 1)))
				.isEqualTo(SituacaoPrazo.INCUMPRIDO);
	}

	// R7

	@Test
	void prioridadeQueSobeComONovoLimiteJaPassadoFicaLogoIncumprida() {
		LocalDate hoje = PEDIDO.plusDays(40);

		// Como NORMAL (120 dias), ao dia 40 ainda estava dentro do prazo
		assertThat(CalculadoraPrazos.situacaoEm(CalculadoraPrazos.dataAlarme(PEDIDO, 120),
				CalculadoraPrazos.dataLimite(PEDIDO, 120), hoje)).isEqualTo(SituacaoPrazo.DENTRO_PRAZO);

		// Reavaliado para MUITO_PRIORITARIA (30 dias), conta desde a mesma data do
		// pedido (R2): o limite foi ao dia 30
		LocalDate novoLimite = CalculadoraPrazos.dataLimite(PEDIDO, 30);
		assertThat(novoLimite).isEqualTo(LIMITE_30);
		assertThat(CalculadoraPrazos.situacaoEm(CalculadoraPrazos.dataAlarme(PEDIDO, 30), novoLimite, hoje))
				.isEqualTo(SituacaoPrazo.INCUMPRIDO);
	}

	// R5

	@Test
	void consultaNoDiaLimiteNaoEIncumprimentoPrevisto() {
		assertThat(CalculadoraPrazos.incumprimentoPrevisto(LIMITE_30, LIMITE_30)).isFalse();
	}

	@Test
	void consultaNoDiaSeguinteAoLimiteEIncumprimentoPrevisto() {
		assertThat(CalculadoraPrazos.incumprimentoPrevisto(LIMITE_30, LocalDate.of(2026, 2, 1))).isTrue();
	}

	// Validação

	@ParameterizedTest(name = "dias = {0}")
	@ValueSource(ints = {0, -1, -30})
	void recusaDiasQueNaoSejamPositivos(int dias) {
		assertThatThrownBy(() -> CalculadoraPrazos.dataLimite(PEDIDO, dias))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maior que zero");
		assertThatThrownBy(() -> CalculadoraPrazos.dataAlarme(PEDIDO, dias))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maior que zero");
	}

	@Test
	void recusaDatasNulas() {
		assertThatThrownBy(() -> CalculadoraPrazos.dataLimite(null, 30)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("dataPedido");
		assertThatThrownBy(() -> CalculadoraPrazos.situacaoEm(ALARME_30, LIMITE_30, null))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hoje");
		assertThatThrownBy(() -> CalculadoraPrazos.incumprimentoPrevisto(LIMITE_30, null))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dataConsulta");
	}

	@Test
	void recusaAlarmeDepoisDaDataLimite() {
		assertThatThrownBy(() -> CalculadoraPrazos.situacaoEm(LIMITE_30, ALARME_30, PEDIDO))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("data do alarme");
	}
}
