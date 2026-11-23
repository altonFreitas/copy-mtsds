package pt.estg.mtsds.acesso.incumprimento.models;

import pt.estg.mtsds.acesso.incumprimento.enums.EstadoCaso;
import pt.estg.mtsds.acesso.incumprimento.enums.OrigemCaso;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Um caso de incumprimento: acompanha um episódio de espera que está em risco
 * ou já falhou o prazo e regista em que nível de tratamento (1, 2 ou 3) vai.
 * Este modelo é só do contexto {@code incumprimento}: o "episódio" completo
 * pertence à {@code lista-espera}; aqui guarda-se apenas o {@code episodioId}.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "casos_incumprimento")
@EntityListeners(AuditingEntityListener.class)
public class CasoIncumprimento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	/**
	 * Identificador do episódio (vem dos eventos). Não é uma chave estrangeira: é
	 * outro serviço/BD.
	 */
	@Column(nullable = false)
	private Long episodioId;

	@Column(nullable = false)
	private String especialidade;

	/**
	 * Guarda o NOME do enum ("INCUMPRIDO") e não o número da posição, que mudaria
	 * se reordenássemos.
	 */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrigemCaso origem;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoCaso estado;

	/**
	 * 0 = em risco (ainda sem incumprimento); 1, 2 e 3 = níveis de tratamento da
	 * regra R6.
	 */
	@Column(nullable = false)
	private int nivel;

	@Column(nullable = false)
	private LocalDate dataDeteccao;

	/** Fim do prazo de 5 dias úteis do diretor (preenchido no nível 2). */
	private LocalDate dataLimiteDiretor;

	@CreatedDate
	@Column(name = "creation_date", updatable = false)
	private LocalDateTime creationDate;

	@LastModifiedDate
	@Column(name = "last_modified_date")
	private LocalDateTime lastModifiedDate;

	/**
	 * Bloqueio otimista: o Hibernate incrementa este número em cada alteração. Se
	 * duas pessoas alterarem o mesmo caso em simultâneo, a segunda falha em vez de
	 * apagar silenciosamente o trabalho da primeira.
	 */
	@Version
	private Long versao;
}
