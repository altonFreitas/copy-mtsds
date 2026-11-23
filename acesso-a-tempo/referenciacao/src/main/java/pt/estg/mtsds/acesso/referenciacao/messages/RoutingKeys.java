package pt.estg.mtsds.acesso.referenciacao.messages;

//Constantes com as RoutingKeys para a TopicExchange
public final class RoutingKeys {

	private RoutingKeys() {
		// Evita instanciaçao
	}

	// Referenciacao e triagem
	public static final String PEDIDO_REFERENCIADO = "pedido.referenciado";
	public static final String PEDIDO_TRIADO = "pedido.triado";
	public static final String PRIORIDADE_REAVALIADA = "prioridade.reavaliada";
	public static final String PEDIDO_DEVOLVIDO = "pedido.devolvido";

	// Lista de espera e TMRG
	public static final String EPISODIO_INSCRITO = "episodio.inscrito";
	public static final String PRAZO_DEFINIDO = "prazo.definido";
	public static final String EPISODIO_SAIDA = "episodio.saida";
	public static final String TMRG_RISCO_DETETADO = "tmrg.risco-detetado";
	public static final String TMRG_INCUMPRIMENTO_PREVISTO = "tmrg.incumprimento-previsto";
	public static final String TMRG_INCUMPRIDO = "tmrg.incumprido";
	public static final String RELOGIO_AVANCADO = "relogio.avancado";

	// Agendamento
	public static final String CONSULTA_AGENDADA = "consulta.agendada";
	public static final String CONSULTA_REALIZADA = "consulta.realizada";
	public static final String CONSULTA_FALTA_REGISTADA = "consulta.falta-registada";
	public static final String CONSULTA_DESMARCADA = "consulta.desmarcada";

	// incumprimento e transferencia
	public static final String INCUMPRIMENTO_REGISTADO = "incumprimento.registado";
	public static final String INCUMPRIMENTO_DIRETOR_ALERTADO = "incumprimento.diretor-alertado";
	public static final String INCUMPRIMENTO_RESOLVIDO = "incumprimento.resolvido";
	public static final String TRANSFERENCIA_PROPOSTA = "transferencia.proposta";
	public static final String TRANSFERENCIA_ACEITE = "transferencia.aceite";
	public static final String TRANSFERENCIA_RECUSADA = "transferencia.recusada";

	// Utentes
	public static final String UTENTE_CONSULTADO = "utente.consultado";
}
