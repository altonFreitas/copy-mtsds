package pt.estg.mtsds.acesso.incumprimento.models;

import pt.estg.mtsds.acesso.incumprimento.enums.EstadoTransferencia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Proposta de transferência de um caso para outra instituição (nível 3). É um
 * passo da "saga" de transferência orquestrada por este serviço.
 *
 * <p>
 * Usa {@code @Getter/@Setter} em vez de {@code @Data}: o {@code @Data} gera
 * {@code equals/hashCode/toString} com TODOS os campos, e com relações entre
 * entidades isso pode dar ciclos infinitos.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "transferencias")
@EntityListeners(AuditingEntityListener.class)
public class Transferencia {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	/**
	 * Muitas transferências podem pertencer ao mesmo caso (ex.: uma recusada e
	 * outra nova).
	 */
	@ManyToOne(optional = false)
	@JoinColumn(name = "caso_id")
	private CasoIncumprimento caso;

	@ManyToOne(optional = false)
	@JoinColumn(name = "instituicao_id")
	private Instituicao instituicao;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoTransferencia estado;

	@Column(nullable = false)
	private LocalDate dataProposta;

	/**
	 * Último dia em que a instituição pode responder; depois disto conta como
	 * recusa.
	 */
	@Column(nullable = false)
	private LocalDate respostaAte;

	private LocalDate dataResposta;

	@CreatedDate
	@Column(name = "creation_date", updatable = false)
	private LocalDateTime creationDate;

	@LastModifiedDate
	@Column(name = "last_modified_date")
	private LocalDateTime lastModifiedDate;
}
