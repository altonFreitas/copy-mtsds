package pt.estg.mtsds.acesso.gestaoacesso.dtos;

import java.time.LocalDate;
import java.util.List;

/**
 * Resposta ao avanço do relógio: a nova data e os pedidos que mudaram de
 * situação na verificação feita logo a seguir.
 *
 * @param dataAtual
 *            data do relógio depois do avanço
 * @param passaramEmRisco
 *            pedidos que passaram a EM_RISCO
 * @param passaramIncumpridos
 *            pedidos que passaram a INCUMPRIDO
 */
public record RelogioAvancadoDTO(LocalDate dataAtual, List<Long> passaramEmRisco, List<Long> passaramIncumpridos) {
}
