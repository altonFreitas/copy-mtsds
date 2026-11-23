package pt.estg.mtsds.acesso.gestaoacesso.auditing;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Ativa o preenchimento automático de @CreatedDate e @LastModifiedDate nas
 * entidades com @EntityListeners(AuditingEntityListener.class) (slides 03 e
 * 04).
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfiguration {
}
