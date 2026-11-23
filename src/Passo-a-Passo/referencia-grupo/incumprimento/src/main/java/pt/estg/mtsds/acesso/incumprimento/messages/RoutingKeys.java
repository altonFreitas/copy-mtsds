package pt.estg.mtsds.acesso.incumprimento.messages;

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

	// Lista de espera e gestão do acesso
	public static final String EPISODIO_INSCRITO = "episodio.inscrito";
	public static final String PRAZO_DEFINIDO = "prazo.definido";
	public static final String EPISODIO_SAIDA = "episodio.saida";
	public static final String PRAZO_EM_RISCO = "prazo.em-risco";
	public static final String PRAZO_ULTRAPASSADO_PREVISTO = "prazo.ultrapassado-previsto";
	public static final String PRAZO_ULTRAPASSADO = "prazo.ultrapassado";
	public static final String MARCACAO_DECIDIDA = "marcacao.decidida";
	public static final String RELOGIO_AVANCADO = "relogio.avancado";

	// Agendamento
	public static final String CONSULTA_AGENDADA = "consulta.agendada";
	public static final String CONSULTA_REALIZADA = "consulta.realizada";
	public static final String CONSULTA_FALTA_REGISTADA = "consulta.falta-registada";
	public static final String CONSULTA_DESMARCADA = "consulta.desmarcada";
	public static final String VAGA_DISPONIVEL = "vaga.disponivel";

	// garantia-acesso e transferencia
	public static final String CASO_ABERTO = "caso.aberto";
	public static final String CASO_DIRETOR_ALERTADO = "caso.diretor-alertado";
	public static final String CASO_RESOLVIDO = "caso.resolvido";
	public static final String TRANSFERENCIA_PROPOSTA = "transferencia.proposta";
	public static final String TRANSFERENCIA_ACEITE = "transferencia.aceite";
	public static final String TRANSFERENCIA_RECUSADA = "transferencia.recusada";

	// Utentes
	public static final String UTENTE_REGISTADO = "utente.registado";
}
