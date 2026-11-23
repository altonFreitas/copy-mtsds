package pt.estg.mtsds.acesso.gestaoacesso.data;

import org.springframework.data.jpa.repository.JpaRepository;

import pt.estg.mtsds.acesso.gestaoacesso.models.Relogio;

/**
 * Acesso à tabela relogio, que tem uma só linha (id {@link Relogio#ID}).
 */
public interface RelogioRepository extends JpaRepository<Relogio, Short> {
}
