package com.acessoatempo.indicadores.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Resposta de {@code GET /indicadores/projecao}: previsão do tamanho da lista. */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProjecaoDTO {

    private String especialidade;

    private long tamanhoAtual;

    private double mediaEntradasSemana;

    private double mediaSaidasSemana;

    /** Quantas semanas à frente se projeta. */
    private int semanas;

    /** Tamanho previsto = atual + semanas x (média de entradas - média de saídas). Nunca negativo. */
    private double tamanhoProjetado;
}
