package org.synanton.resolutor.adapter.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.synanton.resolutor.application.port.out.ProgressSnapshot;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TaskEntityMapper.class, TaskProgressEntityMapper.class, ObjectMapperTestConfig.class})
class ProgressRepositoryJpaAdapterTest extends AbstractPostgresIntegrationTest {

  @Autowired TaskJpaRepository taskJpa;
  @Autowired TaskProgressJpaRepository progressJpa;
  @Autowired TaskEntityMapper taskMapper;
  @Autowired TaskProgressEntityMapper progressMapper;

  @Test
  void saveAndFindRoundTrip() {
    var taskAdapter = new TaskRepositoryJpaAdapter(taskJpa, taskMapper);
    var adapter = new ProgressRepositoryJpaAdapter(progressJpa, taskJpa, progressMapper);
    Task task = pending("project", "42");
    taskAdapter.save(task);

    ProgressSnapshot snap = new ProgressSnapshot(task.id(), 10L, 8L, 2L, 0L);
    adapter.save(snap);

    var found = adapter.findByTaskId(task.id());
    assertThat(found).isPresent();
    assertThat(found.get().totalCount()).isEqualTo(10);
    assertThat(found.get().successCount()).isEqualTo(8);
    assertThat(found.get().failedCount()).isEqualTo(2);
  }

  @Test
  void findByUnknownTaskIdReturnsEmpty() {
    var adapter = new ProgressRepositoryJpaAdapter(progressJpa, taskJpa, progressMapper);
    assertThat(adapter.findByTaskId(TaskId.generate())).isEmpty();
  }

  @Test
  void saveOverwritesExistingSnapshot() {
    var taskAdapter = new TaskRepositoryJpaAdapter(taskJpa, taskMapper);
    var adapter = new ProgressRepositoryJpaAdapter(progressJpa, taskJpa, progressMapper);
    Task task = pending("project", "5");
    taskAdapter.save(task);

    adapter.save(new ProgressSnapshot(task.id(), 3L, 3L, 0L, 0L));
    adapter.save(new ProgressSnapshot(task.id(), 7L, 5L, 2L, 1L));

    var found = adapter.findByTaskId(task.id()).orElseThrow();
    assertThat(found.totalCount()).isEqualTo(7);
    assertThat(found.successCount()).isEqualTo(5);
    assertThat(found.failedCount()).isEqualTo(2);
  }

  @Test
  void inflightCountsGroupInflightStatesByResourceClass() {
    var taskAdapter = new TaskRepositoryJpaAdapter(taskJpa, taskMapper);
    var adapter = new ProgressRepositoryJpaAdapter(progressJpa, taskJpa, progressMapper);

    taskAdapter.save(pending("project", "a").withState(TaskState.STARTED));
    taskAdapter.save(pending("project", "b").withState(TaskState.PROCESSING));
    taskAdapter.save(pending("project", "c")); // PENDING, not in-flight
    taskAdapter.save(pending("talk", "t1").withState(TaskState.STARTED));
    taskAdapter.save(pending("talk", "t2").withState(TaskState.COMPLETED)); // not in-flight

    var counts = adapter.inflightCountsByResourceClass();

    assertThat(counts).containsEntry("project", 2L).containsEntry("talk", 1L);
  }

  private static Task pending(String cls, String id) {
    return new Task(
        TaskId.generate(),
        Resource.of(cls, id),
        Set.of(),
        "{}",
        "{}",
        null,
        TaskState.PENDING,
        Instant.now(),
        null,
        0L);
  }
}
