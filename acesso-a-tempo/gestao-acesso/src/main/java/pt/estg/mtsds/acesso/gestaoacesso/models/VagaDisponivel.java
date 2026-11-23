package pt.estg.mtsds.acesso.gestaoacesso.models;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vaga de consulta livre, recebida do serviço de agendamento. A chave é o id da
 * vaga no agendamento, por isso não é gerada aqui.
 * <p>
 * O índice serve a pesquisa das vagas de uma especialidade por ordem de data,
 * usada na marcação automática (R8).
 */
@Entity
@Table(name = "vaga_disponivel", indexes = {
		@Index(name = "idx_vaga_disponivel_especialidade_data_hora", columnList = "especialidade, data_hora")})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VagaDisponivel {

	@Id
	@Column(name = "vaga_id")
	private Long vagaId;

	@Column(name = "especialidade", nullable = false, length = 60)
	private String especialidade;

	@Column(name = "data_hora", nullable = false)
	private LocalDateTime dataHora;

	@CreatedDate
	@Column(name = "recebida_em", nullable = false, updatable = false)
	private LocalDateTime recebidaEm;
}
