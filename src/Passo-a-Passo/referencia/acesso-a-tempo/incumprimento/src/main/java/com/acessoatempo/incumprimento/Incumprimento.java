package com.acessoatempo.incumprimento;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Classe de arranque do microserviço {@code incumprimento}.
 *
 * <p>{@code @EnableScheduling} ativa as tarefas agendadas ({@code @Scheduled}); é usada para
 * dar como recusadas as propostas de transferência a que a outra instituição não respondeu a tempo.
 */
@SpringBootApplication
@EnableScheduling
public class Incumprimento {

    public static void main(String[] args) {
        SpringApplication.run(Incumprimento.class, args);
    }
}
