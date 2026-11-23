package pt.estg.mtsds.acesso.gestaoacesso.dtos;

import java.time.LocalDate;

/**
 * Data atual do relógio do serviço.
 *
 * @param dataAtual
 *            data de hoje para o serviço
 */
public record RelogioDTO(LocalDate dataAtual) {
}
