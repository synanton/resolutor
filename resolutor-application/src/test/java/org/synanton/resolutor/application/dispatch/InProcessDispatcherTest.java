package org.synanton.resolutor.application.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.application.backpressure.BackpressureConfig;
import org.synanton.resolutor.application.backpressure.BackpressureManager;
import org.synanton.resolutor.application.fake.InMemoryTaskRepository;
import org.synanton.resolutor.application.fake.NoOpMetricsPort;
import org.synanton.resolutor.application.fake.NoOpTaskWorker;
import org.synanton.resolutor.application.port.out.DispatcherPort.GroupResult;
import org.synanton.resolutor.application.port.out.TaskWorker;
import org.synanton.resolutor.domain.plan.ColourWave;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.plan.PlanId;
import org.synanton.resolutor.domain.plan.PlanMetrics;
import org.synanton.resolutor.domain.plan.SequentialGroup;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

class InProcessDispatcherTest {

  private InMemoryTaskRepository taskRepo;
  private NoOpTaskWorker worker;
  private BackpressureManager backpressure;
  private InProcessDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    taskRepo = new InMemoryTaskRepository();
    worker = new NoOpTaskWorker();
    backpressure = new BackpressureManager(BackpressureConfig.disabled());
    dispatcher =
        new InProcessDispatcher(
            taskRepo, worker, backpressure, new NoOpMetricsPort(), DispatchConfig.defaults());
  }

  @AfterEach
  void tearDown() {
    dispatcher.close();
  }

  @Test
  void dispatchesTasksSequentiallyAndMarksCompleted() {
    Task a = pending("project", "1");
    Task b = pending("project", "1");
    taskRepo.save(a);
    taskRepo.save(b);

    GroupResult result = dispatcher.dispatch(new SequentialGroup("c-0", List.of(a.id(), b.id())));

    assertThat(result.componentId()).isEqualTo("c-0");
    assertThat(result.taskResults()).extracting("success").containsExactly(true, true);
    assertThat(taskRepo.findById(a.id()))
        .get()
        .extracting(Task::state)
        .isEqualTo(TaskState.COMPLETED);
    assertThat(taskRepo.findById(b.id()))
        .get()
        .extracting(Task::state)
        .isEqualTo(TaskState.COMPLETED);
    assertThat(worker.executed()).extracting(Task::id).containsExactly(a.id(), b.id());
  }

  @Test
  void reportsMissingTaskWithoutHaltingGroup() {
    Task present = pending("project", "1");
    TaskId missing = TaskId.generate();
    taskRepo.save(present);

    GroupResult result =
        dispatcher.dispatch(new SequentialGroup("c-0", List.of(missing, present.id())));

    assertThat(result.taskResults().get(0).success()).isFalse();
    assertThat(result.taskResults().get(0).failureReason()).contains("not found");
    assertThat(result.taskResults().get(1).success()).isTrue();
  }

  @Test
  void workerFailureMarksTaskFailed() {
    Task a = pending("talk", "9");
    taskRepo.save(a);
    worker.failNext(1);

    GroupResult result = dispatcher.dispatch(new SequentialGroup("c-0", List.of(a.id())));

    assertThat(result.taskResults().get(0).success()).isFalse();
    assertThat(taskRepo.findById(a.id())).get().extracting(Task::state).isEqualTo(TaskState.FAILED);
  }

  @Test
  void runPlanExecutesEveryGroup() {
    Task a = pending("project", "1");
    Task b = pending("talk", "9");
    taskRepo.save(a);
    taskRepo.save(b);

    ExecutionPlan plan =
        new ExecutionPlan(
            PlanId.generate(),
            Instant.now(),
            "v-test",
            Duration.ofMillis(1),
            "FIFO",
            List.of(
                new SequentialGroup("c-0", List.of(a.id())),
                new SequentialGroup("c-1", List.of(b.id()))),
            new PlanMetrics(2, 2, 1, 2.0, 0.5, 0),
            java.util.Map.of(a.id(), "c-0", b.id(), "c-1"));

    List<GroupResult> results = dispatcher.runPlan(plan);

    assertThat(results).hasSize(2);
    assertThat(taskRepo.findById(a.id()))
        .get()
        .extracting(Task::state)
        .isEqualTo(TaskState.COMPLETED);
    assertThat(taskRepo.findById(b.id()))
        .get()
        .extracting(Task::state)
        .isEqualTo(TaskState.COMPLETED);
  }

  @Test
  void timeoutBeforeDispatchMarksTimeout() {
    Task expired =
        new Task(
            TaskId.generate(),
            Resource.of("project", "1"),
            Set.of(),
            "{}",
            "{}",
            null,
            TaskState.PENDING,
            Instant.now().minusSeconds(60),
            Instant.now().minusSeconds(1),
            0L);
    taskRepo.save(expired);

    GroupResult result = dispatcher.dispatch(new SequentialGroup("c-0", List.of(expired.id())));

    assertThat(result.taskResults().get(0).success()).isFalse();
    assertThat(taskRepo.findById(expired.id()))
        .get()
        .extracting(Task::state)
        .isEqualTo(TaskState.TIMEOUT);
  }

  @Test
  void sameWaveRunsConcurrently() {
    Task a = pending("project", "1");
    Task b = pending("project", "2");
    taskRepo.save(a);
    taskRepo.save(b);
    CountDownLatch entered = new CountDownLatch(2);
    TaskWorker latchWorker =
        task -> {
          entered.countDown();
          try {
            if (!entered.await(2, TimeUnit.SECONDS)) {
              throw new IllegalStateException("wave did not run concurrently");
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
          }
          return TaskWorker.Result.completed();
        };
    dispatcher.close();
    dispatcher =
        new InProcessDispatcher(
            taskRepo, latchWorker, backpressure, new NoOpMetricsPort(), DispatchConfig.defaults());

    GroupResult result =
        dispatcher.dispatch(
            new SequentialGroup(
                "c-0",
                List.of(a.id(), b.id()),
                List.of(new ColourWave(0, List.of(a.id(), b.id())))));

    assertThat(result.taskResults()).extracting("success").containsExactly(true, true);
  }

  private static Task pending(String klass, String id) {
    return new Task(
        TaskId.generate(),
        Resource.of(klass, id),
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
