package com.acessoatempo.incumprimento.exceptions;

/**
 * Evento recebido com dados em falta ou mal formados. Não é um erro HTTP (ninguém está à espera de
 * resposta): a exceção sobe até ao listener, o RabbitMQ repete 3 vezes e, se continuar a falhar,
 * a mensagem vai para a Dead-Letter Queue em vez de bloquear a fila para sempre.
 */
public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
