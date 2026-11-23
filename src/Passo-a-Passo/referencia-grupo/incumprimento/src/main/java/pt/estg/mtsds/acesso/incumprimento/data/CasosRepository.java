package pt.estg.mtsds.acesso.incumprimento.data;

import pt.estg.mtsds.acesso.incumprimento.enums.EstadoCaso;
import pt.estg.mtsds.acesso.incumprimento.models.CasoIncumprimento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

/**
 * Repositório dos casos. Os métodos são "derivados do nome": o Spring Data JPA
 * gera o SQL a partir de {@code findBy...And...}. Os que recebem
 * {@code Pageable} devolvem uma página (RNF12: listas paginadas).
 */
public interface CasosRepository extends JpaRepository<CasoIncumprimento, Integer> {

	/** Procura o caso ABERTO de um episódio (só pode existir um). */
	Optional<CasoIncumprimento> findFirstByEpisodioIdAndEstadoIn(Long episodioId, Collection<EstadoCaso> estados);

	Page<CasoIncumprimento> findByEspecialidade(String especialidade, Pageable pageable);

	Page<CasoIncumprimento> findByNivel(int nivel, Pageable pageable);

	Page<CasoIncumprimento> findByEspecialidadeAndNivel(String especialidade, int nivel, Pageable pageable);
}
