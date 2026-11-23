package pt.estg.mtsds.acesso.gestaoacesso.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Sem o perfil demo, o relógio de demonstração não existe.
 */
@WebMvcTest(DemoController.class)
class DemoControllerSemPerfilTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void semPerfilDemoORelogioNaoExiste() throws Exception {
		mockMvc.perform(
				put("/demo/relogio").contentType(MediaType.APPLICATION_JSON).content("{\"data\": \"2027-02-15\"}"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.erro").value("ROTA_NAO_ENCONTRADA"));
	}
}
