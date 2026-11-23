package pt.estg.mtsds.acesso.indicadores.data;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pt.estg.mtsds.acesso.indicadores.models.IndicadorSemanal;

/**
 * Acesso à tabela indicador_semanal.
 */
public interface IndicadorSemanalRepository extends JpaRepository<IndicadorSemanal, Long> {

	/**
	 * A linha de uma especialidade numa semana.
	 *
	 * @param especialidade
	 *            especialidade
	 * @param semana
	 *            semana ISO, por exemplo 2026-W47
	 * @return a linha, ou vazio se ainda não existir
	 */
	Optional<IndicadorSemanal> findByEspecialidadeAndSemana(String especialidade, String semana);

	/**
	 * Todas as semanas de uma especialidade (para somar, calcular médias e
	 * projeções).
	 *
	 * @param especialidade
	 *            especialidade
	 * @return as linhas dessa especialidade
	 */
	List<IndicadorSemanal> findByEspecialidade(String especialidade);

	/**
	 * Soma, por especialidade, as semanas cujo identificador começa por um período
	 * (por exemplo, 2026 apanha 2026-W01 até 2026-W53).
	 *
	 * @param periodo
	 *            ano (2026) ou semana (2026-W47)
	 * @return entradas e incumpridos por especialidade
	 */
	@Query("select i.especialidade as especialidade, sum(i.entradas) as entradas, "
			+ "sum(i.incumpridos) as incumpridos from IndicadorSemanal i "
			+ "where i.semana like concat(:periodo, '%') group by i.especialidade order by i.especialidade")
	List<CumprimentoProjection> cumprimentoPorEspecialidade(@Param("periodo") String periodo);
}
