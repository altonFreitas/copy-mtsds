package pt.estg.mtsds.acesso.gestaoacesso.models;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Prazo de um episódio da lista de espera. Há um registo por pedido: a chave é
 * o id do pedido, atribuído no serviço de referenciação, por isso não é gerada
 * aqui.
 * <p>
 * Os índices servem as consultas previstas: por especialidade e data limite,
 * por data de alarme e só por data limite (verificação diária dos incumpridos).
 * As datas de criação e de alteração são preenchidas pela auditoria do Spring
 * Data (JpaAuditingConfiguration).
 */
@Entity
@Table(name = "episodio_prazo", indexes = {
		@Index(name = "idx_episodio_prazo_especialidade_data_limite", columnList = "especialidade, data_limite"),
		@Index(name = "idx_episodio_prazo_data_alarme", columnList = "data_alarme"),
		@Index(name = "idx_episodio_prazo_data_limite", columnList = "data_limite")})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EpisodioPrazo {

	@Id
	@Column(name = "pedido_id")
	private Long pedidoId;

	@Column(name = "especialidade", nullable = false, length = 60)
	private String especialidade;

	@Column(name = "data_pedido", nullable = false)
	private LocalDate dataPedido;

	@Enumerated(EnumType.STRING)
	@Column(name = "prioridade", nullable = false, length = 20)
	private Prioridade prioridade;

	/** Versão da regra TMRG usada para calcular as datas. */
	@Column(name = "regra_versao", nullable = false)
	private Integer regraVersao;

	@Column(name = "data_limite", nullable = false)
	private LocalDate dataLimite;

	@Column(name = "data_alarme", nullable = false)
	private LocalDate dataAlarme;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@Column(name = "situacao", nullable = false, length = 20)
	private SituacaoPrazo situacao = SituacaoPrazo.DENTRO_PRAZO;

	@Column(name = "alarme_risco_em")
	private LocalDateTime alarmeRiscoEm;

	@Column(name = "encerrado_em")
	private LocalDateTime encerradoEm;

	/**
	 * Vaga marcada para este episódio, preenchida quando chega consulta.agendada.
	 * Sem vaga e sem encerramento, o episódio está à espera de marcação.
	 */
	@Column(name = "vaga_id")
	private Long vagaId;

	/** Quando chegou o evento consulta.agendada com a vaga marcada. */
	@Column(name = "marcado_em")
	private LocalDateTime marcadoEm;

	/** Dia e hora da consulta marcada, vindos do evento consulta.agendada. */
	@Column(name = "data_consulta")
	private LocalDateTime dataConsulta;

	@CreatedDate
	@Column(name = "criado_em", nullable = false, updatable = false)
	private LocalDateTime criadoEm;

	@LastModifiedDate
	@Column(name = "atualizado_em", nullable = false)
	private LocalDateTime atualizadoEm;
}
