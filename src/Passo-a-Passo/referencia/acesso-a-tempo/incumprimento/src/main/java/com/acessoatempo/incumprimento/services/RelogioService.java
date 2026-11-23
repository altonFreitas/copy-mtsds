package com.acessoatempo.incumprimento.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Relógio do serviço (RNF07). Em funcionamento normal devolve a data real. Quando o {@code tmrg} publica
 * {@code relogio.avancado} (só no perfil de demonstração), o serviço passa a usar a data simulada, para que
 * todos os serviços "vejam" o mesmo dia e se possa demonstrar a passagem dos 5 dias úteis sem esperar.
 *
 * <p>Regra de ouro: nenhuma classe do serviço chama {@code LocalDate.now()} diretamente; usa sempre
 * {@link #hoje()}. Assim o tempo pode ser simulado nos testes.
 */
@Service
public class RelogioService {

    private static final Logger log = LoggerFactory.getLogger(RelogioService.class);

    /** null = usa a data real. {@code volatile}: pode ser alterada por uma thread (listener) e lida por outra. */
    private volatile LocalDate dataSimulada;

    /** Data "de hoje" para efeitos de negócio. */
    public LocalDate hoje() {
        return dataSimulada != null ? dataSimulada : LocalDate.now();
    }

    /** Passa a usar uma data simulada. */
    public void definir(LocalDate data) {
        log.info("Relógio simulado: {} -> {}", hoje(), data);
        this.dataSimulada = data;
    }

    /** Volta a usar a data real (usado nos testes). */
    public void repor() {
        this.dataSimulada = null;
    }
}
