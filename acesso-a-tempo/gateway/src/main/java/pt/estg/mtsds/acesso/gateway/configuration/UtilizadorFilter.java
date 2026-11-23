package pt.estg.mtsds.acesso.gateway.configuration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import pt.estg.mtsds.acesso.gateway.dtos.ErroDTO;
import pt.estg.mtsds.acesso.gateway.models.Papel;
import reactor.core.publisher.Mono;

/**
 * Valida o cabeçalho X-Utilizador (formato {@code <id>;<PAPEL>}) e decide se o
 * papel pode fazer a operação pedida.
 * <ul>
 * <li>Caminhos livres (landing e actuator) passam sem cabeçalho.</li>
 * <li>Cabeçalho em falta, mal formado ou com papel desconhecido: 401.</li>
 * <li>GET: aberto a todos os papéis.</li>
 * <li>Outros métodos: só passam se a operação estiver em
 * acesso.autorizacao.permissoes e o papel for um dos indicados; caso contrário,
 * 403.</li>
 * </ul>
 * O cabeçalho segue para o serviço sem alterações, para o serviço guardar o id
 * como autor. Corre depois do {@link TraceIdFilter}, por isso as respostas de
 * erro já levam o traceId.
 */
@Component
public class UtilizadorFilter implements GlobalFilter, Ordered {

	public static final String CABECALHO = "X-Utilizador";

	private static final Logger log = LoggerFactory.getLogger(UtilizadorFilter.class);

	private static final String EXEMPLO = "X-Utilizador: 123;MEDICO_FAMILIA";

	private final List<PathPattern> caminhosLivres;
	private final List<Regra> regras;
	private final ObjectMapper objectMapper;
	private final String nomeServico;

	/** Permissão da configuração com o caminho já convertido em padrão. */
	private record Regra(HttpMethod metodo, PathPattern caminho, Set<Papel> papeis) {
	}

	/**
	 * Converte os caminhos da configuração em padrões uma única vez, no arranque.
	 * Um caminho inválido impede a gateway de arrancar.
	 *
	 * @param propriedades
	 *            regras de autorização do application.yml
	 * @param objectMapper
	 *            usado para escrever as respostas de erro em JSON
	 * @param nomeServico
	 *            nome da aplicação, usado no campo servico do erro
	 */
	public UtilizadorFilter(AutorizacaoProperties propriedades, ObjectMapper objectMapper,
			@Value("${spring.application.name}") String nomeServico) {
		PathPatternParser parser = PathPatternParser.defaultInstance;
		this.caminhosLivres = propriedades.caminhosLivres().stream().map(parser::parse).toList();
		this.regras = propriedades.permissoes().stream()
				.map(p -> new Regra(p.metodo(), parser.parse(p.caminho()), p.papeis())).toList();
		this.objectMapper = objectMapper;
		this.nomeServico = nomeServico;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		ServerHttpRequest pedido = exchange.getRequest();
		PathContainer caminho = pedido.getPath().pathWithinApplication();
		if (caminhosLivres.stream().anyMatch(p -> p.matches(caminho))) {
			return chain.filter(exchange);
		}

		String valor = pedido.getHeaders().getFirst(CABECALHO);
		if (valor == null || valor.isBlank()) {
			return recusar(exchange, HttpStatus.UNAUTHORIZED, "X_UTILIZADOR_EM_FALTA",
					"O pedido não traz o cabeçalho " + CABECALHO
							+ ". Envie o utilizador e o papel no formato <id>;<PAPEL>, por exemplo " + EXEMPLO + ".");
		}

		// split com -1 mantém as partes vazias, para "123;" e ";TRIADOR" serem
		// recusados
		String[] partes = valor.split(";", -1);
		if (partes.length != 2 || partes[0].isBlank() || partes[1].isBlank()) {
			return recusar(exchange, HttpStatus.UNAUTHORIZED, "X_UTILIZADOR_MAL_FORMADO",
					"O cabeçalho " + CABECALHO + " tem o valor '" + valor
							+ "', que não segue o formato <id>;<PAPEL>. Envie, por exemplo, " + EXEMPLO + ".");
		}

		String nomePapel = partes[1].trim();
		Optional<Papel> papel = Papel.de(nomePapel);
		if (papel.isEmpty()) {
			return recusar(exchange, HttpStatus.UNAUTHORIZED, "PAPEL_DESCONHECIDO", "O papel '" + nomePapel
					+ "' não existe. Use um dos papéis válidos, em maiúsculas: " + Papel.listaValidos() + ".");
		}

		HttpMethod metodo = pedido.getMethod();
		if (HttpMethod.GET.equals(metodo)) {
			return chain.filter(exchange);
		}

		String operacao = metodo.name() + " " + caminho.value();
		Optional<Regra> regra = regras.stream().filter(r -> r.metodo().equals(metodo) && r.caminho().matches(caminho))
				.findFirst();
		if (regra.isEmpty()) {
			return recusar(exchange, HttpStatus.FORBIDDEN, "OPERACAO_NAO_PERMITIDA", "A operação " + operacao
					+ " não é permitida a nenhum papel. Confirme o método e o caminho na documentação da API.");
		}
		if (!regra.get().papeis().contains(papel.get())) {
			String autorizados = regra.get().papeis().stream().map(Enum::name).collect(Collectors.joining(", "));
			return recusar(exchange, HttpStatus.FORBIDDEN, "PAPEL_SEM_PERMISSAO", "O papel " + papel.get().name()
					+ " não pode fazer " + operacao + ". Esta operação só é permitida a: " + autorizados + ".");
		}
		return chain.filter(exchange);
	}

	/** Corre logo a seguir ao {@link TraceIdFilter}. */
	@Override
	public int getOrder() {
		return TraceIdFilter.ORDEM + 1;
	}

	/** Termina o pedido com um erro no formato comum, sem o enviar ao serviço. */
	private Mono<Void> recusar(ServerWebExchange exchange, HttpStatus estado, String codigo, String mensagem) {
		ServerHttpRequest pedido = exchange.getRequest();
		String caminho = pedido.getPath().value();
		String traceId = pedido.getHeaders().getFirst(TraceIdFilter.CABECALHO);
		log.warn("{} em {} {} [trace={}]", codigo, pedido.getMethod().name(), caminho, traceId);

		ErroDTO erro = new ErroDTO(nomeServico, estado.value(), codigo, mensagem, caminho, LocalDateTime.now(),
				traceId);
		byte[] corpo;
		try {
			corpo = objectMapper.writeValueAsBytes(erro);
		} catch (JsonProcessingException e) {
			return Mono.error(e);
		}
		ServerHttpResponse resposta = exchange.getResponse();
		resposta.setStatusCode(estado);
		resposta.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		return resposta.writeWith(Mono.just(resposta.bufferFactory().wrap(corpo)));
	}
}
