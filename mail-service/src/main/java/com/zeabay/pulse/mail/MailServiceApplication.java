package com.zeabay.pulse.mail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@SpringBootApplication
@EnableR2dbcRepositories(basePackages = "com.zeabay.pulse.mail.domain")
public class MailServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(MailServiceApplication.class, args);
  }
}
