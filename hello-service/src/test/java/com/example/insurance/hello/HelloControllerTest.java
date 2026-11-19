package com.example.insurance.hello;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HelloController.class)
class HelloControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void returnsServiceNameAndRole() throws Exception {
    mockMvc
        .perform(get("/api/v1/hello"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.service").value("hello-service"))
        .andExpect(jsonPath("$.customerRole").value("CUSTOMER"));
  }
}
