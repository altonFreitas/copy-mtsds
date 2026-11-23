package pt.estg.mtsds.acesso.gestaoacesso.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pt.estg.mtsds.acesso.gestaoacesso.data.RelogioRepository;
import pt.estg.mtsds.acesso.gestaoacesso.exceptions.DadosInvalidosException;
import pt.estg.mtsds.acesso.gestaoacesso.models.Relogio;

/**
 * Testes do serviço do relógio, com o repositório simulado e um relógio fixo.
 */
@ExtendWith(MockitoExtension.class)
class RelogioServiceTest {

	private static final LocalDate DATA_REAL = LocalDate.of(2026, 11, 19);
	private static final LocalDate DATA_GUARDADA = LocalDate.of(2026, 12, 1);

	@Mock
	private RelogioRepository repositorio;

	private RelogioService servico;

	@BeforeEach
	void criarServico() {
		Clock fixo = Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneId.of("Europe/Lisbon"));
		servico = new RelogioService(repositorio, fixo);
	}

	private void comLinha(LocalDate data) {
		when(repositorio.findById(Relogio.ID)).thenReturn(Optional.of(Relogio.builder().dataAtual(data).build()));
	}

	@Test
	void semLinhaHojeEADataDoClock() {
		when(repositorio.findById(Relogio.ID)).thenReturn(Optional.empty());

		assertThat(servico.hoje()).isEqualTo(DATA_REAL);
	}

	@Test
	void comLinhaHojeEADataGuardada() {
		comLinha(DATA_GUARDADA);

		assertThat(servico.hoje()).isEqualTo(DATA_GUARDADA);
	}

	@Test
	void avancarParaGravaANovaData() {
		when(repositorio.findById(Relogio.ID)).thenReturn(Optional.empty());

		servico.avancarPara(DATA_GUARDADA);

		ArgumentCaptor<Relogio> gravado = ArgumentCaptor.forClass(Relogio.class);
		verify(repositorio).save(gravado.capture());
		assertThat(gravado.getValue().getId()).isEqualTo(Relogio.ID);
		assertThat(gravado.getValue().getDataAtual()).isEqualTo(DATA_GUARDADA);
	}

	@Test
	void avancarParaUmaDataAnteriorAHojeERecusado() {
		comLinha(DATA_GUARDADA);

		assertThatThrownBy(() -> servico.avancarPara(DATA_GUARDADA.minusDays(1)))
				.isInstanceOf(DadosInvalidosException.class).hasMessageContaining("só anda para a frente");
		verify(repositorio, never()).save(any());
	}

	@Test
	void reporVoltaADataDoClock() {
		servico.repor();

		verify(repositorio).deleteById(Relogio.ID);
		// Depois de apagada a linha, o repositório já não a encontra
		when(repositorio.findById(Relogio.ID)).thenReturn(Optional.empty());
		assertThat(servico.hoje()).isEqualTo(DATA_REAL);
	}
}
