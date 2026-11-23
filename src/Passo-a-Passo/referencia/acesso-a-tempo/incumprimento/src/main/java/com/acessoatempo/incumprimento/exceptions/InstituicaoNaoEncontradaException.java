package com.acessoatempo.incumprimento.exceptions;

import org.springframework.http.HttpStatus;

/** A instituição de destino não existe: HTTP 404. */
public class InstituicaoNaoEncontradaException extends NegocioException {

    public InstituicaoNaoEncontradaException(Integer id) {
        super(HttpStatus.NOT_FOUND, "INSTITUICAO_NAO_ENCONTRADA",
                "A instituição " + id + " não existe. Consulte GET /instituicoes?esp=... para ver as disponíveis.");
    }
}
