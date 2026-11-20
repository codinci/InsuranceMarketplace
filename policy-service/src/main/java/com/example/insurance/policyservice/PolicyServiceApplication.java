package com.example.insurance.policyservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point of policy-service: the catalogue and, from M4, policies (PLAN 3). */
@SpringBootApplication
public class PolicyServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(PolicyServiceApplication.class, args);
  }
}
