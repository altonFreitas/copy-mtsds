package com.acessoatempo.indicadores.data;

import com.acessoatempo.indicadores.models.IndicadorSemanal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repositório JPA dos indicadores. Ao estender {@code JpaRepository} ficamos logo com
 * save, findById, findAll, delete... sem escrever SQL (aula 03).
 */
public interface IndicadoresRepository extends JpaRepository<IndicadorSemanal, Integer> {

    /** Query derivada do NOME do método: o JPA deduz o SQL a partir de "findBy" + campos. */
    Optional<IndicadorSemanal> findByEspecialidadeAndSemana(String especialidade, String semana);

    /** Todas as semanas de uma especialidade (para somar, calcular médias e projeções). */
    List<IndicadorSemanal> findByEspecialidade(String especialidade);

    /**
     * Query explícita em JPQL (aula 03: {@code @Query}). Agrega por especialidade todas as semanas
     * cujo identificador começa por {@code periodo} (ex.: "2026" apanha 2026-W01 ... 2026-W53).
     */
    @Query("select i.especialidade as especialidade, sum(i.entradas) as entradas, "
            + "sum(i.incumpridos) as incumpridos from IndicadorSemanal i "
            + "where i.semana like concat(:periodo, '%') group by i.especialidade order by i.especialidade")
    List<CumprimentoProjection> cumprimentoPorEspecialidade(@Param("periodo") String periodo);
}
