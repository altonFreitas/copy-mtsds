package pt.estg.mtsds.acesso.agendamento.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import pt.estg.mtsds.acesso.agendamento.dtos.MarcacaoDTO;
import pt.estg.mtsds.acesso.agendamento.exceptions.NaoEncontradoException;
import pt.estg.mtsds.acesso.agendamento.exceptions.TransicaoInvalidaException;
import pt.estg.mtsds.acesso.agendamento.models.EstadoMarcacao;
import pt.estg.mtsds.acesso.agendamento.models.OrigemMarcacao;
import pt.estg.mtsds.acesso.agendamento.services.AgendamentoService;

/**
 * Testes dos endpoints das marcações com MockMvc. O serviço é simulado: as
 * regras têm testes próprios.
 */
@WebMvcTest(MarcacaoController.class)
class MarcacaoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AgendamentoService servico;

	private static MarcacaoDTO marcacao(EstadoMarcacao estado) {
		return new MarcacaoDTO(5L, 1001L, 70L, LocalDateTime.of(2026, 12, 10, 9, 0), "Cardiologia", estado,
				OrigemMarcacao.AUTOMATICA);
	}

	private static MockHttpServletRequestBuilder realizacao(String resultado) {
		return put("/marcacoes/5/realizacao").header("X-Utilizador", "5;ADMINISTRATIVO")
				.contentType(MediaType.APPLICATION_JSON).content("{\"resultado\": \"" + resultado + "\"}");
	}

	@Test
	void obterMarcacaoDa200() throws Exception {
		when(servico.obterMarcacao(5L)).thenReturn(marcacao(EstadoMarcacao.AGENDADA));

		mockMvc.perform(get("/marcacoes/5")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(5))
				.andExpect(jsonPath("$.episodioId").value(1001)).andExpect(jsonPath("$.vagaId").value(70))
				.andExpect(jsonPath("$.dataHora").value("2026-12-10T09:00:00"))
				.andExpect(jsonPath("$.especialidade").value("Cardiologia"))
				.andExpect(jsonPath("$.estado").value("AGENDADA")).andExpect(jsonPath("$.origem").value("AUTOMATICA"));
	}

	@Test
	void obterMarcacaoQueNaoExisteDa404() throws Exception {
		when(servico.obterMarcacao(9L))
				.thenThrow(new NaoEncontradoException("MARCACAO_NAO_ENCONTRADA", "Não existe a marcação 9."));

		mockMvc.perform(get("/marcacoes/9")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.erro").value("MARCACAO_NAO_ENCONTRADA"));
	}

	@Test
	void registarRealizacaoDa200() throws Exception {
		when(servico.registarRealizacao(5L, EstadoMarcacao.REALIZADA)).thenReturn(marcacao(EstadoMarcacao.REALIZADA));

		mockMvc.perform(realizacao("REALIZADA")).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("REALIZADA"));
	}

	@Test
	void resultadoQueNaoERealizadaNemFaltaDa400() throws Exception {
		mockMvc.perform(realizacao("CANCELADA")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("DADOS_INVALIDOS"));
		verify(servico, never()).registarRealizacao(anyLong(), any());
	}

	@Test
	void registarRealizacaoDeMarcacaoQueNaoExisteDa404() throws Exception {
		when(servico.registarRealizacao(5L, EstadoMarcacao.FALTA))
				.thenThrow(new NaoEncontradoException("MARCACAO_NAO_ENCONTRADA", "Não existe a marcação 5."));

		mockMvc.perform(realizacao("FALTA")).andExpect(status().isNotFound());
	}

	@Test
	void registarRealizacaoDeMarcacaoJaFechadaDa409() throws Exception {
		when(servico.registarRealizacao(5L, EstadoMarcacao.FALTA))
				.thenThrow(new TransicaoInvalidaException("A marcação 5 já está REALIZADA."));

		mockMvc.perform(realizacao("FALTA")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.erro").value("TRANSICAO_INVALIDA"));
	}
}
