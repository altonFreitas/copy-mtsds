package pt.estg.mtsds.acesso.agendamento.models;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Agenda de consultas de uma especialidade num dia. As vagas pertencem a uma
 * agenda. A data de criação é preenchida pela auditoria do Spring Data.
 */
@Entity
@Table(name = "agenda", indexes = {@Index(name = "idx_agenda_especialidade_data", columnList = "especialidade, data")})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agenda {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "especialidade", nullable = false, length = 60)
	private String especialidade;

	@Column(name = "data", nullable = false)
	private LocalDate data;

	/** Id de quem criou a agenda, vindo do cabeçalho X-Utilizador. */
	@Column(name = "criada_por", nullable = false, length = 60)
	private String criadaPor;

	@CreatedDate
	@Column(name = "criada_em", nullable = false, updatable = false)
	private LocalDateTime criadaEm;
}
