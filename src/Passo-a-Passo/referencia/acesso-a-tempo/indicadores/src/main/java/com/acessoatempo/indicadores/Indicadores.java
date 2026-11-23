package com.acessoatempo.indicadores;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe de arranque do microserviço {@code indicadores}.
 *
 * <p>{@code @SpringBootApplication} junta três coisas: configuração automática (Spring Boot),
 * procura de componentes ({@code @Controller}, {@code @Service}, {@code @Component}...) neste
 * package e nos filhos, e a declaração de beans ({@code @Configuration}).
 */
@SpringBootApplication
public class Indicadores {

    public static void main(String[] args) {
        SpringApplication.run(Indicadores.class, args);
    }
}
