package pt.estg.mtsds.acesso.incumprimento.dtos;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Formato comum de erro de todos os serviços (secção 8.1 da proposta). O campo
 * erro é um código estável, para os testes não dependerem do texto.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErroDTO {

	private String servico;
	private int estado;
	private String erro;
	private String mensagem;
	private List<ErroCampoDTO> erros;
	private String caminho;
	private LocalDateTime dataHora;
	private String traceId;
}
