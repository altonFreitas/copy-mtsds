package com.acessoatempo.indicadores.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resposta de {@code GET /indicadores/lista}: tamanho da lista e movimento (entradas/saídas).
 * É um DTO: só tem o que o cliente precisa, nunca a entidade JPA (RNF10).
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ListaIndicadorDTO {

    private String especialidade;

    /** Semana pedida (ex.: 2026-W47) ou {@code null} se o pedido foi sobre todo o período. */
    private String semana;

    private long entradas;

    private long saidas;

    /** Tamanho da lista = entradas - saídas (acumulado até à semana pedida). */
    private long tamanhoLista;
}
