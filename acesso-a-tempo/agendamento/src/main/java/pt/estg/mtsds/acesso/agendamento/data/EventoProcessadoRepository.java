package pt.estg.mtsds.acesso.agendamento.data;

import org.springframework.data.jpa.repository.JpaRepository;

import pt.estg.mtsds.acesso.agendamento.models.EventoProcessado;

/**
 * Acesso à tabela eventos_processados. A chave é o id do evento.
 */
public interface EventoProcessadoRepository extends JpaRepository<EventoProcessado, String> {
}
