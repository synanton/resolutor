package org.synanton.resolutor.adapter.persistence.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.synanton.resolutor.application.port.out.LeadershipPort;
import org.synanton.resolutor.application.port.out.PlanPublisherPort;
import org.synanton.resolutor.application.port.out.ProgressRepositoryPort;
import org.synanton.resolutor.application.port.out.TaskRepositoryPort;

/** Wires the JPA persistence adapter into the Spring context. */
@Configuration
@EnableJpaRepositories(basePackageClasses = TaskJpaRepository.class)
@EntityScan(basePackageClasses = TaskEntity.class)
public class PersistenceJpaConfig {

  @Bean
  TaskEntityMapper taskEntityMapper(ObjectMapper json) {
    return new TaskEntityMapper(json);
  }

  @Bean
  TaskProgressEntityMapper taskProgressEntityMapper() {
    return new TaskProgressEntityMapper();
  }

  @Bean
  ExecutionPlanJsonMapper executionPlanJsonMapper(ObjectMapper json) {
    return new ExecutionPlanJsonMapper(json);
  }

  @Bean
  TaskRepositoryPort taskRepositoryPort(TaskJpaRepository jpa, TaskEntityMapper mapper) {
    return new TaskRepositoryJpaAdapter(jpa, mapper);
  }

  @Bean
  ProgressRepositoryPort progressRepositoryPort(
      TaskProgressJpaRepository progressJpa,
      TaskJpaRepository taskJpa,
      TaskProgressEntityMapper mapper) {
    return new ProgressRepositoryJpaAdapter(progressJpa, taskJpa, mapper);
  }

  @Bean
  PlanPublisherPort planPublisherPort(
      ExecutionPlanLatestJpaRepository latestRepo,
      ExecutionPlanJpaRepository historyRepo,
      ExecutionPlanJsonMapper mapper) {
    return new PlanPublisherJpaAdapter(latestRepo, historyRepo, mapper);
  }

  @Bean
  @ConditionalOnMissingBean
  LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new org.springframework.jdbc.core.JdbcTemplate(dataSource))
            .usingDbTime()
            .build());
  }

  @Bean
  LeadershipPort leadershipPort(LockProvider lockProvider) {
    return new ShedLockLeadershipAdapter(lockProvider);
  }
}
