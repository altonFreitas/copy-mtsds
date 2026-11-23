package pt.estg.mtsds.acesso.agendamento.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import pt.estg.mtsds.acesso.agendamento.dtos.ErroDTO;
import pt.estg.mtsds.acesso.agendamento.models.Vaga;

/**
 * Testes dos handlers que nenhum endpoint atual provoca, chamados diretamente.
 */
class ExceptionHandlersTest {

	@Test
	void conflitoDeVersaoNaVagaDa409VagaOcupadaNoFormatoComum() {
		ExceptionHandlers handlers = new ExceptionHandlers();
		ReflectionTestUtils.setField(handlers, "nomeServico", "agendamento");
		MockHttpServletRequest pedido = new MockHttpServletRequest("POST", "/marcacoes");

		ResponseEntity<ErroDTO> resposta = handlers
				.vagaOcupadaEmConcorrencia(new ObjectOptimisticLockingFailureException(Vaga.class, 70L), pedido);

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		ErroDTO erro = resposta.getBody();
		assertThat(erro).isNotNull();
		assertThat(erro.getServico()).isEqualTo("agendamento");
		assertThat(erro.getEstado()).isEqualTo(409);
		assertThat(erro.getErro()).isEqualTo("VAGA_OCUPADA");
		assertThat(erro.getMensagem()).isNotBlank();
		assertThat(erro.getCaminho()).isEqualTo("/marcacoes");
		assertThat(erro.getDataHora()).isNotNull();
	}
}
