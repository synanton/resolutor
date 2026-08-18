package org.synanton.resolutor.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.plan.PlanId;
import org.synanton.resolutor.domain.plan.PlanMetrics;
import org.synanton.resolutor.domain.plan.SequentialGroup;
import org.synanton.resolutor.domain.task.TaskId;

class PlanExplainViewTest {

  @Test
  void densityAndDegreeFromConflictsAndVertices() {
    TaskId a = TaskId.generate();
    TaskId b = TaskId.generate();
    ExecutionPlan plan =
        new ExecutionPlan(
            PlanId.generate(),
            Instant.parse("2026-01-01T00:00:00Z"),
            "v",
            Duration.ZERO,
            "FIFO",
            List.of(new SequentialGroup("c-0", List.of(a, b))),
            new PlanMetrics(2, 1, 2, 0.5, 1.0, 1),
            Map.of(a, "c-0", b, "c-0"));

    PlanExplainView view = PlanExplainView.from(plan);

    assertThat(view.conflictDensity()).isEqualTo(1.0);
    assertThat(view.averageDegree()).isEqualTo(1.0);
  }
}
