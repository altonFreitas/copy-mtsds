package pt.estg.mtsds.acesso.agendamento.models;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vaga de consulta numa agenda. A especialidade repete a da agenda, para a
 * pesquisa das vagas livres não precisar de juntar as duas tabelas.
 * <p>
 * A versão faz o bloqueio otimista: se duas marcações tentarem ocupar a mesma
 * vaga ao mesmo tempo, a segunda falha em vez de a ocupar outra vez.
 */
@Entity
@Table(name = "vaga", indexes = {
		@Index(name = "idx_vaga_especialidade_estado_data_hora", columnList = "especialidade, estado, data_hora")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vaga {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "agenda_id", nullable = false)
	private Agenda agenda;

	@Column(name = "especialidade", nullable = false, length = 60)
	private String especialidade;

	@Column(name = "data_hora", nullable = false)
	private LocalDateTime dataHora;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@Column(name = "estado", nullable = false, length = 20)
	private EstadoVaga estado = EstadoVaga.LIVRE;

	@Version
	@Column(name = "versao", nullable = false)
	private Long versao;
}
