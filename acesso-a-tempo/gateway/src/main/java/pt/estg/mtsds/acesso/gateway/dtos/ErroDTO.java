package pt.estg.mtsds.acesso.gateway.dtos;

import java.time.LocalDateTime;

/**
 * Resposta de erro da gateway, com os mesmos campos do formato comum dos
 * serviços. O campo erro é um código estável, para os testes não dependerem do
 * texto da mensagem.
 *
 * @param servico
 *            serviço que gerou o erro (sempre "gateway")
 * @param estado
 *            código HTTP
 * @param erro
 *            código do erro
 * @param mensagem
 *            o que falhou e o que fazer
 * @param caminho
 *            caminho do pedido
 * @param dataHora
 *            momento em que o erro foi gerado
 * @param traceId
 *            identificador do pedido, igual ao do cabeçalho X-Trace-Id
 */
public record ErroDTO(String servico, int estado, String erro, String mensagem, String caminho, LocalDateTime dataHora,
		String traceId) {
}
