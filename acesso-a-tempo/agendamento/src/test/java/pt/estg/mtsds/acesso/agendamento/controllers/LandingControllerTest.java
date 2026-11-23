package pt.estg.mtsds.acesso.agendamento.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testes com MockMvc (slide 02): o landing responde e os erros seguem o formato
 * comum.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class LandingControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void landingResponde() throws Exception {
		mockMvc.perform(get("/agendamento/landing")).andExpect(status().isOk())
				.andExpect(content().string(containsString("agendamento"))).andExpect(header().exists("X-Trace-Id"));
	}

	@Test
	void rotaInexistenteDevolveErroNoFormatoComum() throws Exception {
		mockMvc.perform(get("/nao-existe").header("X-Trace-Id", "teste123")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.servico").value("agendamento")).andExpect(jsonPath("$.estado").value(404))
				.andExpect(jsonPath("$.erro").value("ROTA_NAO_ENCONTRADA"))
				.andExpect(jsonPath("$.traceId").value("teste123"));
	}
}
