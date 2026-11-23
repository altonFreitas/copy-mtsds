package com.acessoatempo.incumprimento.exceptions;

import org.springframework.http.HttpStatus;

/** O caso pedido não existe: HTTP 404. */
public class CasoNaoEncontradoException extends NegocioException {

    public CasoNaoEncontradoException(Integer id) {
        super(HttpStatus.NOT_FOUND, "CASO_NAO_ENCONTRADO",
                "O caso de incumprimento " + id + " não existe. Consulte GET /incumprimentos para ver os casos.");
    }
}
