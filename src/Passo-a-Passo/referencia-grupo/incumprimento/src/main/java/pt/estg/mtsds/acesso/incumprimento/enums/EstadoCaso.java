package pt.estg.mtsds.acesso.incumprimento.enums;

import java.util.List;

/**
 * Estados de um caso de incumprimento.
 *
 * <pre>
 * EM_RISCO --(incumprimento previsto/incumprido)--> REGISTADO (nível 1)
 * REGISTADO --alerta-diretor--> DIRETOR_ALERTADO (nível 2)
 * DIRETOR_ALERTADO --propor transferência--> TRANSFERENCIA_PROPOSTA (nível 3)
 * TRANSFERENCIA_PROPOSTA --aceite--> RESOLVIDO | --recusada/sem resposta--> SINALIZADO_DIRECAO_CLINICA
 * qualquer estado aberto --consulta marcada--> RESOLVIDO
 * </pre>
 */
public enum EstadoCaso {
	EM_RISCO, REGISTADO, DIRETOR_ALERTADO, TRANSFERENCIA_PROPOSTA, SINALIZADO_DIRECAO_CLINICA, RESOLVIDO;

	/**
	 * Estados em que o caso ainda está aberto (só pode haver um caso aberto por
	 * episódio).
	 */
	public static final List<EstadoCaso> ABERTOS = List.of(EM_RISCO, REGISTADO, DIRETOR_ALERTADO,
			TRANSFERENCIA_PROPOSTA, SINALIZADO_DIRECAO_CLINICA);
}
