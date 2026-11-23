package com.acessoatempo.incumprimento.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Mensagem (evento) trocada através do RabbitMQ.
 *
 * <p>O formato JSON segue o <b>envelope do grupo</b> ({@code MensagemBase} do repositório
 * {@code acesso-a-tempo}): {@code eventoId}, {@code traceId}, {@code autor} e {@code dataHoraEvento},
 * seguidos dos campos <b>próprios de cada evento, ao mesmo nível</b> (não há um mapa {@code dados}).
 * O <b>tipo</b> do evento NÃO vai no JSON: é a routing key da mensagem (ex.: {@code tmrg.incumprido}).
 *
 * <p>Regras para ser compatível com os outros serviços:
 * <ul>
 *   <li>{@code @JsonIgnoreProperties(ignoreUnknown = true)}: campos que este serviço não conhece
 *       (os de outros eventos, ou futuros) são ignorados em vez de provocarem erro;</li>
 *   <li>todos os campos de negócio são opcionais: cada evento só traz os seus;</li>
 *   <li>só identificadores e datas, <b>nunca dados pessoais</b> (secção 4.5 da proposta).</li>
 * </ul>
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventoMessage {

    // ---------------------------------------------------------------- envelope (MensagemBase)

    /** Identificador único do evento (UUID). Serve para o consumidor ignorar repetições (idempotência). */
    private String eventoId;

    /** Identificador do pedido HTTP original, para seguir o caminho nos logs de todos os serviços. */
    private String traceId;

    /** Quem desencadeou a ação (utilizador ou o próprio sistema). */
    private String autor;

    /** Momento em que o evento foi criado (data e hora). */
    private LocalDateTime dataHoraEvento;

    /**
     * Tipo do evento = routing key. NÃO vai no JSON ({@code @JsonIgnore}): o listener preenche-o a partir do
     * cabeçalho da mensagem, e o publisher usa-o como routing key.
     */
    @JsonIgnore
    private String tipo;

    // ---------------------------------------------------------------- identificação do episódio

    /** O {@code tmrg} identifica o episódio pelo id do pedido (chave da tabela {@code episodio_prazo}). */
    private Long pedidoId;

    /** Termo da proposta para o mesmo identificador. Aceitam-se os dois nomes; ao publicar enviam-se os dois. */
    private Long episodioId;

    private String especialidade;

    private String prioridade;

    // ---------------------------------------------------------------- datas (formato AAAA-MM-DD)

    private LocalDate dataPedido;

    private LocalDate dataLimite;

    private LocalDate dataAlarme;

    private LocalDate dataConsulta;

    /** Nova data do relógio (evento {@code relogio.avancado}); mesmo nome do DTO {@code RelogioDTO} do tmrg. */
    private LocalDate dataAtual;

    /** Nome alternativo de {@link #dataAtual} (usado numa versão anterior deste contrato). */
    private LocalDate data;

    // ---------------------------------------------------------------- campos dos eventos do incumprimento

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
     * Identificador do episódio, seja qual for o nome em que veio ({@code episodioId} ou {@code pedidoId}).
     * {@code @JsonIgnore}: é um auxiliar, não faz parte do JSON.
     */
    @JsonIgnore
    public Long identificador() {
        return episodioId != null ? episodioId : pedidoId;
    }

    /** Data do relógio do evento {@code relogio.avancado}: {@code dataAtual}, ou {@code data} se for esse o nome usado. */
    @JsonIgnore
    public LocalDate dataDoRelogio() {
        return dataAtual != null ? dataAtual : data;
    }
}
