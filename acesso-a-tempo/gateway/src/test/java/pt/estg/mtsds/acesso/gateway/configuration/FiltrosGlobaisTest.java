package pt.estg.mtsds.acesso.gateway.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;

/**
 * Testes dos filtros globais da gateway (traceId e X-Utilizador) com as rotas
 * reais do application.yml. Um servidor HTTP local faz de todos os serviços:
 * responde 200 e devolve, em cabeçalhos Recebido-*, o X-Utilizador e o
 * X-Trace-Id que lhe chegaram.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class FiltrosGlobaisTest {

	private static final List<String> SERVICOS = List.of("referenciacao", "agendamento", "lista-espera",
			"gestao-acesso", "garantia-acesso", "historico", "utentes", "notificacoes", "servico-modelo");

	private static final String TRACE = "X-Trace-Id";
	private static final String UTILIZADOR = "X-Utilizador";

	private static HttpServer servico;

	@Autowired
	private WebTestClient cliente;

	/**
	 * Arranca o serviço falso e põe o seu endereço em acesso.servicos, no lugar do
	 * endereço de cada serviço usado nas rotas.
	 */
	@DynamicPropertySource
	static void servicoFalso(DynamicPropertyRegistry registo) throws IOException {
		servico = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		servico.createContext("/", troca -> {
			for (String cabecalho : List.of(UTILIZADOR, TRACE)) {
				String valor = troca.getRequestHeaders().getFirst(cabecalho);
				if (valor != null) {
					troca.getResponseHeaders().add("Recebido-" + cabecalho, valor);
				}
			}
			byte[] corpo = "ok".getBytes(StandardCharsets.UTF_8);
			troca.sendResponseHeaders(200, corpo.length);
			try (OutputStream saida = troca.getResponseBody()) {
				saida.write(corpo);
			}
		});
		// Como os serviços reais (TraceIdFilter do servico-modelo), devolve o
		// X-Trace-Id recebido
		servico.createContext("/servico-modelo", troca -> {
			troca.getResponseHeaders().add(TRACE, troca.getRequestHeaders().getFirst(TRACE));
			troca.sendResponseHeaders(200, -1);
			troca.close();
		});
		servico.start();
		String uri = "http://localhost:" + servico.getAddress().getPort();
		for (String nome : SERVICOS) {
			registo.add("acesso.servicos." + nome, () -> uri);
		}
	}

	@AfterAll
	static void pararServicoFalso() {
		servico.stop(0);
	}

	private ResponseSpec pedido(HttpMethod metodo, String caminho, String utilizador) {
		WebTestClient.RequestBodySpec spec = cliente.method(metodo).uri(caminho);
		if (utilizador != null) {
			spec.header(UTILIZADOR, utilizador);
		}
		return spec.exchange();
	}

	/**
	 * Confirma o formato comum do erro e que o traceId do corpo é o da resposta.
	 */
	private void erroNoFormatoComum(ResponseSpec resposta, int estado, String codigo, String caminho) {
		EntityExchangeResult<byte[]> resultado = resposta.expectStatus().isEqualTo(estado).expectHeader()
				.contentType(MediaType.APPLICATION_JSON).expectBody().jsonPath("$.servico").isEqualTo("gateway")
				.jsonPath("$.estado").isEqualTo(estado).jsonPath("$.erro").isEqualTo(codigo).jsonPath("$.mensagem")
				.isNotEmpty().jsonPath("$.caminho").isEqualTo(caminho).jsonPath("$.dataHora").isNotEmpty()
				.returnResult();
		String traceId = resultado.getResponseHeaders().getFirst(TRACE);
		assertThat(traceId).matches("[0-9a-f]{16}");
		String corpo = new String(resultado.getResponseBodyContent(), StandardCharsets.UTF_8);
		assertThat(JsonPath.<String>read(corpo, "$.traceId")).isEqualTo(traceId);
	}

	// 200

	@Test
	void getAbertoATodosOsPapeis() {
		pedido(HttpMethod.GET, "/pedidos", "42;UTENTE").expectStatus().isOk().expectHeader()
				.valueEquals("Recebido-" + UTILIZADOR, "42;UTENTE");
	}

	@Test
	void landingPassaSemCabecalho() {
		pedido(HttpMethod.GET, "/servico-modelo/landing", null).expectStatus().isOk();
	}

	@Test
	void operacaoPermitidaPassaComOCabecalhoInalterado() {
		pedido(HttpMethod.POST, "/pedidos", "7;MEDICO_FAMILIA").expectStatus().isOk().expectHeader()
				.valueEquals("Recebido-" + UTILIZADOR, "7;MEDICO_FAMILIA");
	}

	@Test
	void operacaoComVariavelNoCaminhoPassa() {
		pedido(HttpMethod.PUT, "/pedidos/15/triagem", "3;TRIADOR").expectStatus().isOk();
		pedido(HttpMethod.DELETE, "/marcacoes/9", "5;DIRETOR_SERVICO").expectStatus().isOk();
	}

	@Test
	void marcacaoPeloGestorDoAcessoPassa() {
		pedido(HttpMethod.POST, "/marcacoes", "8;GESTOR_ACESSO").expectStatus().isOk();
	}

	@Test
	void agendaPeloAdministrativoPassa() {
		pedido(HttpMethod.POST, "/agendas", "5;ADMINISTRATIVO").expectStatus().isOk();
	}

	@ParameterizedTest
	@ValueSource(strings = {"/garantia-acesso/landing", "/historico/landing"})
	void landingsDosServicosRenomeadosPassamSemCabecalho(String caminho) {
		pedido(HttpMethod.GET, caminho, null).expectStatus().isOk();
	}

	@Test
	void alertaAoDiretorNumCasoPeloGestorDoAcessoPassa() {
		pedido(HttpMethod.PUT, "/casos/3/alerta-diretor", "8;GESTOR_ACESSO").expectStatus().isOk();
	}

	@Test
	void caminhoAntigoDosIncumprimentosNaoTemRota() {
		pedido(HttpMethod.GET, "/incumprimentos/3", "8;GESTOR_ACESSO").expectStatus().isNotFound();
	}

	@Test
	void landingDaGestaoDoAcessoPassaSemCabecalho() {
		pedido(HttpMethod.GET, "/gestao-acesso/landing", null).expectStatus().isOk();
	}

	// 401

	@Test
	void semCabecalhoDevolve401() {
		erroNoFormatoComum(pedido(HttpMethod.GET, "/pedidos", null), 401, "X_UTILIZADOR_EM_FALTA", "/pedidos");
	}

	@ParameterizedTest
	@ValueSource(strings = {"42", "42;", ";TRIADOR", "42;TRIADOR;extra"})
	void cabecalhoMalFormadoDevolve401(String valor) {
		erroNoFormatoComum(pedido(HttpMethod.GET, "/pedidos", valor), 401, "X_UTILIZADOR_MAL_FORMADO", "/pedidos");
	}

	@Test
	void papelDesconhecidoDevolve401() {
		erroNoFormatoComum(pedido(HttpMethod.GET, "/pedidos", "42;ENFERMEIRO"), 401, "PAPEL_DESCONHECIDO", "/pedidos");
	}

	// 403

	@Test
	void papelSemPermissaoDevolve403() {
		erroNoFormatoComum(pedido(HttpMethod.POST, "/pedidos", "3;TRIADOR"), 403, "PAPEL_SEM_PERMISSAO", "/pedidos");
	}

	@Test
	void marcacaoPeloUtenteDevolve403() {
		erroNoFormatoComum(pedido(HttpMethod.POST, "/marcacoes", "42;UTENTE"), 403, "PAPEL_SEM_PERMISSAO",
				"/marcacoes");
	}

	@Test
	void agendaPeloUtenteDevolve403() {
		erroNoFormatoComum(pedido(HttpMethod.POST, "/agendas", "42;UTENTE"), 403, "PAPEL_SEM_PERMISSAO", "/agendas");
	}

	@Test
	void metodoNaoListadoDevolve403() {
		erroNoFormatoComum(pedido(HttpMethod.PATCH, "/pedidos/1", "7;MEDICO_FAMILIA"), 403, "OPERACAO_NAO_PERMITIDA",
				"/pedidos/1");
	}

	// traceId

	@Test
	void geraTraceIdEPassaOAoServico() {
		var resultado = pedido(HttpMethod.GET, "/pedidos", "42;UTENTE").expectStatus().isOk()
				.returnResult(String.class);
		String traceId = resultado.getResponseHeaders().getFirst(TRACE);
		assertThat(traceId).matches("[0-9a-f]{16}");
		assertThat(resultado.getResponseHeaders().getFirst("Recebido-" + TRACE)).isEqualTo(traceId);
	}

	@Test
	void respostaSoTemUmTraceIdQuandoOServicoTambemODevolve() {
		var resultado = cliente.get().uri("/servico-modelo/landing").exchange().expectStatus().isOk()
				.returnResult(String.class);
		List<String> valores = resultado.getResponseHeaders().get(TRACE);
		assertThat(valores).hasSize(1);
		assertThat(valores.get(0)).matches("[0-9a-f]{16}");
	}

	@Test
	void mantemOTraceIdRecebido() {
		cliente.get().uri("/pedidos").header(UTILIZADOR, "42;UTENTE").header(TRACE, "abc123").exchange().expectStatus()
				.isOk().expectHeader().valueEquals(TRACE, "abc123").expectHeader()
				.valueEquals("Recebido-" + TRACE, "abc123");
	}
}
