package com.acessoatempo.incumprimento.data;

import com.acessoatempo.incumprimento.models.EventoProcessado;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositório dos eventos já processados (só se usa existsById e save). */
public interface EventosProcessadosRepository extends JpaRepository<EventoProcessado, String> {
}
