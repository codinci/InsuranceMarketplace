package com.example.insurance.policyservice.catalogue.rules;

import com.example.insurance.policyservice.catalogue.domain.PlanDocument;
import com.example.insurance.policyservice.catalogue.domain.PlanJson;
import com.example.insurance.policyservice.catalogue.domain.Violation;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validates a plan document in two layers (PLAN 5.1): the JSON Schema first, then the semantic
 * rules. Semantic rules only run on a document that passed the schema.
 */
@Component
public class PlanValidator {

  public static final String MALFORMED_JSON = "MALFORMED_JSON";
  public static final String PLAN_NOT_READABLE = "PLAN_NOT_READABLE";

  private final PlanSchemaValidator schema;
  private final PlanSemanticValidator semantics;
  private final JsonMapper mapper = PlanJson.newMapper();

  public PlanValidator(PlanSchemaValidator schema, PlanSemanticValidator semantics) {
    this.schema = schema;
    this.semantics = semantics;
  }

  public ValidationReport validate(String json) {
    JsonNode document;
    try {
      document = mapper.readTree(json);
    } catch (JacksonException e) {
      return ValidationReport.invalid(
          List.of(new Violation(MALFORMED_JSON, "", "The document is not valid JSON.")));
    }
    return validate(document);
  }

  public ValidationReport validate(JsonNode document) {
    List<Violation> schemaViolations = schema.validate(document);
    if (!schemaViolations.isEmpty()) {
      return ValidationReport.invalid(schemaViolations);
    }
    PlanDocument plan;
    try {
      plan = mapper.treeToValue(document, PlanDocument.class);
    } catch (JacksonException e) {
      return ValidationReport.invalid(
          List.of(
              new Violation(
                  PLAN_NOT_READABLE,
                  "",
                  "The document matches the schema but a value cannot be read, for example a"
                      + " date that does not exist.")));
    }
    List<Violation> violations = semantics.validate(plan);
    return violations.isEmpty()
        ? new ValidationReport(List.of(), plan)
        : ValidationReport.invalid(violations);
  }
}
