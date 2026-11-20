package com.example.insurance.policyservice.config;

import com.example.insurance.policyservice.catalogue.service.CatalogueSeeder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Seeds the catalogue at startup when {@code catalogue.seed.enabled=true} (compose only). */
@Component
@ConditionalOnProperty(name = "catalogue.seed.enabled", havingValue = "true")
public class CatalogueSeedRunner implements ApplicationRunner {

  private final CatalogueSeeder seeder;

  public CatalogueSeedRunner(CatalogueSeeder seeder) {
    this.seeder = seeder;
  }

  @Override
  public void run(ApplicationArguments args) {
    seeder.seed();
  }
}
