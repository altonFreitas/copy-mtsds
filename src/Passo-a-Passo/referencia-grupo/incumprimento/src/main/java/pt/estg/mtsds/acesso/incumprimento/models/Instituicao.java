package pt.estg.mtsds.acesso.incumprimento.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Instituição de destino simulada, para onde se pode propor uma consulta quando
 * o prazo falha (nível 3). Numa instituição real isto seria um sistema externo;
 * aqui é uma tabela simples (ver DadosIniciais).
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "instituicoes")
public class Instituicao {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(nullable = false)
	private String nome;

	/** Especialidade que a instituição aceita. */
	@Column(nullable = false)
	private String especialidade;

	public Instituicao(String nome, String especialidade) {
		this.nome = nome;
		this.especialidade = especialidade;
	}
}
