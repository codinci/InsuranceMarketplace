package com.example.insurance.policyservice.catalogue.domain;

import java.util.List;

/** A module of a plan, such as INPATIENT or OUTPATIENT, with its options. */
public record ModuleDef(String code, boolean mandatory, List<ModuleOption> options) {

  public ModuleDef {
    options = options == null ? List.of() : List.copyOf(options);
  }
}
