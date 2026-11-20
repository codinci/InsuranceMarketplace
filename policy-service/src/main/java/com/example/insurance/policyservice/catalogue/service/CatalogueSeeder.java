package com.example.insurance.policyservice.catalogue.service;

import com.example.insurance.policyservice.catalogue.domain.Insurer;
import com.example.insurance.policyservice.catalogue.domain.PlanDocument;
import com.example.insurance.policyservice.catalogue.repository.InsuranceProductRepository;
import com.example.insurance.policyservice.catalogue.repository.InsurerRepository;
import com.example.insurance.policyservice.catalogue.repository.PlanVersionRepository;
import com.example.insurance.policyservice.catalogue.rules.PlanValidator;
import com.example.insurance.policyservice.catalogue.rules.ValidationReport;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

/**
 * Loads the plans in {@code classpath:catalogue-seed} (PLAN 5.4 step 4). Each file goes through the
 * same import and publish services as the admin API, so a seed file is held to the same rules.
 * Versions that already exist are skipped, so it is safe on every start. An invalid seed file stops
 * the service from starting.
 */
@Service
public class CatalogueSeeder {

  static final String LOCATION = "classpath*:catalogue-seed/**/*.json";
  private static final Logger LOG = LoggerFactory.getLogger(CatalogueSeeder.class);

  private final PlanValidator validator;
  private final InsurerRepository insurers;
  private final InsuranceProductRepository products;
  private final PlanVersionRepository plans;
  private final PlanImportService importer;
  private final PlanLifecycleService lifecycle;
  private final Clock clock;

  public CatalogueSeeder(
      PlanValidator validator,
      InsurerRepository insurers,
      InsuranceProductRepository products,
      PlanVersionRepository plans,
      PlanImportService importer,
      PlanLifecycleService lifecycle,
      Clock clock) {
    this.validator = validator;
    this.insurers = insurers;
    this.products = products;
    this.plans = plans;
    this.importer = importer;
    this.lifecycle = lifecycle;
    this.clock = clock;
  }

  /** Returns how many plan versions were published by this run. */
  public int seed() {
    int published = 0;
    for (Resource file : seedFiles()) {
      byte[] bytes = read(file);
      ValidationReport report = validator.validate(new String(bytes, StandardCharsets.UTF_8));
      PlanDocument plan =
          report
              .validPlan()
              .orElseThrow(() ->
                  new IllegalStateException(
                      "Seed file "
                          + file.getFilename()
                          + " is not valid: "
                          + report.violations().stream()
                              .map(v -> v.code() + " at " + v.path())
                              .collect(Collectors.joining(", "))));
      Insurer insurer = insurerFor(plan);
      if (alreadyThere(insurer, plan)) {
        continue;
      }
      UUID planId =
          importer
              .importPlan(StaffScope.platform(), insurer.getId(), bytes, false, null)
              .plan()
              .id();
      lifecycle.publish(StaffScope.platform(), planId, null);
      published++;
    }
    LOG.info("Catalogue seed finished: {} plan versions published.", published);
    return published;
  }

  private Insurer insurerFor(PlanDocument plan) {
    return insurers
        .findByCode(plan.insurer().code())
        .orElseGet(() ->
            insurers.save(
                Insurer.create(plan.insurer().code(), plan.insurer().name(), clock.instant())));
  }

  private boolean alreadyThere(Insurer insurer, PlanDocument plan) {
    return products
        .findByInsurerIdAndCode(insurer.getId(), plan.product().code())
        .flatMap(product ->
            plans.findByProductIdAndCodeAndPlanVersion(
                product.getId(), plan.plan().code(), plan.plan().version()))
        .isPresent();
  }

  /** Sorted by path, so version 1 of a plan is always seeded before version 2. */
  static Resource[] seedFiles() {
    try {
      Resource[] files = new PathMatchingResourcePatternResolver().getResources(LOCATION);
      Arrays.sort(files, Comparator.comparing(CatalogueSeeder::pathOf));
      return files;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String pathOf(Resource resource) {
    try {
      return resource.getURI().toString();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static byte[] read(Resource file) {
    try (InputStream in = file.getInputStream()) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
