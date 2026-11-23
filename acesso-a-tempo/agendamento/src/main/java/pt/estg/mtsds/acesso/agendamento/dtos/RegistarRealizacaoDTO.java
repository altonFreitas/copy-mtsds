package pt.estg.mtsds.acesso.agendamento.dtos;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import pt.estg.mtsds.acesso.agendamento.models.EstadoMarcacao;

/**
 * Pedido para registar o que aconteceu numa consulta marcada.
 *
 * @param resultado
 *            REALIZADA ou FALTA
 */
public record RegistarRealizacaoDTO(
		@NotNull(message = "Indique o resultado da consulta: REALIZADA ou FALTA.") EstadoMarcacao resultado) {

	/**
	 * Confirma que o resultado é um dos dois aceites. Um resultado nulo fica para a
	 * validação de resultado.
	 *
	 * @return true se o resultado for REALIZADA, FALTA ou nulo
	 */
	@AssertTrue(message = "O resultado tem de ser REALIZADA ou FALTA.")
	public boolean isResultadoAceite() {
		return resultado == null || resultado == EstadoMarcacao.REALIZADA || resultado == EstadoMarcacao.FALTA;
	}
}
