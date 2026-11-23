package pt.estg.mtsds.acesso.indicadores.messages;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

//Clase base para todos os eventos publicadosno RabbitMQ. Transporta os dados e autoria comuns a todosos microsserviços
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MensagemBase {

	// Identificador unico para garantir a idempotencia
	@Builder.Default
	private String eventoId = UUID.randomUUID().toString();

	// transporta o traceid do request HTTP original para os logs nao se perderem
	private String traceId;

	// Quem desencandeou a açao
	private String autor;

	@Builder.Default
	private LocalDateTime dataHoraEvento = LocalDateTime.now();

}
