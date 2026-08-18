package org.synanton.resolutor.adapter.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.application.backpressure.BackpressureConfig;
import org.synanton.resolutor.application.backpressure.BackpressureManager;
import org.synanton.resolutor.application.fake.InMemoryTaskRepository;
import org.synanton.resolutor.domain.plan.PlanMetrics;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

class MicrometerMetricsPortTest {

  @Test
  void gaugesAndCountersReflectRepositoryAndRecordedEvents() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    InMemoryTaskRepository tasks = new InMemoryTaskRepository();
    tasks.save(
        new Task(
            TaskId.generate(),
            Resource.of("project", "7"),
            Set.of(),
            "{}",
            "{}",
            null,
            TaskState.PENDING,
            Instant.parse("2026-08-14T00:00:00Z"),
            null,
            0L));
    BackpressureManager backpressure = new BackpressureManager(BackpressureConfig.defaults());
    MicrometerMetricsPort port =
        new MicrometerMetricsPort(
            registry, backpressure, tasks, io.micrometer.observation.ObservationRegistry.NOOP);

    port.incrementTasksIngested(2);
    port.recordPlanBuilt(new PlanMetrics(1, 1, 1, 1.0, 0.0, 0, 2, 3, 0.5), Duration.ofMillis(12));
    port.recordBackpressureDenied("project");
    port.recordResourceGraphCall("success");
    port.recordGroupDispatched("c1", Duration.ofMillis(5), true);

    assertThat(registry.get("resolutor.tasks.ingested").counter().count()).isEqualTo(2.0);
    assertThat(registry.get("resolutor.tasks.state").tag("state", "PENDING").gauge().value())
        .isEqualTo(1.0);
    assertThat(registry.get("resolutor.plan.tasks.total").gauge().value()).isEqualTo(1.0);
    assertThat(registry.get("resolutor.plan.chromatic_number").gauge().value()).isEqualTo(2.0);
    assertThat(registry.get("resolutor.plan.wave_count").gauge().value()).isEqualTo(3.0);
    assertThat(registry.get("resolutor.plan.intra_component_parallelism").gauge().value())
        .isEqualTo(0.5);
    assertThat(
            registry
                .get("resolutor.resource_graph.calls")
                .tag("outcome", "success")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            registry
                .get("resolutor.backpressure.denied")
                .tag("resource_class", "project")
                .counter()
                .count())
        .isEqualTo(1.0);
  }
}
