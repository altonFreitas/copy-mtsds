package pt.estg.mtsds.acesso.gestaoacesso.data;

import org.springframework.data.jpa.repository.JpaRepository;

import pt.estg.mtsds.acesso.gestaoacesso.models.RegraVersao;

/**
 * Acesso à tabela regra_versao.
 */
public interface RegraVersaoRepository extends JpaRepository<RegraVersao, Long> {
}
