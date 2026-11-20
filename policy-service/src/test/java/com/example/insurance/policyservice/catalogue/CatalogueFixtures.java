package com.example.insurance.policyservice.catalogue;

import com.example.insurance.policyservice.catalogue.domain.PlanDefinition;
import com.example.insurance.policyservice.catalogue.domain.PlanDocument;
import com.example.insurance.policyservice.catalogue.domain.PlanJson;
import com.example.insurance.policyservice.catalogue.rating.AgeBandPerMemberStrategy;
import com.example.insurance.policyservice.catalogue.rating.FamilySizeTierStrategy;
import com.example.insurance.policyservice.catalogue.rating.FlatPerEligibleMemberStrategy;
import com.example.insurance.policyservice.catalogue.rating.RatingStrategyRegistry;
import com.example.insurance.policyservice.catalogue.rules.PlanSchemaValidator;
import com.example.insurance.policyservice.catalogue.rules.PlanSemanticValidator;
import com.example.insurance.policyservice.catalogue.rules.PlanValidator;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import tools.jackson.core.JsonPointer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Loads catalogue test resources and builds the engine without Spring. */
public final class CatalogueFixtures {

  public static final JsonMapper MAPPER = PlanJson.newMapper();

  private CatalogueFixtures() {}

  public static String resource(String path) {
    try (InputStream in = CatalogueFixtures.class.getResourceAsStream("/catalogue/" + path)) {
      if (in == null) {
        throw new IllegalArgumentException("No test resource catalogue/" + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static JsonNode validPlanJson(String file) {
    return MAPPER.readTree(resource("valid/" + file));
  }

  public static PlanDefinition validPlan(String file) {
    return MAPPER.treeToValue(validPlanJson(file), PlanDocument.class).plan();
  }

  public static RatingStrategyRegistry registry() {
    return new RatingStrategyRegistry(
        List.of(
            new AgeBandPerMemberStrategy(),
            new FamilySizeTierStrategy(),
            new FlatPerEligibleMemberStrategy()));
  }

  public static PlanValidator validator() {
    return new PlanValidator(new PlanSchemaValidator(), new PlanSemanticValidator(registry()));
  }

  /** Sets or replaces the value at a JSON Pointer. The parent must exist. */
  public static void set(JsonNode root, String pointer, JsonNode value) {
    JsonPointer path = JsonPointer.compile(pointer);
    JsonNode parent = root.at(path.head());
    String last = path.last().getMatchingProperty();
    if (parent instanceof ArrayNode array) {
      array.set(Integer.parseInt(last), value);
    } else {
      ((ObjectNode) parent).set(last, value);
    }
  }

  /** Removes the value at a JSON Pointer. */
  public static void remove(JsonNode root, String pointer) {
    JsonPointer path = JsonPointer.compile(pointer);
    JsonNode parent = root.at(path.head());
    String last = path.last().getMatchingProperty();
    if (parent instanceof ArrayNode array) {
      array.remove(Integer.parseInt(last));
    } else {
      ((ObjectNode) parent).remove(last);
    }
  }
}
