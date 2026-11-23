package com.acessoatempo.indicadores.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Modelo de leitura do serviço: uma linha por (especialidade, semana) com contadores agregados.
 *
 * <p>O serviço NÃO guarda episódios nem utentes: só contagens (princípio "indicadores só com dados
 * agregados", RNF03). Cada evento recebido incrementa um ou mais contadores.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "indicadores_semanais",
        uniqueConstraints = @UniqueConstraint(columnNames = {"especialidade", "semana"}))
public class IndicadorSemanal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Especialidade hospitalar (ex.: CARDIOLOGIA). */
    @Column(nullable = false)
    private String especialidade;

    /** Semana ISO no formato {@code 2026-W47}. */
    @Column(nullable = false, length = 8)
    private String semana;

    /** Episódios que entraram na lista nessa semana (evento episodio.inscrito). */
    @Column(nullable = false)
    private long entradas;

    /** Episódios que saíram da lista nessa semana (evento episodio.saida). */
    @Column(nullable = false)
    private long saidas;

    /** Episódios que ultrapassaram a data-limite (evento tmrg.incumprido). */
    @Column(nullable = false)
    private long incumpridos;

    /** Número de triagens feitas (evento pedido.triado). */
    @Column(nullable = false)
    private long triagens;

    /** Soma dos dias entre o pedido e a triagem; dividida por {@code triagens} dá o tempo médio. */
    @Column(nullable = false)
    private long diasTriagemTotal;

    /** Construtor de conveniência: linha nova, com todos os contadores a zero. */
    public IndicadorSemanal(String especialidade, String semana) {
        this.especialidade = especialidade;
        this.semana = semana;
    }
}
