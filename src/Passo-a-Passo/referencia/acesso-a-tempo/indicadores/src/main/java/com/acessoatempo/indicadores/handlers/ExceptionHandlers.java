package com.acessoatempo.indicadores.handlers;

import com.acessoatempo.indicadores.dtos.ErroDTO;
import com.acessoatempo.indicadores.exceptions.NegocioException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;

/**
 * Tratamento global de exceções (aula {@code loan_proposals}: {@code @ControllerAdvice}).
 * Todas as respostas de erro do serviço saem no mesmo formato ({@link ErroDTO}).
 */
@ControllerAdvice
public class ExceptionHandlers {

    private static final Logger log = LoggerFactory.getLogger(ExceptionHandlers.class);

    /** Lê o nome do serviço do application.yml (spring.application.name). */
    @Value("${spring.application.name}")
    private String servico;

    /** Erros de negócio: o estado HTTP e o código vêm da própria exceção. */
    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ErroDTO> handleNegocio(NegocioException e, HttpServletRequest request) {
        return resposta(e.getStatus(), e.getCodigo(), e.getMessage(), request);
    }

    /** Parâmetro com tipo errado (ex.: semanas=abc) ou JSON mal formado: HTTP 400. */
    @ExceptionHandler({TypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErroDTO> handlePedidoInvalido(Exception e, HttpServletRequest request) {
        return resposta(HttpStatus.BAD_REQUEST, "PEDIDO_INVALIDO",
                "Os dados do pedido são inválidos ou estão mal formados. Verifique os parâmetros e o corpo enviado.",
                request);
    }

    /**
     * Qualquer outra exceção. As exceções do próprio Spring MVC (parâmetro em falta, URL inexistente,
     * método não permitido...) implementam {@code ErrorResponse} e já trazem o código HTTP certo.
     * O resto é um erro inesperado: HTTP 500 com mensagem genérica; o detalhe fica só no log.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroDTO> handleGenerica(Exception e, HttpServletRequest request) {
        if (e instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            return resposta(status, "PEDIDO_INVALIDO", er.getBody().getDetail(), request);
        }
        log.error("Erro inesperado em {}", request.getRequestURI(), e);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO",
                "Ocorreu um erro inesperado. Indique o traceId a quem der suporte.", request);
    }

    /** Constrói o corpo de erro comum. O traceId vem do cabeçalho que a Gateway acrescenta. */
    private ResponseEntity<ErroDTO> resposta(HttpStatus status, String codigo, String mensagem,
                                             HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        ErroDTO erro = new ErroDTO(servico, status.value(), codigo, mensagem,
                request.getRequestURI(), LocalDateTime.now(), traceId != null ? traceId : "n/a");
        return ResponseEntity.status(status).body(erro);
    }
}
