package com.acessoatempo.indicadores.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Registo dos eventos já processados. É o que torna o consumidor <b>idempotente</b>: o RabbitMQ garante
 * entrega "pelo menos uma vez", por isso o mesmo evento pode chegar duas vezes. Se o {@code eventoId}
 * já estiver aqui, o evento é ignorado.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "eventos_processados")
public class EventoProcessado {

    /** O próprio identificador do evento é a chave primária (não pode haver dois iguais). */
    @Id
    private String eventoId;

    private String tipo;

    private LocalDateTime processadoEm;
}
