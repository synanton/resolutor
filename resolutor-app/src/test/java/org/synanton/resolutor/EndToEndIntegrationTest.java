package org.synanton.resolutor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.synanton.resolutor.application.port.in.NewTaskCommand;
import org.synanton.resolutor.application.port.in.PlanQueryPort;
import org.synanton.resolutor.application.port.in.TaskIngestionPort;
import org.synanton.resolutor.application.port.out.ResourceGraphPort;
import org.synanton.resolutor.application.port.out.TaskRepositoryPort;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end integration test: boots the full Spring context against a real Postgres and asserts
 * that an ingested task is planned, dispatched, and completed by the scheduled runtime loop.
 *
 * <p>The upstream resource-graph service is stubbed via a Spring bean so we don't need to run a
 * WireMock server; this keeps the test focused on Resolutor's own runtime plumbing.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class EndToEndIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void tightenScheduling(DynamicPropertyRegistry registry) {
    registry.add("resolutor.planner.tick-interval", () -> "PT0.05S");
  }

  @Autowired TaskIngestionPort ingestion;
  @Autowired PlanQueryPort planQuery;
  @Autowired TaskRepositoryPort taskRepo;

  @org.springframework.boot.test.context.TestConfiguration
  static class StubResourceGraph {
    @Bean
    @Primary
    ResourceGraphPort stubResourceGraphPort() {
      return (Task task) -> Set.of(task.topResource());
    }
  }

  @Test
  void ingestedTaskGetsPlannedAndCompleted() {
    TaskId id =
        ingestion.ingest(
            new NewTaskCommand(Resource.of("project", "7"), "{}", "{}", (Instant) null));

    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(() -> assertThat(planQuery.latestPlan()).isPresent());

    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () ->
                assertThat(taskRepo.findById(id))
                    .get()
                    .extracting(Task::state)
                    .isEqualTo(TaskState.COMPLETED));
  }
}
