package org.synanton.resolutor.application.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.application.fake.InMemoryTaskRepository;
import org.synanton.resolutor.application.fake.NoOpMetricsPort;
import org.synanton.resolutor.application.port.in.NewTaskCommand;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

class TaskIngestionServiceTest {

  private InMemoryTaskRepository taskRepo;
  private TaskIngestionService service;

  @BeforeEach
  void setUp() {
    taskRepo = new InMemoryTaskRepository();
    service =
        new TaskIngestionService(
            taskRepo,
            new NoOpMetricsPort(),
            Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void ingestPersistsTaskInPendingState() {
    NewTaskCommand cmd = new NewTaskCommand(Resource.of("project", "42"), "{}", "{}", null);

    TaskId id = service.ingest(cmd);

    assertThat(id).isNotNull();
    var saved = taskRepo.findById(id);
    assertThat(saved).isPresent();
    assertThat(saved.get().state()).isEqualTo(TaskState.PENDING);
  }

  @Test
  void ingestSetsTopResource() {
    Resource resource = Resource.of("room", "7");
    NewTaskCommand cmd = new NewTaskCommand(resource, "{}", "{}", null);

    TaskId id = service.ingest(cmd);

    assertThat(taskRepo.findById(id).get().topResource()).isEqualTo(resource);
  }

  @Test
  void ingestSetsVersionToZero() {
    NewTaskCommand cmd = new NewTaskCommand(Resource.of("project", "1"), "{}", "{}", null);

    TaskId id = service.ingest(cmd);

    assertThat(taskRepo.findById(id).get().version()).isZero();
  }

  @Test
  void ingestReturnsUniqueIdsPerCall() {
    NewTaskCommand cmd = new NewTaskCommand(Resource.of("project", "1"), "{}", "{}", null);

    TaskId id1 = service.ingest(cmd);
    TaskId id2 = service.ingest(cmd);

    assertThat(id1).isNotEqualTo(id2);
  }

  @Test
  void ingestSetsEmptyResolvedResources() {
    NewTaskCommand cmd = new NewTaskCommand(Resource.of("project", "1"), "{}", "{}", null);

    TaskId id = service.ingest(cmd);

    assertThat(taskRepo.findById(id).get().resolvedResources()).isEmpty();
  }
}
