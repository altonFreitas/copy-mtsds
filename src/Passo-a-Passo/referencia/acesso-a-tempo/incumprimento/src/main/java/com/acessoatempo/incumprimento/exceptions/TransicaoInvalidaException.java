package com.acessoatempo.incumprimento.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Operação que viola uma regra de negócio (ex.: alertar o diretor de um caso já resolvido): HTTP 409 Conflict.
 * O pedido está bem formado, mas o estado atual do caso não o permite.
 */
public class TransicaoInvalidaException extends NegocioException {

    public TransicaoInvalidaException(String mensagem) {
        super(HttpStatus.CONFLICT, "TRANSICAO_INVALIDA", mensagem);
    }
}
