package com.zeabay.pulse.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * Extends the default Spring Data R2DBC repository scan to include the {@code zeabay-outbox}
 * module's {@code OutboxEventRepository}, which lives outside the application's base package.
 *
 * <p>Kept as a separate {@code @Configuration} class (not on {@code @SpringBootApplication}) so
 * that {@code @WebFluxTest} slice tests exclude it and avoid starting the R2DBC context.
 */
@Configuration
@EnableR2dbcRepositories(
    basePackages = {"com.zeabay.pulse.auth.domain.repository", "com.zeabay.common.outbox"})
public class R2dbcConfig {}
