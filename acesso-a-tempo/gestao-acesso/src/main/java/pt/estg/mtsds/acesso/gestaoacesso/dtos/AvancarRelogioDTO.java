package pt.estg.mtsds.acesso.gestaoacesso.dtos;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * Pedido para avançar o relógio de demonstração.
 *
 * @param data
 *            data para onde o relógio passa (formato AAAA-MM-DD)
 */
public record AvancarRelogioDTO(
		@NotNull(message = "Indique a data para onde o relógio deve avançar, no formato AAAA-MM-DD.") LocalDate data) {
}
