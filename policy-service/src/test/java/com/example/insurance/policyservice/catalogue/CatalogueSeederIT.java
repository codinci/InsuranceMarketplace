package com.example.insurance.policyservice.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.insurance.policyservice.AbstractPolicyIT;
import com.example.insurance.policyservice.catalogue.service.CatalogueSeeder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/** The seed loads both fictional insurers with two plans each, and is safe to run again. */
class CatalogueSeederIT extends AbstractPolicyIT {

  @Autowired CatalogueSeeder seeder;
  @Autowired JdbcTemplate jdbc;

  @Test
  void seedsTwoPlansPerInsurerAndCanRunAgain() throws Exception {
    seeder.seed();
    int second = seeder.seed();

    assertThat(second).isZero();
    Integer rows =
        jdbc.queryForObject(
            "select count(*) from plans p join insurers i on i.id = p.insurer_id"
                + " where i.code in ('ACACIA', 'BAOBAB') and p.status = 'PUBLISHED'",
            Integer.class);
    assertThat(rows).isEqualTo(4);
    for (String insurer : List.of("ACACIA", "BAOBAB")) {
      MvcResult plans = mockMvc.perform(get("/api/v1/insurers/" + insurer + "/plans")).andReturn();
      assertThat((Integer) read(plans, "$.totalElements")).as(insurer).isEqualTo(2);
    }
  }
}
