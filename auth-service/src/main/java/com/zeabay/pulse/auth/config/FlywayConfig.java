package com.zeabay.pulse.auth.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

  @Bean
  public ApplicationRunner flywayRunner(
      @Value("${auth.flyway.url}") String url,
      @Value("${auth.flyway.user}") String user,
      @Value("${auth.flyway.password}") String password) {

    return args -> {
      Flyway flyway =
          Flyway.configure()
              .dataSource(url, user, password)
              .locations("classpath:db/migration")
              .baselineOnMigrate(true)
              .baselineVersion("0")
              .load();

      flyway.migrate();
    };
  }
}
