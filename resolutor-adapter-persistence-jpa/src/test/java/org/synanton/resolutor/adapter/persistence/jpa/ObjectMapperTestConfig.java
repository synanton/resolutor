package org.synanton.resolutor.adapter.persistence.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class ObjectMapperTestConfig {

  @Bean
  ObjectMapper objectMapper() {
    return new ObjectMapper();
  }
}
