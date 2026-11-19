package com.example.insurance.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RolesTest {

  @Test
  void roleNamesMatchThePlanExactly() {
    assertThat(Roles.CUSTOMER).isEqualTo("CUSTOMER");
    assertThat(Roles.ADJUSTER).isEqualTo("ADJUSTER");
    assertThat(Roles.ADMIN).isEqualTo("ADMIN");
    assertThat(Roles.SERVICE).isEqualTo("SERVICE");
  }
}
