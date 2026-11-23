package com.acessoatempo.incumprimento.exceptions;

import org.springframework.http.HttpStatus;

/** A transferência pedida não existe: HTTP 404. */
public class TransferenciaNaoEncontradaException extends NegocioException {

    public TransferenciaNaoEncontradaException(Integer id) {
        super(HttpStatus.NOT_FOUND, "TRANSFERENCIA_NAO_ENCONTRADA",
                "A transferência " + id + " não existe.");
    }
}
