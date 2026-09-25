package com.grupo21.rescuesync.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Habilita la auditoría JPA (createdAt / updatedAt automáticos en BaseEntity).
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
