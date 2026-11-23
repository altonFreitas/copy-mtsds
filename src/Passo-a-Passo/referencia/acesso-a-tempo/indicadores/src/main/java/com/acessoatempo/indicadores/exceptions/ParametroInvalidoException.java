package com.acessoatempo.indicadores.exceptions;

import org.springframework.http.HttpStatus;

/** Parâmetro do pedido em falta ou com valor inválido: resulta em HTTP 400. */
public class ParametroInvalidoException extends NegocioException {

    public ParametroInvalidoException(String mensagem) {
        super(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", mensagem);
    }
}
