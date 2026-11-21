package com.example.insurance.claimservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point of claim-service: the policy read model and, from M5b, claims (PLAN 3, 7). */
@SpringBootApplication
public class ClaimServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ClaimServiceApplication.class, args);
  }
}
