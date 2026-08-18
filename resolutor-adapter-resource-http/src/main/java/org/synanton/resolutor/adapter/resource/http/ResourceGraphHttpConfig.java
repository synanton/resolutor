package org.synanton.resolutor.adapter.resource.http;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.synanton.resolutor.application.port.out.ResourceGraphPort;

/** Wires the HTTP {@link ResourceGraphPort} adapter into the Spring context. */
@Configuration
@Profile("!compose")
@EnableConfigurationProperties(ResourceGraphHttpProperties.class)
public class ResourceGraphHttpConfig {

  @Bean
  RestClient resourceGraphRestClient(ResourceGraphHttpProperties props) {
    Duration timeout = props.timeoutOrDefault();
    HttpClient http =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(timeout);
    return RestClient.builder().requestFactory(factory).build();
  }

  @Bean
  @ConditionalOnMissingBean(ResourceGraphPort.class)
  ResourceGraphPort resourceGraphPort(
      RestClient resourceGraphRestClient, ResourceGraphHttpProperties props) {
    return new ResourceGraphHttpAdapter(resourceGraphRestClient, props.endpointOrDefault());
  }
}
