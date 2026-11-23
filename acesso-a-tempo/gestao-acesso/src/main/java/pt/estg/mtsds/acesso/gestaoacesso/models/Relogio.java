package pt.estg.mtsds.acesso.gestaoacesso.models;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data atual do serviço, guardada na base de dados para a demonstração poder
 * avançar o tempo. A tabela tem uma só linha, sempre com o id {@link #ID}.
 */
@Entity
@Table(name = "relogio")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Relogio {

	/** Id da única linha da tabela. */
	public static final short ID = 1;

	@Id
	@Column(name = "id")
	@Builder.Default
	private Short id = ID;

	@Column(name = "data_atual", nullable = false)
	private LocalDate dataAtual;

	@LastModifiedDate
	@Column(name = "atualizado_em", nullable = false)
	private LocalDateTime atualizadoEm;
}
