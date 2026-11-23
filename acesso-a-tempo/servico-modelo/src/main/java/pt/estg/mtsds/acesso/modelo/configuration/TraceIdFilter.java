package pt.estg.mtsds.acesso.modelo.configuration;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Coloca o traceId de cada pedido no MDC, para aparecer em todas as linhas de
 * log (RNF08). Usa o cabeçalho X-Trace-Id enviado pela gateway; se não vier,
 * gera um.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

	public static final String CABECALHO = "X-Trace-Id";

	@Override
	protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
			throws ServletException, IOException {
		String traceId = pedido.getHeader(CABECALHO);
		if (traceId == null || traceId.isBlank()) {
			traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		}
		MDC.put("traceId", traceId);
		resposta.setHeader(CABECALHO, traceId);
		try {
			cadeia.doFilter(pedido, resposta);
		} finally {
			MDC.remove("traceId");
			MDC.remove("episodioId");
		}
	}
}
