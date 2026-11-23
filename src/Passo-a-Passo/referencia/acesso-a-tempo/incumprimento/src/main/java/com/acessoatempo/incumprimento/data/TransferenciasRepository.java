package com.acessoatempo.incumprimento.data;

import com.acessoatempo.incumprimento.enums.EstadoTransferencia;
import com.acessoatempo.incumprimento.models.Transferencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/** Repositório das propostas de transferência. */
public interface TransferenciasRepository extends JpaRepository<Transferencia, Integer> {

    /** Propostas ainda sem resposta cujo prazo já terminou (usado pela tarefa agendada). */
    List<Transferencia> findByEstadoAndRespostaAteBefore(EstadoTransferencia estado, LocalDate data);
}
