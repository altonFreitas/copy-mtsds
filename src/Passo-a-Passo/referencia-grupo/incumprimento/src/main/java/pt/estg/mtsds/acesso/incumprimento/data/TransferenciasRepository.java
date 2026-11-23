package pt.estg.mtsds.acesso.incumprimento.data;

import pt.estg.mtsds.acesso.incumprimento.enums.EstadoTransferencia;
import pt.estg.mtsds.acesso.incumprimento.models.Transferencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/** Repositório das propostas de transferência. */
public interface TransferenciasRepository extends JpaRepository<Transferencia, Integer> {

	/**
	 * Propostas ainda sem resposta cujo prazo já terminou (usado pela tarefa
	 * agendada).
	 */
	List<Transferencia> findByEstadoAndRespostaAteBefore(EstadoTransferencia estado, LocalDate data);
}
