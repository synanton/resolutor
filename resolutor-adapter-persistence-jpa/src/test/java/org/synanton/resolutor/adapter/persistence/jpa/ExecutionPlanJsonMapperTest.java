package org.synanton.resolutor.adapter.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.domain.plan.ColourWave;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.plan.PlanId;
import org.synanton.resolutor.domain.plan.PlanMetrics;
import org.synanton.resolutor.domain.plan.SequentialGroup;
import org.synanton.resolutor.domain.task.TaskId;

class ExecutionPlanJsonMapperTest {

  private final ExecutionPlanJsonMapper mapper = new ExecutionPlanJsonMapper(new ObjectMapper());

  @Test
  void roundTripPreservesWavesAndColouringMetrics() {
    TaskId a = TaskId.of(new UUID(0, 1));
    TaskId b = TaskId.of(new UUID(0, 2));
    SequentialGroup group =
        new SequentialGroup("c-0", List.of(a, b), List.of(new ColourWave(0, List.of(a, b))));
    ExecutionPlan plan =
        new ExecutionPlan(
            PlanId.of(new UUID(0, 9)),
            Instant.parse("2026-01-01T00:00:00Z"),
            "v3",
            Duration.ZERO,
            "FIFO",
            List.of(group),
            new PlanMetrics(2, 1, 2, 0.5, 1.0, 0, 1, 1, 1.0),
            Map.of(a, "c-0", b, "c-0"));

    ExecutionPlan back = mapper.deserialise(mapper.serialise(plan));

    assertThat(back.groups().getFirst().waves()).isEqualTo(group.waves());
    assertThat(back.metrics().chromaticNumber()).isEqualTo(1);
    assertThat(back.metrics().waveCount()).isEqualTo(1);
    assertThat(back.metrics().intraComponentParallelism()).isEqualTo(1.0);
  }

  @Test
  void missingWavesAndMetricsDefaultToSerial() {
    TaskId a = TaskId.of(new UUID(0, 1));
    String legacy =
        """
        {"id":"00000000-0000-0000-0000-000000000009",\
        "generatedAt":"2026-01-01T00:00:00Z","plannerVersion":"v2","planningDuration":"PT0S",\
        "orderPolicy":"FIFO","groups":[{"componentId":"c-0","orderedTasks":["%s"]}],\
        "metrics":{"totalTasks":1,"connectedComponents":1,"largestComponent":1,\
        "parallelismFactor":1.0,"serializationRatio":1.0,"conflictsDetected":0},\
        "taskToComponent":{"%s":"c-0"}}
        """
            .formatted(a.value(), a.value());

    ExecutionPlan plan = mapper.deserialise(legacy);

    assertThat(plan.groups().getFirst().waves()).hasSize(1);
    assertThat(plan.groups().getFirst().waves().getFirst().taskIds()).containsExactly(a);
    assertThat(plan.metrics().chromaticNumber()).isZero();
    assertThat(plan.metrics().waveCount()).isZero();
  }
}
