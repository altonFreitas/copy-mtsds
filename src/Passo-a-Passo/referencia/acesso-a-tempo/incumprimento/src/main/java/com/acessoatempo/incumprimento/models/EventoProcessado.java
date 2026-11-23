package com.acessoatempo.incumprimento.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Registo dos eventos já processados (idempotência do consumidor). O RabbitMQ garante entrega "pelo menos
 * uma vez": se o mesmo {@code eventoId} chegar duas vezes, a segunda é ignorada.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "eventos_processados")
public class EventoProcessado {

    @Id
    private String eventoId;

    private String tipo;

    private LocalDateTime processadoEm;
}
