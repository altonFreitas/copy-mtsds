package com.acessoatempo.incumprimento.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Cálculo de dias úteis (regra R6): o diretor de serviço tem 5 dias úteis para reagendar, sem contar
 * sábados, domingos e feriados nacionais. Os feriados vêm de uma lista configurável no application.yml
 * ({@code incumprimento.feriados}), por isso podem mudar sem alterar o código.
 */
@Service
public class DiasUteisService {

    private final Set<LocalDate> feriados = new HashSet<>();

    /**
     * Injeção por construtor: o Spring passa o valor de {@code incumprimento.feriados} (texto com datas
     * separadas por vírgula; por omissão vazio). Num teste basta fazer {@code new DiasUteisService("2026-12-25")}.
     */
    public DiasUteisService(@Value("${incumprimento.feriados:}") String feriadosConfigurados) {
        Arrays.stream(feriadosConfigurados.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(LocalDate::parse)
                .forEach(feriados::add);
    }

    /** Verdadeiro se for dia útil: não é sábado, nem domingo, nem feriado. */
    public boolean eDiaUtil(LocalDate data) {
        DayOfWeek dia = data.getDayOfWeek();
        return dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY && !feriados.contains(data);
    }

    /**
     * Soma {@code dias} dias úteis a {@code inicio}. O dia de início não conta: 5 dias úteis a partir de
     * uma segunda-feira (sem feriados) terminam na segunda-feira seguinte.
     */
    public LocalDate adicionarDiasUteis(LocalDate inicio, int dias) {
        LocalDate data = inicio;
        int somados = 0;
        while (somados < dias) {
            data = data.plusDays(1);
            if (eDiaUtil(data)) {
                somados++;
            }
        }
        return data;
    }
}
