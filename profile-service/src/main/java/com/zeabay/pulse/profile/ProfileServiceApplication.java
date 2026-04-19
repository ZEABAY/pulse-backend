package com.zeabay.pulse.profile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@SpringBootApplication
@EnableR2dbcRepositories(basePackages = "com.zeabay.pulse.profile.domain.repository")
public class ProfileServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ProfileServiceApplication.class, args);
  }
}
