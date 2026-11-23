package pt.estg.mtsds.acesso.incumprimento.data;

import pt.estg.mtsds.acesso.incumprimento.models.Instituicao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repositório das instituições de destino (lista pequena e fechada, por isso
 * sem paginação).
 */
public interface InstituicoesRepository extends JpaRepository<Instituicao, Integer> {

	List<Instituicao> findByEspecialidade(String especialidade);
}
