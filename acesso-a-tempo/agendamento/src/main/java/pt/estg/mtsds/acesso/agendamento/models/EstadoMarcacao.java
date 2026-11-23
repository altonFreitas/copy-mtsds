package pt.estg.mtsds.acesso.agendamento.models;

/** Estado de uma marcação. Só AGENDADA conta como marcação ativa. */
public enum EstadoMarcacao {
	AGENDADA, REALIZADA, FALTA, CANCELADA
}
