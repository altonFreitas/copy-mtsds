package pt.estg.mtsds.acesso.incumprimento.data;

import pt.estg.mtsds.acesso.incumprimento.models.EventoProcessado;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositório dos eventos já processados (só se usa existsById e save). */
public interface EventosProcessadosRepository extends JpaRepository<EventoProcessado, String> {
}
