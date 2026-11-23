package com.acessoatempo.incumprimento.enums;

/** Evento do {@code tmrg} que deu origem ao caso. */
public enum OrigemCaso {
    /** tmrg.risco-detetado: 75% do prazo consumido (ainda não é incumprimento). */
    RISCO,
    /** tmrg.incumprimento-previsto: consulta marcada para depois da data-limite (R5). */
    INCUMPRIMENTO_PREVISTO,
    /** tmrg.incumprido: a data-limite passou sem consulta (R4). */
    INCUMPRIDO
}
