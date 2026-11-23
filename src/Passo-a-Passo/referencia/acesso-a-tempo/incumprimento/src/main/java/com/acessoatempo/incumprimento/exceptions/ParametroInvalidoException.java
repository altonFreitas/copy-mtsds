package com.acessoatempo.incumprimento.exceptions;

import org.springframework.http.HttpStatus;

/** Parâmetro do pedido em falta ou com valor inválido: HTTP 400. */
public class ParametroInvalidoException extends NegocioException {

    public ParametroInvalidoException(String mensagem) {
        super(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", mensagem);
    }
}
