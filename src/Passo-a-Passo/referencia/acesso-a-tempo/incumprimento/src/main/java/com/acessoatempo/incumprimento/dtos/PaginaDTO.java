package com.acessoatempo.incumprimento.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Resposta paginada genérica (RNF12). Em vez de devolver o objeto {@code Page} do Spring (que traz muita
 * informação técnica e não tem formato JSON estável), devolve-se só o que o cliente precisa.
 *
 * @param <T> tipo dos elementos da página (aqui, {@code CasoDTO})
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaginaDTO<T> {

    private List<T> conteudo;

    /** Número da página, a começar em 0. */
    private int pagina;

    private int tamanho;

    private long totalElementos;

    private int totalPaginas;
}
