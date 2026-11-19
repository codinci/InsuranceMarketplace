package com.example.insurance.hello;

import com.example.insurance.common.Roles;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hello")
public class HelloController {

  private static final Logger log = LoggerFactory.getLogger(HelloController.class);

  @GetMapping
  public Map<String, String> hello() {
    // No request data is logged. The correlation id reaches the log line through the MDC.
    log.info("hello request served");
    return Map.of("service", "hello-service", "customerRole", Roles.CUSTOMER);
  }
}
