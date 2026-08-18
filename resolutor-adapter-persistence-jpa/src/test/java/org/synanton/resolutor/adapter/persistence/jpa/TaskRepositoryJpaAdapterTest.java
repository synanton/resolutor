package org.synanton.resolutor.adapter.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Cursor;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TaskEntityMapper.class, TaskRepositoryJpaAdapter.class, ObjectMapperTestConfig.class})
class TaskRepositoryJpaAdapterTest extends AbstractPostgresIntegrationTest {

  @Autowired TaskJpaRepository jpa;
  @Autowired TaskEntityMapper mapper;
  @Autowired ObjectMapper json;

  @Test
  void saveThenFindByIdRoundTrips() {
    var adapter = new TaskRepositoryJpaAdapter(jpa, mapper);
    Task task = pending("project", "42");

    adapter.save(task);
    var found = adapter.findById(task.id());

    assertThat(found).isPresent();
    assertThat(found.get().state()).isEqualTo(TaskState.PENDING);
    assertThat(found.get().topResource()).isEqualTo(task.topResource());
  }

  @Test
  void resolvedResourcesRoundTripThroughJsonb() {
    var adapter = new TaskRepositoryJpaAdapter(jpa, mapper);
    Task base = pending("project", "1");
    Set<Resource> resolved =
        Set.of(Resource.of("project", "1"), Resource.of("talk", "9"), Resource.of("room", "3"));
    Task task = base.withResolvedResources(resolved);

    adapter.save(task);
    var found = adapter.findById(task.id());

    assertThat(found).isPresent();
    assertThat(found.get().resolvedResources()).isEqualTo(resolved);
  }

  @Test
  void cursorRoundTripsWhenPresent() {
    var adapter = new TaskRepositoryJpaAdapter(jpa, mapper);
    Task task = pending("project", "5").withCursor(new Cursor("cursor:page:42"));

    adapter.save(task);
    var found = adapter.findById(task.id());

    assertThat(found).isPresent();
    assertThat(found.get().cursor()).isNotNull();
    assertThat(found.get().cursor().value()).isEqualTo("cursor:page:42");
  }

  @Test
  void loadBatchForPlanningReturnsOnlyPendingAndPaused() {
    var adapter = new TaskRepositoryJpaAdapter(jpa, mapper);
    Task pending = pending("project", "a");
    Task paused = pending("project", "b").withState(TaskState.PAUSED);
    Task completed = pending("project", "c").withState(TaskState.COMPLETED);

    adapter.save(pending);
    adapter.save(paused);
    adapter.save(completed);

    List<Task> loaded = adapter.loadBatchForPlanning(10);

    assertThat(loaded).extracting(Task::id).containsExactlyInAnyOrder(pending.id(), paused.id());
  }

  @Test
  void loadBatchForPlanningOrdersByCreatedAtThenId() {
    var adapter = new TaskRepositoryJpaAdapter(jpa, mapper);
    Instant t0 = Instant.parse("2026-08-01T00:00:00Z");
    Task first = pendingAt(t0);
    Task second = pendingAt(t0.plusSeconds(1));
    Task third = pendingAt(t0.plusSeconds(2));

    adapter.save(third);
    adapter.save(first);
    adapter.save(second);

    List<Task> loaded = adapter.loadBatchForPlanning(10);

    assertThat(loaded)
        .extracting(t -> t.createdAt().getEpochSecond())
        .containsExactly(
            t0.getEpochSecond(),
            t0.plusSeconds(1).getEpochSecond(),
            t0.plusSeconds(2).getEpochSecond());
  }

  @Test
  void loadBatchForPlanningRespectsLimit() {
    var adapter = new TaskRepositoryJpaAdapter(jpa, mapper);
    for (int i = 0; i < 5; i++) {
      adapter.save(pending("project", "id-" + i));
    }
    assertThat(adapter.loadBatchForPlanning(3)).hasSize(3);
  }

  @Test
  void saveUpdatesExistingRow() {
    var adapter = new TaskRepositoryJpaAdapter(jpa, mapper);
    Task task = pending("project", "42");
    adapter.save(task);
    adapter.save(task.withState(TaskState.STARTED));

    var found = adapter.findById(task.id());
    assertThat(found).isPresent();
    assertThat(found.get().state()).isEqualTo(TaskState.STARTED);
    assertThat(jpa.count()).isEqualTo(1);
  }

  @Test
  void loadExpiredReturnsOnlyElapsedTimeoutsInGivenStates() {
    var adapter = new TaskRepositoryJpaAdapter(jpa, mapper);
    Instant now = Instant.parse("2026-08-14T12:00:00Z");
    Task expired = pendingAt(now.minusSeconds(10));
    expired =
        new Task(
            expired.id(),
            expired.topResource(),
            expired.resolvedResources(),
            expired.searchDsl(),
            expired.payload(),
            expired.cursor(),
            TaskState.PENDING,
            expired.createdAt(),
            now.minusSeconds(1),
            expired.version());
    Task live =
        new Task(
            TaskId.generate(),
            Resource.of("project", "y"),
            Set.of(),
            "{}",
            "{}",
            null,
            TaskState.PENDING,
            now.minusSeconds(10),
            now.plusSeconds(60),
            0L);
    adapter.save(expired);
    adapter.save(live);

    List<Task> loaded = adapter.loadExpired(now, Set.of(TaskState.PENDING, TaskState.STARTED));

    assertThat(loaded).extracting(Task::id).containsExactly(expired.id());
  }

  @Test
  void loadByStatesFiltersOnState() {
    var adapter = new TaskRepositoryJpaAdapter(jpa, mapper);
    Task started = pending("project", "s").withState(TaskState.STARTED);
    Task pending = pending("project", "p");
    adapter.save(started);
    adapter.save(pending);

    List<Task> loaded = adapter.loadByStates(Set.of(TaskState.STARTED));

    assertThat(loaded).extracting(Task::id).containsExactly(started.id());
  }

  @Test
  void countByStateGroupsLiveRows() {
    var adapter = new TaskRepositoryJpaAdapter(jpa, mapper);
    adapter.save(pending("project", "p"));
    adapter.save(pending("project", "s").withState(TaskState.STARTED));
    adapter.save(pending("project", "c").withState(TaskState.COMPLETED));

    assertThat(adapter.countByState())
        .containsEntry(TaskState.PENDING, 1L)
        .containsEntry(TaskState.STARTED, 1L)
        .containsEntry(TaskState.COMPLETED, 1L);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static Task pending(String cls, String id) {
    return new Task(
        TaskId.generate(),
        Resource.of(cls, id),
        Set.of(),
        "{\"q\":\"\"}",
        "{\"data\":true}",
        null,
        TaskState.PENDING,
        Instant.now(),
        null,
        0L);
  }

  private static Task pendingAt(Instant at) {
    return new Task(
        TaskId.generate(),
        Resource.of("project", "x"),
        Set.of(),
        "{}",
        "{}",
        null,
        TaskState.PENDING,
        at,
        null,
        0L);
  }
}
