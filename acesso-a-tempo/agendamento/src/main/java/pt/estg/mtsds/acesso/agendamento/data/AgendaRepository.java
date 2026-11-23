package pt.estg.mtsds.acesso.agendamento.data;

import org.springframework.data.jpa.repository.JpaRepository;

import pt.estg.mtsds.acesso.agendamento.models.Agenda;

/**
 * Acesso à tabela agenda.
 */
public interface AgendaRepository extends JpaRepository<Agenda, Long> {
}
