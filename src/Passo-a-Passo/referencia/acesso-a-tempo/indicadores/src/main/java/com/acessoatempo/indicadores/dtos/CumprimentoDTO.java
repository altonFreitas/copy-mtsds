package com.acessoatempo.indicadores.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Resposta de {@code GET /indicadores/cumprimento}: taxa de cumprimento do TMRG por especialidade. */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CumprimentoDTO {

    private String especialidade;

    private long entradas;

    private long incumpridos;

    /** Percentagem de episódios que NÃO ultrapassaram a data-limite (0 a 100). */
    private double taxaCumprimento;
}
