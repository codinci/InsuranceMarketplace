package com.example.insurance.hello;

import com.example.insurance.common.Roles;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hello")
public class HelloController {

  @GetMapping
  public Map<String, String> hello() {
    return Map.of("service", "hello-service", "customerRole", Roles.CUSTOMER);
  }
}
