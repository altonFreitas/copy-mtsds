package pt.estg.mtsds.acesso.indicadores.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Modelo de leitura do serviço: uma linha por (especialidade, semana) com
 * contadores agregados. O serviço não guarda episódios nem utentes, só
 * contagens (RNF03); cada evento recebido incrementa um ou mais contadores.
 */
@Entity
@Table(name = "indicador_semanal", uniqueConstraints = @UniqueConstraint(name = "uk_indicador_semanal_especialidade_semana", columnNames = {
		"especialidade", "semana"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndicadorSemanal {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** Especialidade hospitalar, tal como vem nos eventos. */
	@Column(name = "especialidade", nullable = false, length = 60)
	private String especialidade;

	/** Semana ISO no formato 2026-W47. */
	@Column(name = "semana", nullable = false, length = 8)
	private String semana;

	/** Episódios que entraram na lista nessa semana (episodio.inscrito). */
	@Column(name = "entradas", nullable = false)
	private long entradas;

	/** Episódios que saíram da lista nessa semana (episodio.saida). */
	@Column(name = "saidas", nullable = false)
	private long saidas;

	/** Episódios que ultrapassaram a data-limite (tmrg.incumprido). */
	@Column(name = "incumpridos", nullable = false)
	private long incumpridos;

	/** Número de triagens feitas (pedido.triado). */
	@Column(name = "triagens", nullable = false)
	private long triagens;

	/**
	 * Soma dos dias entre o pedido e a triagem; dividida por triagens dá o tempo
	 * médio.
	 */
	@Column(name = "dias_triagem_total", nullable = false)
	private long diasTriagemTotal;
}
