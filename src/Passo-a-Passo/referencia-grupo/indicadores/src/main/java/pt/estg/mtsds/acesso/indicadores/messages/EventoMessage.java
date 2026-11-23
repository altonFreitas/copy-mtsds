package pt.estg.mtsds.acesso.indicadores.messages;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Evento recebido do RabbitMQ. Estende o envelope do grupo
 * ({@link MensagemBase}: eventoId, traceId, autor e dataHoraEvento) e
 * acrescenta os campos dos eventos que este serviço consome, ao mesmo nível
 * (não há um mapa de dados).
 * <p>
 * O tipo do evento não vai no JSON: é a routing key da mensagem. Campos que o
 * serviço não conhece são ignorados, para os eventos poderem ganhar campos sem
 * partir os consumidores.
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventoMessage extends MensagemBase {

	/**
	 * Tipo do evento = routing key. Preenchido pelo listener a partir do cabeçalho
	 * da mensagem.
	 */
	@JsonIgnore
	private String tipo;

	/** Identificador do episódio (o mesmo valor do pedidoId). */
	private Long episodioId;

	private Long pedidoId;

	private String especialidade;

	private String prioridade;

	private LocalDate dataPedido;

	private LocalDate dataAlarme;

	private LocalDate dataLimite;

	/**
	 * Identificador do episódio, venha como episodioId ou como pedidoId.
	 *
	 * @return o identificador, ou nulo se o evento não trouxer nenhum
	 */
	@JsonIgnore
	public Long identificador() {
		return episodioId != null ? episodioId : pedidoId;
	}
}
