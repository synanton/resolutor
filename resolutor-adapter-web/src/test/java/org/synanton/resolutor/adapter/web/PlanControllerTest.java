package org.synanton.resolutor.adapter.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.synanton.resolutor.application.port.in.PlanQueryPort;
import org.synanton.resolutor.application.port.in.ProgressPort;
import org.synanton.resolutor.application.port.in.TaskIngestionPort;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.plan.PlanId;
import org.synanton.resolutor.domain.plan.PlanMetrics;
import org.synanton.resolutor.domain.plan.SequentialGroup;
import org.synanton.resolutor.domain.task.TaskId;

@WebMvcTest(controllers = PlanController.class)
@Import(GlobalExceptionHandler.class)
class PlanControllerTest {

  @Autowired MockMvc mvc;

  @MockBean PlanQueryPort planQuery;
  @MockBean TaskIngestionPort ingestion;
  @MockBean ProgressPort progress;

  @Test
  void latestReturns204WhenNoPlanPublished() throws Exception {
    when(planQuery.latestPlan()).thenReturn(Optional.empty());

    mvc.perform(get("/api/v1/plans/latest")).andExpect(status().isNoContent());
  }

  @Test
  void latestReturns200AndBodyWhenPresent() throws Exception {
    TaskId a = TaskId.generate();
    TaskId b = TaskId.generate();
    SequentialGroup g = new SequentialGroup("c-1", List.of(a, b));
    PlanMetrics metrics = new PlanMetrics(2, 1, 2, 0.5, 1.0, 1);
    ExecutionPlan plan =
        new ExecutionPlan(
            PlanId.generate(),
            Instant.parse("2026-01-01T00:00:00Z"),
            "v-test",
            Duration.ofMillis(42),
            "FIFO",
            List.of(g),
            metrics,
            Map.of(a, "c-1", b, "c-1"));
    when(planQuery.latestPlan()).thenReturn(Optional.of(plan));

    mvc.perform(get("/api/v1/plans/latest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.plannerVersion").value("v-test"))
        .andExpect(jsonPath("$.orderPolicy").value("FIFO"))
        .andExpect(jsonPath("$.metrics.totalTasks").value(2))
        .andExpect(jsonPath("$.metrics.parallelismFactor").value(0.5))
        .andExpect(jsonPath("$.groups[0].componentId").value("c-1"))
        .andExpect(jsonPath("$.groups[0].orderedTasks.length()").value(2))
        .andExpect(jsonPath("$.groups[0].waves.length()").value(2));
  }

  @Test
  void byIdReturns404WhenMissing() throws Exception {
    java.util.UUID id = java.util.UUID.randomUUID();
    when(planQuery.planById(org.synanton.resolutor.domain.plan.PlanId.of(id)))
        .thenReturn(Optional.empty());

    mvc.perform(get("/api/v1/plans/" + id)).andExpect(status().isNotFound());
  }

  @Test
  void explainReturnsDensity() throws Exception {
    TaskId a = TaskId.generate();
    PlanId id = PlanId.generate();
    ExecutionPlan plan =
        new ExecutionPlan(
            id,
            Instant.parse("2026-01-01T00:00:00Z"),
            "v-test",
            Duration.ZERO,
            "FIFO",
            List.of(new SequentialGroup("c-1", List.of(a))),
            new PlanMetrics(1, 1, 1, 1.0, 1.0, 0),
            Map.of(a, "c-1"));
    when(planQuery.planById(id)).thenReturn(Optional.of(plan));

    mvc.perform(get("/api/v1/plans/" + id.value() + "/explain"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.value().toString()))
        .andExpect(jsonPath("$.conflictDensity").value(0.0));
  }
}
