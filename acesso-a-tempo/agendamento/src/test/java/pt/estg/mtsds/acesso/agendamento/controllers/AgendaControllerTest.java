package pt.estg.mtsds.acesso.agendamento.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import pt.estg.mtsds.acesso.agendamento.dtos.AgendaDTO;
import pt.estg.mtsds.acesso.agendamento.dtos.CriarAgendaDTO;
import pt.estg.mtsds.acesso.agendamento.dtos.VagaDTO;
import pt.estg.mtsds.acesso.agendamento.exceptions.RegraNegocioException;
import pt.estg.mtsds.acesso.agendamento.models.EstadoVaga;
import pt.estg.mtsds.acesso.agendamento.services.AgendamentoService;

/**
 * Testes do endpoint das agendas com MockMvc. O serviço é simulado: as regras
 * têm testes próprios.
 */
@WebMvcTest(AgendaController.class)
class AgendaControllerTest {

	private static final String CORPO = """
			{"especialidade": "Cardiologia", "data": "2026-12-10", "horas": ["09:00", "09:30"]}""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AgendamentoService servico;

	private static MockHttpServletRequestBuilder pedido(String corpo) {
		return post("/agendas").header("X-Utilizador", "8;GESTOR_ACESSO").contentType(MediaType.APPLICATION_JSON)
				.content(corpo);
	}

	@Test
	void criarAgendaDa201ComAsVagasEUsaOIdDoCabecalhoComoAutor() throws Exception {
		LocalDate dia = LocalDate.of(2026, 12, 10);
		when(servico.criarAgenda(any(), eq("8"))).thenReturn(
				new AgendaDTO(7L, "Cardiologia", dia, List.of(new VagaDTO(70L, dia.atTime(9, 0), EstadoVaga.LIVRE),
						new VagaDTO(71L, LocalDateTime.of(2026, 12, 10, 9, 30), EstadoVaga.LIVRE))));

		mockMvc.perform(pedido(CORPO)).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(7))
				.andExpect(jsonPath("$.vagas[0].id").value(70))
				.andExpect(jsonPath("$.vagas[1].dataHora").value("2026-12-10T09:30:00"))
				.andExpect(jsonPath("$.vagas[1].estado").value("LIVRE"));
		verify(servico).criarAgenda(
				new CriarAgendaDTO("Cardiologia", dia, List.of(LocalTime.of(9, 0), LocalTime.of(9, 30))), "8");
	}

	@ParameterizedTest
	@ValueSource(strings = {"{\"especialidade\": \"Cardiologia\", \"data\": \"2026-12-10\", \"horas\": []}",
			"{\"especialidade\": \"\", \"data\": \"2026-12-10\", \"horas\": [\"09:00\"]}",
			"{\"especialidade\": \"Cardiologia\", \"horas\": [\"09:00\"]}",
			"{\"especialidade\": \"Cardiologia\", \"data\": \"2026-12-10\", \"horas\": [\"09:00\", \"09:00\"]}"})
	void pedidoInvalidoDa400(String corpo) throws Exception {
		mockMvc.perform(pedido(corpo)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("DADOS_INVALIDOS")).andExpect(jsonPath("$.erros").isNotEmpty());
		verify(servico, never()).criarAgenda(any(), any());
	}

	@Test
	void semCabecalhoXUtilizadorDa400() throws Exception {
		mockMvc.perform(post("/agendas").contentType(MediaType.APPLICATION_JSON).content(CORPO))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").value("CABECALHO_EM_FALTA"));
	}

	@Test
	void vagaDuplicadaDa409() throws Exception {
		when(servico.criarAgenda(any(), any()))
				.thenThrow(new RegraNegocioException("VAGA_DUPLICADA", "Já existem vagas de Cardiologia."));

		mockMvc.perform(pedido(CORPO)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.erro").value("VAGA_DUPLICADA"))
				.andExpect(jsonPath("$.servico").value("agendamento"));
	}
}
