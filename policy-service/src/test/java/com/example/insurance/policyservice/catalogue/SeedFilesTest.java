package com.example.insurance.policyservice.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.policyservice.catalogue.rules.PlanValidator;
import com.example.insurance.policyservice.catalogue.rules.ValidationReport;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.JsonNode;

/**
 * PLAN 5.4 step 3: every seed file passes both validation layers and is priced by at least one
 * golden case in {@code seed-premiums.json}. A new seed file without an expected premium fails
 * here.
 */
class SeedFilesTest {

  private final PlanValidator validator = CatalogueFixtures.validator();

  private static List<String> seedPaths() throws IOException {
    Resource[] files =
        new PathMatchingResourcePatternResolver()
            .getResources("classpath*:catalogue-seed/**/*.json");
    return Arrays.stream(files)
        .map(f -> {
          try {
            String uri = f.getURI().toString();
            return uri.substring(uri.indexOf("catalogue-seed/"));
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        })
        .sorted()
        .toList();
  }

  @Test
  void thereAreTwoPlansForEachOfTwoInsurers() throws IOException {
    assertThat(seedPaths())
        .containsExactly(
            "catalogue-seed/acacia/premier.v1.json",
            "catalogue-seed/acacia/standard.v1.json",
            "catalogue-seed/baobab/family.v1.json",
            "catalogue-seed/baobab/lite.v1.json");
  }

  @Test
  void everySeedFileIsValid() throws IOException {
    for (String path : seedPaths()) {
      ValidationReport report = validator.validate(CatalogueFixtures.classpath("/" + path));
      assertThat(report.violations()).as(path).isEmpty();
    }
  }

  @Test
  void everySeedFileHasAnExpectedPremium() throws IOException {
    JsonNode cases =
        CatalogueFixtures.MAPPER.readTree(CatalogueFixtures.resource("seed-premiums.json"));
    Set<String> priced =
        cases.valueStream().map(c -> c.get("plan").asString()).collect(Collectors.toSet());
    assertThat(priced).containsAll(seedPaths());
  }
}
