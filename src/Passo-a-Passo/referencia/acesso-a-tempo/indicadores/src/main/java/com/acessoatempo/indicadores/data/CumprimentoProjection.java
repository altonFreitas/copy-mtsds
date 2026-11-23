package com.acessoatempo.indicadores.data;

/**
 * "Projeção" Spring Data: interface cujos getters têm o mesmo nome dos aliases da query
 * ({@code as especialidade}, {@code as entradas}...). O Spring preenche-a automaticamente,
 * sem precisarmos de criar uma classe para um resultado que não é uma entidade.
 */
public interface CumprimentoProjection {

    String getEspecialidade();

    Long getEntradas();

    Long getIncumpridos();
}
