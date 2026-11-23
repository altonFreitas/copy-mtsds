package pt.estg.mtsds.acesso.gateway.configuration;

import java.util.UUID;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Garante que cada pedido leva um cabeçalho X-Trace-Id. Se o pedido não o
 * trouxer, gera um com 16 caracteres hexadecimais. O valor segue para o serviço
 * e volta na resposta; os serviços copiam-no para os logs e para as respostas
 * de erro. É o primeiro filtro global, para os erros 401 e 403 do
 * {@link UtilizadorFilter} já levarem o traceId.
 */
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

	public static final String CABECALHO = "X-Trace-Id";

	/** Ordem deste filtro; os outros filtros globais ordenam-se a partir dela. */
	public static final int ORDEM = Ordered.HIGHEST_PRECEDENCE;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		String traceId = exchange.getRequest().getHeaders().getFirst(CABECALHO);
		ServerWebExchange atual = exchange;
		if (traceId == null || traceId.isBlank()) {
			traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
			ServerHttpRequest pedido = exchange.getRequest().mutate().header(CABECALHO, traceId).build();
			atual = exchange.mutate().request(pedido).build();
		}
		// Só antes de enviar a resposta: nessa altura já lá estão os cabeçalhos do
		// serviço, e o set substitui o X-Trace-Id que ele devolveu em vez de o repetir
		String valor = traceId;
		ServerHttpResponse resposta = atual.getResponse();
		resposta.beforeCommit(() -> {
			resposta.getHeaders().set(CABECALHO, valor);
			return Mono.empty();
		});
		return chain.filter(atual);
	}

	@Override
	public int getOrder() {
		return ORDEM;
	}
}
