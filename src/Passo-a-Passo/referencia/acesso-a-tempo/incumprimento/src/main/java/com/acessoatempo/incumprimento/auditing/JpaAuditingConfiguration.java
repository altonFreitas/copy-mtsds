package com.acessoatempo.incumprimento.auditing;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Ativa a auditoria do JPA (igual à aula 03/04). Com isto, os campos anotados com {@code @CreatedDate}
 * e {@code @LastModifiedDate} nas entidades são preenchidos automaticamente pelo Spring.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfiguration {
}
