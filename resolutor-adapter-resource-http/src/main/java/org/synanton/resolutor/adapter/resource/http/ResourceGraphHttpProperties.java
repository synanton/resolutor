package org.synanton.resolutor.adapter.resource.http;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the HTTP {@link org.synanton.resolutor.application.port.out.ResourceGraphPort}.
 */
@ConfigurationProperties(prefix = "resolutor.resource-graph")
public record ResourceGraphHttpProperties(@Nullable String endpoint, @Nullable Duration timeout) {

  public String endpointOrDefault() {
    return endpoint == null ? "http://localhost:9000/resources" : endpoint;
  }

  public Duration timeoutOrDefault() {
    return timeout == null ? Duration.ofSeconds(2) : timeout;
  }
}
