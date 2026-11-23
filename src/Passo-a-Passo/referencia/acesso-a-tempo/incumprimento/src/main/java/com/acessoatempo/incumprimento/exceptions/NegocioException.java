package com.acessoatempo.incumprimento.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base das exceções de negócio que devem virar uma resposta HTTP. Cada exceção concreta diz qual o
 * código HTTP e o código de erro estável; o {@code ExceptionHandlers} faz o resto num só sítio.
 *
 * <p>Estende {@code RuntimeException} (como as exceções do {@code loan_proposals} da aula): não obriga
 * a declarar {@code throws} em todos os métodos.
 */
@Getter
public abstract class NegocioException extends RuntimeException {

    private final HttpStatus status;

    private final String codigo;

    protected NegocioException(HttpStatus status, String codigo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
    }
}
