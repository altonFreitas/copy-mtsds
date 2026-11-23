package pt.estg.mtsds.acesso.gestaoacesso.models;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Prazo máximo, em dias, de uma prioridade numa versão das regras TMRG. Cada
 * versão tem uma linha por prioridade e uma data de início de vigência. As
 * versões antigas ficam guardadas, para cada episódio manter a versão com que o
 * prazo foi calculado.
 */
@Entity
@Table(name = "regra_versao", uniqueConstraints = @UniqueConstraint(name = "uk_regra_versao_versao_prioridade", columnNames = {
		"versao", "prioridade"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegraVersao {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "versao", nullable = false)
	private Integer versao;

	@Enumerated(EnumType.STRING)
	@Column(name = "prioridade", nullable = false, length = 20)
	private Prioridade prioridade;

	/** Prazo máximo de resposta, em dias. */
	@Column(name = "dias", nullable = false)
	private Integer dias;

	@Column(name = "inicio_vigencia", nullable = false)
	private LocalDate inicioVigencia;

	@CreatedDate
	@Column(name = "criado_em", nullable = false, updatable = false)
	private LocalDateTime criadoEm;
}
