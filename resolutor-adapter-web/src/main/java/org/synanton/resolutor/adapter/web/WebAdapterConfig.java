package org.synanton.resolutor.adapter.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the web adapter into the Spring context and configures OpenAPI metadata. */
@Configuration
public class WebAdapterConfig {

  /** Register the correlation-id filter ahead of the servlet chain. */
  @Bean
  FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
    FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new CorrelationIdFilter());
    registration.setOrder(Integer.MIN_VALUE + 100);
    registration.addUrlPatterns("/*");
    return registration;
  }

  @Bean
  OpenAPI resolutorOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Resolutor API")
                .version("v1")
                .description(
                    "Compiler-inspired execution planning. See docs/design.md for the model.")
                .license(
                    new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0")));
  }
}
