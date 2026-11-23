package pt.estg.mtsds.acesso.agendamento.models;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Marcação de uma vaga para um episódio da lista de espera. O episódio é só o
 * id que vem de outro serviço, sem chave estrangeira. A data de criação é
 * preenchida pela auditoria do Spring Data.
 */
@Entity
@Table(name = "marcacao", indexes = {@Index(name = "idx_marcacao_episodio_id", columnList = "episodio_id"),
		@Index(name = "idx_marcacao_vaga_id", columnList = "vaga_id")})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Marcacao {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "episodio_id", nullable = false)
	private Long episodioId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vaga_id", nullable = false)
	private Vaga vaga;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@Column(name = "estado", nullable = false, length = 20)
	private EstadoMarcacao estado = EstadoMarcacao.AGENDADA;

	@Enumerated(EnumType.STRING)
	@Column(name = "origem", nullable = false, length = 20)
	private OrigemMarcacao origem;

	/** Quem fez a marcação: o id do utilizador, ou o serviço na automática. */
	@Column(name = "autor", nullable = false, length = 60)
	private String autor;

	@CreatedDate
	@Column(name = "criada_em", nullable = false, updatable = false)
	private LocalDateTime criadaEm;
}
