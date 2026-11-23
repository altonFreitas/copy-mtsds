package pt.estg.mtsds.acesso.indicadores.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testes dos endpoints de indicadores: códigos HTTP e formato JSON, incluindo o
 * formato comum de erro do molde.
 */
@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false"})
@AutoConfigureMockMvc
class IndicadoresControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listaDeEspecialidadeSemDadosDevolveZeros() throws Exception {
		mockMvc.perform(get("/indicadores/lista?esp=SEM_DADOS").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk()).andExpect(jsonPath("$.especialidade").value("SEM_DADOS"))
				.andExpect(jsonPath("$.tamanhoLista").value(0));
	}

	@Test
	void listaSemEspecialidadeDa400() throws Exception {
		mockMvc.perform(get("/indicadores/lista").accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.servico").value("indicadores")).andExpect(jsonPath("$.estado").value(400))
				.andExpect(jsonPath("$.erro").value("PARAMETRO_INVALIDO")).andExpect(jsonPath("$.traceId").exists());
	}

	@Test
	void semanasNaoNumericoDa400ComFormatoComum() throws Exception {
		mockMvc.perform(get("/indicadores/projecao?esp=X&semanas=abc").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").value("IDENTIFICADOR_INVALIDO"));
	}

	@Test
	void periodoInvalidoDa400() throws Exception {
		mockMvc.perform(get("/indicadores/cumprimento?periodo=abc").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").value("PARAMETRO_INVALIDO"));
	}

	@Test
	void urlInexistenteDa404EnaoDa500() throws Exception {
		mockMvc.perform(get("/indicadores/nao-existe").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}
}
