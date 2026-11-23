package pt.estg.mtsds.acesso.indicadores.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Uma linha por evento recebido na fila do historico. É uma tabela de
 * acrescentar (append-only): nunca há updates nem deletes. O episodioId é a
 * chave de todos os eventos de um caso; pode ser nulo para eventos que não são
 * de um episódio concreto (ex.: relogio.avancado).
 */
@Entity
@Table(name = "evento_historico")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoHistorico {

	/** UUID do evento — chave primária; vem no envelope MensagemBase. */
	@Id
	@Column(name = "evento_id", nullable = false, length = 36)
	private String eventoId;

	/** Routing key da TopicExchange — identifica o tipo do evento. */
	@Column(name = "routing_key", nullable = false, length = 80)
	private String routingKey;

	/** Número único do episódio; nulo nos eventos que não pertencem a um caso. */
	@Column(name = "episodio_id")
	private Long episodioId;

	/** traceId do pedido que gerou o evento; vem no envelope. */
	@Column(name = "trace_id", length = 36)
	private String traceId;

	/** Utilizador ou serviço que gerou o evento; vem no envelope. */
	@Column(name = "autor", length = 100)
	private String autor;

	/** Data e hora em que o evento aconteceu; vem no envelope. */
	@Column(name = "data_hora_evento", nullable = false)
	private LocalDateTime dataHoraEvento;

	/**
	 * Conteúdo completo do evento em JSON. Guardado como texto para não depender do
	 * esquema dos outros serviços.
	 */
	@Column(name = "conteudo", nullable = false, columnDefinition = "TEXT")
	private String conteudo;
}
