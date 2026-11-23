package pt.estg.mtsds.acesso.incumprimento.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * Mensagem (evento) trocada através do RabbitMQ. Estende o envelope do grupo,
 * {@link MensagemBase}.
 *
 * <p>
 * O formato JSON segue o <b>envelope do grupo</b> ({@code MensagemBase} do
 * repositório {@code acesso-a-tempo}): {@code eventoId}, {@code traceId},
 * {@code autor} e {@code dataHoraEvento}, seguidos dos campos <b>próprios de
 * cada evento, ao mesmo nível</b> (não há um mapa {@code dados}). O <b>tipo</b>
 * do evento NÃO vai no JSON: é a routing key da mensagem (ex.:
 * {@code tmrg.incumprido}).
 *
 * <p>
 * Regras para ser compatível com os outros serviços:
 * <ul>
 * <li>{@code @JsonIgnoreProperties(ignoreUnknown = true)}: campos que este
 * serviço não conhece (os de outros eventos, ou futuros) são ignorados em vez
 * de provocarem erro;</li>
 * <li>todos os campos de negócio são opcionais: cada evento só traz os
 * seus;</li>
 * <li>só identificadores e datas, <b>nunca dados pessoais</b> (secção 4.5 da
 * proposta).</li>
 * </ul>
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
	 * Tipo do evento = routing key. NÃO vai no JSON ({@code @JsonIgnore}): o
	 * listener preenche-o a partir do cabeçalho da mensagem, e o publisher usa-o
	 * como routing key.
	 */
	@JsonIgnore
	private String tipo;

	// ----------------------------------------------------------------
	// identificação do episódio

	/**
	 * O {@code tmrg} identifica o episódio pelo id do pedido (chave da tabela
	 * {@code episodio_prazo}).
	 */
	private Long pedidoId;

	/**
	 * Termo da proposta para o mesmo identificador. Aceitam-se os dois nomes; ao
	 * publicar enviam-se os dois.
	 */
	private Long episodioId;

	private String especialidade;

	private String prioridade;

	// ---------------------------------------------------------------- datas
	// (formato AAAA-MM-DD)

	private LocalDate dataPedido;

	private LocalDate dataLimite;

	private LocalDate dataAlarme;

	private LocalDate dataConsulta;

	/**
	 * Nova data do relógio (evento {@code relogio.avancado}); mesmo nome do DTO
	 * {@code RelogioDTO} do tmrg.
	 */
	private LocalDate dataAtual;

	/**
	 * Nome alternativo de {@link #dataAtual} (usado numa versão anterior deste
	 * contrato).
	 */
	private LocalDate data;

	// ---------------------------------------------------------------- campos dos
	// eventos do incumprimento

	private Integer casoId;

	private Integer transferenciaId;

	private Integer instituicaoId;

	/** Origem do caso: RISCO, INCUMPRIMENTO_PREVISTO ou INCUMPRIDO. */
	private String origem;

	/** Motivo da resolução: CONSULTA_AGENDADA ou TRANSFERIDO. */
	private String motivo;

	private LocalDate dataLimiteDiretor;

	private LocalDate respostaAte;

	/**
	 * Identificador do episódio, seja qual for o nome em que veio
	 * ({@code episodioId} ou {@code pedidoId}). {@code @JsonIgnore}: é um auxiliar,
	 * não faz parte do JSON.
	 */
	@JsonIgnore
	public Long identificador() {
		return episodioId != null ? episodioId : pedidoId;
	}

	/**
	 * Data do relógio do evento {@code relogio.avancado}: {@code dataAtual}, ou
	 * {@code data} se for esse o nome usado.
	 */
	@JsonIgnore
	public LocalDate dataDoRelogio() {
		return dataAtual != null ? dataAtual : data;
	}
}
