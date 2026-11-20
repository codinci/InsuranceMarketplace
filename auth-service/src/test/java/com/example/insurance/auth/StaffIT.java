package com.example.insurance.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Staff creation and insurer isolation (PLAN 4, CLAUDE.md: every staff endpoint has a cross-insurer
 * test).
 */
@SpringBootTest
@AutoConfigureMockMvc
class StaffIT extends AbstractAuthIT {

  private static final UUID INSURER_A = UUID.randomUUID();
  private static final UUID INSURER_B = UUID.randomUUID();

  @Test
  void aPlatformAdminCreatesAnInsurerAdminWhoCanThenCreateAnAdjusterForTheirOwnInsurer()
      throws Exception {
    String insurerAdminEmail = uniqueEmail("admin.a");
    MvcResult created =
        createStaff(platformAdminToken(), insurerAdminEmail, "ADMIN", INSURER_A.toString());
    assertThat(created.getResponse().getStatus()).isEqualTo(201);

    String insurerAdminToken = accessTokenOf(login(insurerAdminEmail, PASSWORD));
    MvcResult adjuster =
        createStaff(insurerAdminToken, uniqueEmail("adj.a"), "ADJUSTER", INSURER_A.toString());

    assertThat(adjuster.getResponse().getStatus()).isEqualTo(201);
    assertThat(JsonPath.<String>read(adjuster.getResponse().getContentAsString(), "$.insurerId"))
        .isEqualTo(INSURER_A.toString());
  }

  @Test
  void anInsurerAdminCannotCreateStaffForAnotherInsurer() throws Exception {
    String adminA = uniqueEmail("iso.a");
    createStaff(platformAdminToken(), adminA, "ADMIN", INSURER_A.toString());
    String tokenA = accessTokenOf(login(adminA, PASSWORD));

    MvcResult crossInsurer =
        createStaff(tokenA, uniqueEmail("iso.b.adj"), "ADJUSTER", INSURER_B.toString());

    assertThat(crossInsurer.getResponse().getStatus()).isEqualTo(403);
    assertThat(code(crossInsurer)).isEqualTo("INSURER_SCOPE_VIOLATION");
  }

  @Test
  void anInsurerAdminCannotCreateAPlatformAdmin() throws Exception {
    String adminA = uniqueEmail("pa.a");
    createStaff(platformAdminToken(), adminA, "ADMIN", INSURER_A.toString());
    String tokenA = accessTokenOf(login(adminA, PASSWORD));

    MvcResult platform = createStaff(tokenA, uniqueEmail("pa.new"), "ADMIN", null);

    assertThat(platform.getResponse().getStatus()).isEqualTo(403);
    assertThat(code(platform)).isEqualTo("INSURER_SCOPE_VIOLATION");
  }

  @Test
  void anAdjusterCannotCreateStaffAtAll() throws Exception {
    String adjusterEmail = uniqueEmail("only.adj");
    createStaff(platformAdminToken(), adjusterEmail, "ADJUSTER", INSURER_B.toString());
    String adjusterToken = accessTokenOf(login(adjusterEmail, PASSWORD));

    MvcResult result =
        createStaff(adjusterToken, uniqueEmail("nope"), "ADJUSTER", INSURER_B.toString());

    assertThat(result.getResponse().getStatus()).isEqualTo(403);
    assertThat(code(result)).isEqualTo("FORBIDDEN");
  }

  @Test
  void aCustomerCannotCreateStaff() throws Exception {
    String customerToken = accessTokenOf(registeredCustomerLogin(uniqueEmail("cust.staff")));

    MvcResult result =
        createStaff(customerToken, uniqueEmail("nope2"), "ADJUSTER", INSURER_A.toString());

    assertThat(result.getResponse().getStatus()).isEqualTo(403);
  }

  @Test
  void anAnonymousCallerIsUnauthorised() throws Exception {
    MvcResult result = createStaff(null, uniqueEmail("anon"), "ADJUSTER", INSURER_A.toString());

    assertThat(result.getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  void anAdjusterMustHaveAnInsurer() throws Exception {
    MvcResult result =
        createStaff(platformAdminToken(), uniqueEmail("no.insurer"), "ADJUSTER", null);

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(code(result)).isEqualTo("INSURER_REQUIRED");
  }

  @Test
  void theRequestCannotAskForACustomerOrAnyRoleOtherThanStaffRoles() throws Exception {
    MvcResult result =
        createStaff(
            platformAdminToken(), uniqueEmail("customer.role"), "CUSTOMER", INSURER_A.toString());

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(code(result)).isEqualTo("VALIDATION_FAILED");
  }

  @Test
  void aStaffEmailCannotBeReused() throws Exception {
    String email = uniqueEmail("dup.staff");
    createStaff(platformAdminToken(), email, "ADJUSTER", INSURER_A.toString());

    MvcResult again = createStaff(platformAdminToken(), email, "ADJUSTER", INSURER_A.toString());

    assertThat(again.getResponse().getStatus()).isEqualTo(409);
    assertThat(code(again)).isEqualTo("EMAIL_TAKEN");
  }

  @Test
  void theSeededPlatformAdminCanSignInAndHasNoInsurer() throws Exception {
    MvcResult login = login(PLATFORM_ADMIN_EMAIL, PLATFORM_ADMIN_PASSWORD);

    assertThat(login.getResponse().getStatus())
        .as(login.getResponse().getContentAsString())
        .isEqualTo(200);
    MvcResult me =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/v1/auth/me")
                    .header("Authorization", "Bearer " + accessTokenOf(login)))
            .andReturn();
    assertThat(JsonPath.<Object>read(me.getResponse().getContentAsString(), "$.insurerId"))
        .isNull();
    assertThat(
            JsonPath.<java.util.List<String>>read(me.getResponse().getContentAsString(), "$.roles"))
        .containsExactly("ADMIN");
  }

  private MvcResult createStaff(String bearer, String email, String role, String insurerId)
      throws Exception {
    String insurer = insurerId == null ? "null" : "\"" + insurerId + "\"";
    String body =
        "{\"email\":\""
            + email
            + "\",\"password\":\""
            + PASSWORD
            + "\",\"firstName\":\"Staff\",\"lastName\":\"Member\","
            + "\"role\":\""
            + role
            + "\",\"insurerId\":"
            + insurer
            + "}";
    return postJson("/api/v1/admin/users", body, bearer);
  }

  private static String code(MvcResult result) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), "$.code");
  }
}
