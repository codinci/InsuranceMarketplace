package com.example.insurance.policyservice.catalogue.service;

import com.example.insurance.policyservice.catalogue.domain.PlanDocument;
import com.example.insurance.policyservice.catalogue.domain.PlanJson;
import com.example.insurance.policyservice.catalogue.domain.PlanVersion;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the document stored with a plan version. It was validated on import, so reading it back
 * cannot fail unless the schema changed incompatibly.
 */
@Component
public class StoredPlans {

  private final JsonMapper mapper = PlanJson.newMapper();

  public PlanDocument document(PlanVersion plan) {
    return mapper.readValue(plan.getDefinition(), PlanDocument.class);
  }
}
