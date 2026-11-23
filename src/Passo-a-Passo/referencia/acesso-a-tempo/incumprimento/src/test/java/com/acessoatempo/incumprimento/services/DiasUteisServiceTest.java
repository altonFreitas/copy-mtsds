package com.acessoatempo.incumprimento.services;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes unitários (sem Spring) do cálculo de dias úteis: não precisam de BD nem de contexto. */
public class DiasUteisServiceTest {

    // 25/12/2026 é uma sexta-feira e é feriado.
    private final DiasUteisService servico = new DiasUteisService("2026-12-25, 2027-01-01");

    @Test
    void cincoDiasUteisSaltamOFimDeSemana() {
        // Segunda 16/11/2026 + 5 dias úteis = segunda 23/11/2026.
        assertEquals(LocalDate.of(2026, 11, 23), servico.adicionarDiasUteis(LocalDate.of(2026, 11, 16), 5));
        // Sexta 20/11/2026 + 5 dias úteis = sexta 27/11/2026.
        assertEquals(LocalDate.of(2026, 11, 27), servico.adicionarDiasUteis(LocalDate.of(2026, 11, 20), 5));
    }

    @Test
    void feriadosNaoContam() {
        // Segunda 21/12/2026 + 5 dias úteis: 22, 23, 24, (25 feriado, 26 e 27 fim de semana), 28, 29 -> terça 29/12.
        assertEquals(LocalDate.of(2026, 12, 29), servico.adicionarDiasUteis(LocalDate.of(2026, 12, 21), 5));
    }

    @Test
    void sabadoDomingoEFeriadoNaoSaoDiasUteis() {
        assertFalse(servico.eDiaUtil(LocalDate.of(2026, 11, 21)));   // sábado
        assertFalse(servico.eDiaUtil(LocalDate.of(2026, 11, 22)));   // domingo
        assertFalse(servico.eDiaUtil(LocalDate.of(2026, 12, 25)));   // feriado
        assertTrue(servico.eDiaUtil(LocalDate.of(2026, 11, 23)));    // segunda
    }

    @Test
    void semFeriadosConfiguradosFunciona() {
        DiasUteisService semFeriados = new DiasUteisService("");
        assertTrue(semFeriados.eDiaUtil(LocalDate.of(2026, 12, 25)));
    }
}
