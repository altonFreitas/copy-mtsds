package pt.estg.mtsds.acesso.gestaoacesso.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pt.estg.mtsds.acesso.gestaoacesso.exceptions.DadosInvalidosException;
import pt.estg.mtsds.acesso.gestaoacesso.services.RelogioService;
import pt.estg.mtsds.acesso.gestaoacesso.services.ResultadoVerificacao;
import pt.estg.mtsds.acesso.gestaoacesso.services.VerificacaoPrazosService;

/**
 * Testes do relógio de demonstração com MockMvc e o perfil demo. Os serviços
 * são simulados: a lógica do relógio e da verificação tem testes próprios.
 */
@WebMvcTest(DemoController.class)
@ActiveProfiles("demo")
class DemoControllerTest {

	private static final LocalDate NOVA_DATA = LocalDate.of(2027, 2, 15);

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RelogioService relogioService;

	@MockitoBean
	private VerificacaoPrazosService verificacaoPrazosService;

	@Test
	void putComDataValidaAvancaEDevolveAsListas() throws Exception {
		when(verificacaoPrazosService.verificar())
				.thenReturn(new ResultadoVerificacao(List.of(11L, 12L), List.of(21L)));
		when(relogioService.hoje()).thenReturn(NOVA_DATA);

		mockMvc.perform(
				put("/demo/relogio").contentType(MediaType.APPLICATION_JSON).content("{\"data\": \"2027-02-15\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.dataAtual").value("2027-02-15"))
				.andExpect(jsonPath("$.passaramEmRisco[0]").value(11))
				.andExpect(jsonPath("$.passaramEmRisco[1]").value(12))
				.andExpect(jsonPath("$.passaramIncumpridos[0]").value(21));
		verify(relogioService).avancarPara(NOVA_DATA);
	}

	@Test
	void putSemDataDa400() throws Exception {
		mockMvc.perform(put("/demo/relogio").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").value("DADOS_INVALIDOS"))
				.andExpect(jsonPath("$.erros[0].campo").value("data"));
		verify(relogioService, never()).avancarPara(any());
		verify(verificacaoPrazosService, never()).verificar();
	}

	@Test
	void putComDataAnteriorDa400() throws Exception {
		when(relogioService.avancarPara(LocalDate.of(2020, 1, 1)))
				.thenThrow(new DadosInvalidosException("DATA_RELOGIO_ANTERIOR", "O relógio só anda para a frente."));

		mockMvc.perform(
				put("/demo/relogio").contentType(MediaType.APPLICATION_JSON).content("{\"data\": \"2020-01-01\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").value("DATA_RELOGIO_ANTERIOR"))
				.andExpect(jsonPath("$.servico").value("gestao-acesso"));
		verify(verificacaoPrazosService, never()).verificar();
	}

	@Test
	void deleteRepoeEDevolveADataReal() throws Exception {
		when(relogioService.hoje()).thenReturn(LocalDate.of(2026, 11, 19));

		mockMvc.perform(delete("/demo/relogio")).andExpect(status().isOk())
				.andExpect(jsonPath("$.dataAtual").value("2026-11-19"));
		verify(relogioService).repor();
	}
}
