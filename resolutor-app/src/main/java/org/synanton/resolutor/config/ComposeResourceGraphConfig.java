package org.synanton.resolutor.config;

import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.synanton.resolutor.application.port.out.ResourceGraphPort;
import org.synanton.resolutor.domain.task.Task;

/**
 * Local compose/dev footprint: each task touches only its top resource so a fresh {@code docker
 * compose up} works without an external graph service.
 */
@Configuration
@Profile("compose")
public class ComposeResourceGraphConfig {

  @Bean
  ResourceGraphPort composeResourceGraphPort() {
    return (Task task) -> Set.of(task.topResource());
  }
}
