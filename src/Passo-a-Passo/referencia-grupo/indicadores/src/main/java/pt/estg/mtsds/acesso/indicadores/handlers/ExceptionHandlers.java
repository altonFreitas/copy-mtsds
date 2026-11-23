package pt.estg.mtsds.acesso.indicadores.handlers;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import pt.estg.mtsds.acesso.indicadores.dtos.ErroCampoDTO;
import pt.estg.mtsds.acesso.indicadores.dtos.ErroDTO;
import pt.estg.mtsds.acesso.indicadores.exceptions.DadosInvalidosException;
import pt.estg.mtsds.acesso.indicadores.exceptions.NaoEncontradoException;
import pt.estg.mtsds.acesso.indicadores.exceptions.RegraNegocioException;
import pt.estg.mtsds.acesso.indicadores.exceptions.ServicoIndisponivelException;

/**
 * Tratamento global de exceções (@ControllerAdvice, slide 04). Todas as
 * respostas de erro usam o ErroDTO. O detalhe técnico fica só no log. O último
 * handler apanha tudo o que nenhum outro tratou (500).
 */
@Slf4j
@RestControllerAdvice
public class ExceptionHandlers {

	@Value("${spring.application.name}")
	private String nomeServico;

	@ExceptionHandler(NaoEncontradoException.class)
	public ResponseEntity<ErroDTO> naoEncontrado(NaoEncontradoException e, HttpServletRequest pedido) {
		log.warn("{}: {}", e.getCodigo(), e.getMessage());
		return resposta(HttpStatus.NOT_FOUND, e.getCodigo(), e.getMessage(), pedido, null);
	}

	@ExceptionHandler(DadosInvalidosException.class)
	public ResponseEntity<ErroDTO> dadosInvalidos(DadosInvalidosException e, HttpServletRequest pedido) {
		log.warn("{}: {}", e.getCodigo(), e.getMessage());
		return resposta(HttpStatus.BAD_REQUEST, e.getCodigo(), e.getMessage(), pedido, null);
	}

	@ExceptionHandler(RegraNegocioException.class)
	public ResponseEntity<ErroDTO> regraNegocio(RegraNegocioException e, HttpServletRequest pedido) {
		log.warn("{}: {}", e.getCodigo(), e.getMessage());
		return resposta(HttpStatus.CONFLICT, e.getCodigo(), e.getMessage(), pedido, null);
	}

	@ExceptionHandler(ServicoIndisponivelException.class)
	public ResponseEntity<ErroDTO> servicoIndisponivel(ServicoIndisponivelException e, HttpServletRequest pedido) {
		log.error("{}: {}", e.getCodigo(), e.getMessage());
		return resposta(HttpStatus.SERVICE_UNAVAILABLE, e.getCodigo(), e.getMessage(), pedido, null);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErroDTO> validacao(MethodArgumentNotValidException e, HttpServletRequest pedido) {
		List<ErroCampoDTO> erros = e.getBindingResult().getFieldErrors().stream()
				.map(f -> new ErroCampoDTO(f.getField(), f.getDefaultMessage())).toList();
		String mensagem = "O pedido tem " + erros.size()
				+ " campo(s) inválido(s). Corrija os campos indicados em erros e repita o pedido.";
		log.warn("DADOS_INVALIDOS: {}", erros);
		return resposta(HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS", mensagem, pedido, erros);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErroDTO> jsonInvalido(HttpMessageNotReadableException e, HttpServletRequest pedido) {
		log.warn("JSON_INVALIDO: {}", e.getMessage());
		return resposta(HttpStatus.BAD_REQUEST, "JSON_INVALIDO",
				"O corpo do pedido não é JSON válido ou tem um valor de tipo errado. "
						+ "Verifique as vírgulas, as aspas e os valores aceites por cada campo.",
				pedido, null);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErroDTO> tipoErrado(MethodArgumentTypeMismatchException e, HttpServletRequest pedido) {
		log.warn("IDENTIFICADOR_INVALIDO: {}={}", e.getName(), e.getValue());
		return resposta(HttpStatus.BAD_REQUEST, "IDENTIFICADOR_INVALIDO",
				"O valor " + e.getValue() + " não é válido para o parâmetro " + e.getName()
						+ ". Use o identificador devolvido na criação do recurso.",
				pedido, null);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErroDTO> rotaInexistente(NoResourceFoundException e, HttpServletRequest pedido) {
		log.warn("ROTA_NAO_ENCONTRADA: {} {}", pedido.getMethod(), pedido.getRequestURI());
		return resposta(HttpStatus.NOT_FOUND, "ROTA_NAO_ENCONTRADA",
				"Não existe o endpoint " + pedido.getMethod() + " " + pedido.getRequestURI() + " no serviço "
						+ nomeServico + ". Confirme o caminho na documentação da API.",
				pedido, null);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErroDTO> metodoNaoSuportado(HttpRequestMethodNotSupportedException e,
			HttpServletRequest pedido) {
		log.warn("METODO_NAO_SUPORTADO: {} {}", pedido.getMethod(), pedido.getRequestURI());
		return resposta(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NAO_SUPORTADO",
				"O método " + pedido.getMethod() + " não é aceite em " + pedido.getRequestURI() + ". Métodos aceites: "
						+ e.getSupportedHttpMethods() + ".",
				pedido, null);
	}

	/** Exceção genérica: tudo o que nenhum outro handler tratou. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErroDTO> erroInterno(Exception e, HttpServletRequest pedido) {
		log.error("ERRO_INTERNO em {} {}", pedido.getMethod(), pedido.getRequestURI(), e);
		return resposta(
				HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO", "Ocorreu um erro inesperado no serviço " + nomeServico
						+ " e o pedido não foi concluído. " + "Indique o código " + MDC.get("traceId") + " ao suporte.",
				pedido, null);
	}

	private ResponseEntity<ErroDTO> resposta(HttpStatus estado, String codigo, String mensagem,
			HttpServletRequest pedido, List<ErroCampoDTO> erros) {
		ErroDTO erro = ErroDTO.builder().servico(nomeServico).estado(estado.value()).erro(codigo).mensagem(mensagem)
				.erros(erros).caminho(pedido.getRequestURI()).dataHora(LocalDateTime.now()).traceId(MDC.get("traceId"))
				.build();
		return ResponseEntity.status(estado).body(erro);
	}
}
