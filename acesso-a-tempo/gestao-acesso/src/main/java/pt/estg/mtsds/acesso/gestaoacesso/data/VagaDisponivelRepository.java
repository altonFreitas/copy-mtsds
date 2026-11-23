package pt.estg.mtsds.acesso.gestaoacesso.data;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pt.estg.mtsds.acesso.gestaoacesso.models.VagaDisponivel;

/**
 * Acesso à tabela vaga_disponivel. A chave é o id da vaga no agendamento.
 */
public interface VagaDisponivelRepository extends JpaRepository<VagaDisponivel, Long> {

	/**
	 * Vagas livres de uma especialidade, da mais cedo para a mais tarde.
	 *
	 * @param especialidade
	 *            especialidade da consulta
	 * @return as vagas dessa especialidade ordenadas por data e hora
	 */
	List<VagaDisponivel> findByEspecialidadeOrderByDataHoraAsc(String especialidade);
}
