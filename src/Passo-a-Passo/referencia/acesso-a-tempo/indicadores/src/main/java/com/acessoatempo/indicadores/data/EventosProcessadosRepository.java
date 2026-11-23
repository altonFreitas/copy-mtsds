package com.acessoatempo.indicadores.data;

import com.acessoatempo.indicadores.models.EventoProcessado;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório dos eventos já processados. Só usamos {@code existsById} e {@code save},
 * que já vêm do {@code JpaRepository}.
 */
public interface EventosProcessadosRepository extends JpaRepository<EventoProcessado, String> {
}
