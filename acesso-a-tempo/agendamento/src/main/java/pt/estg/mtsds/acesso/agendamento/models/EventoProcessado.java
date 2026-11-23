package pt.estg.mtsds.acesso.agendamento.models;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Evento do RabbitMQ que o serviço já processou. Serve para os consumidores
 * serem idempotentes: um evento cujo id já aqui esteja é ignorado. A chave é o
 * id que vem no próprio evento, por isso não é gerada aqui.
 */
@Entity
@Table(name = "eventos_processados")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoProcessado {

	@Id
	@Column(name = "evento_id", length = 64)
	private String eventoId;

	/** Routing key do evento, por exemplo pedido.triado. */
	@Column(name = "tipo", nullable = false, length = 60)
	private String tipo;

	@CreatedDate
	@Column(name = "processado_em", nullable = false, updatable = false)
	private LocalDateTime processadoEm;
}
