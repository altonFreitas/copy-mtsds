package com.acessoatempo.indicadores.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Resposta de {@code GET /indicadores/triagem}: tempo médio entre o pedido e a triagem. */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TriagemDTO {

    private String especialidade;

    private long triagens;

    /** Média de dias entre o pedido e a triagem (a triagem tardia come o prazo, R1/R7). */
    private double mediaDiasTriagem;
}
