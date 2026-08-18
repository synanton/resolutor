package org.synanton.resolutor.application.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.application.backpressure.BackpressureConfig;
import org.synanton.resolutor.application.backpressure.BackpressureManager;
import org.synanton.resolutor.application.fake.FixedResourceGraphPort;
import org.synanton.resolutor.application.fake.InMemoryPlanPublisher;
import org.synanton.resolutor.application.fake.NoOpMetricsPort;
import org.synanton.resolutor.application.planner.ExecutionPlanner;
import org.synanton.resolutor.application.planner.PlannerConfig;
import org.synanton.resolutor.application.port.in.NewTaskCommand;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.resource.Resource;

class PlanQueryServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-14T12:00:00Z"), ZoneOffset.UTC);

  private InMemoryPlanPublisher publisher;
  private PlanQueryService queries;

  @BeforeEach
  void setUp() {
    publisher = new InMemoryPlanPublisher();
    ExecutionPlanner planner =
        new ExecutionPlanner(
            new FixedResourceGraphPort(),
            new BackpressureManager(BackpressureConfig.disabled()),
            new NoOpMetricsPort(),
            PlannerConfig.defaults("v-test"));
    queries = new PlanQueryService(publisher, planner, CLOCK);
  }

  @Test
  void planForReturnsLatestAtOrBefore() {
    ExecutionPlan first = queries.simulate(List.of(cmd("1")));
    ExecutionPlan second = queries.simulate(List.of(cmd("2")));
    publisher.publish(first);
    publisher.publish(second);

    assertThat(queries.planById(first.id())).contains(first);
    assertThat(queries.latestPlan()).contains(second);
    assertThat(queries.planFor(second.generatedAt())).contains(second);
    assertThat(queries.planFor(Instant.EPOCH)).isEmpty();
  }

  @Test
  void simulateDoesNotPublish() {
    ExecutionPlan plan = queries.simulate(List.of(cmd("7"), cmd("8")));

    assertThat(plan.metrics().totalTasks()).isEqualTo(2);
    assertThat(publisher.allPublished()).isEmpty();
  }

  private static NewTaskCommand cmd(String id) {
    return new NewTaskCommand(Resource.of("project", id), "{}", "{}", null);
  }
}
