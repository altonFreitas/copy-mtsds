package com.acessoatempo.incumprimento.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarefa agendada: todos os dias, dá como recusadas as propostas de transferência a que a outra instituição
 * não respondeu no prazo. Evita que a "saga" fique pendurada para sempre (compensação por timeout).
 *
 * <p>Nota: com várias réplicas do serviço, a tarefa correria em todas. O {@code incumprimento} corre com
 * uma só réplica; se escalar, é preciso um lock (ex.: ShedLock).
 */
@Component
public class TransferenciasScheduler {

    private static final Logger log = LoggerFactory.getLogger(TransferenciasScheduler.class);

    @Autowired
    private CasosService casosService;

    /** Cron: segundo minuto hora dia mês dia-da-semana. Aqui: todos os dias à 01:00. */
    @Scheduled(cron = "0 0 1 * * *")
    public void expirarTransferencias() {
        int expiradas = casosService.expirarTransferencias();
        log.info("Tarefa de expiração de transferências: {} propostas dadas como recusadas", expiradas);
    }
}
