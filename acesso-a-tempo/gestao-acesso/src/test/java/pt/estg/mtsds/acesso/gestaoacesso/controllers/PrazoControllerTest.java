package pt.estg.mtsds.acesso.gestaoacesso.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pt.estg.mtsds.acesso.gestaoacesso.dtos.PrazoDTO;
import pt.estg.mtsds.acesso.gestaoacesso.exceptions.NaoEncontradoException;
import pt.estg.mtsds.acesso.gestaoacesso.models.Prioridade;
import pt.estg.mtsds.acesso.gestaoacesso.models.SituacaoPrazo;
import pt.estg.mtsds.acesso.gestaoacesso.services.PrazoService;

/**
 * Testes do endpoint dos prazos com MockMvc. O serviço é simulado: os cálculos
 * têm testes próprios.
 */
@WebMvcTest(PrazoController.class)
class PrazoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PrazoService servico;

	@Test
	void devolve200ComOPrazo() throws Exception {
		when(servico.obter(1001L)).thenReturn(new PrazoDTO(1001L, "Cardiologia", Prioridade.MUITO_PRIORITARIA,
				LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 23), 1, 22, 30, 73,
				SituacaoPrazo.EM_RISCO, null, null));

		mockMvc.perform(get("/prazos/1001")).andExpect(status().isOk()).andExpect(jsonPath("$.episodioId").value(1001))
				.andExpect(jsonPath("$.dataLimite").value("2026-01-31"))
				.andExpect(jsonPath("$.diasConsumidos").value(22)).andExpect(jsonPath("$.diasPrazo").value(30))
				.andExpect(jsonPath("$.percentagemConsumida").value(73))
				.andExpect(jsonPath("$.situacao").value("EM_RISCO"));
	}

	@Test
	void episodioQueNaoExisteDa404() throws Exception {
		when(servico.obter(9L))
				.thenThrow(new NaoEncontradoException("PRAZO_NAO_ENCONTRADO", "Não existe prazo para o episódio 9."));

		mockMvc.perform(get("/prazos/9")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.erro").value("PRAZO_NAO_ENCONTRADO"))
				.andExpect(jsonPath("$.servico").value("gestao-acesso"));
	}
}
