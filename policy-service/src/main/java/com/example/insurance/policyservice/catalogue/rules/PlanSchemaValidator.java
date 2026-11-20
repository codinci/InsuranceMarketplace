package com.example.insurance.policyservice.catalogue.rules;

import com.example.insurance.policyservice.catalogue.domain.Violation;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.NodePath;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Checks a plan document against {@code catalogue/plan-schema.v1.json} (JSON Schema 2020-12). */
@Component
public final class PlanSchemaValidator {

  public static final String SCHEMA_VIOLATION = "SCHEMA_VIOLATION";
  static final String SCHEMA_RESOURCE = "/catalogue/plan-schema.v1.json";

  private final Schema schema;

  public PlanSchemaValidator() {
    try (InputStream in = PlanSchemaValidator.class.getResourceAsStream(SCHEMA_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing schema resource " + SCHEMA_RESOURCE);
      }
      schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public List<Violation> validate(JsonNode document) {
    return schema.validate(document).stream().map(PlanSchemaValidator::toViolation).toList();
  }

  private static Violation toViolation(Error error) {
    StringBuilder pointer = new StringBuilder(pointerOf(error.getInstanceLocation()));
    if (error.getProperty() != null
        && ("required".equals(error.getKeyword())
            || "additionalProperties".equals(error.getKeyword()))) {
      pointer.append('/').append(escape(error.getProperty()));
    }
    return new Violation(SCHEMA_VIOLATION, pointer.toString(), error.getMessage());
  }

  private static String pointerOf(NodePath path) {
    StringBuilder pointer = new StringBuilder();
    for (int i = 0; i < path.getNameCount(); i++) {
      pointer.append('/').append(escape(String.valueOf(path.getElement(i))));
    }
    return pointer.toString();
  }

  /** JSON Pointer escaping (RFC 6901). */
  private static String escape(String token) {
    return token.replace("~", "~0").replace("/", "~1");
  }
}
