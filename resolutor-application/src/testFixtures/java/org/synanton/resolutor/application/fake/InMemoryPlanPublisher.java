package org.synanton.resolutor.application.fake;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import org.synanton.resolutor.application.port.out.PlanPublisherPort;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.plan.PlanId;

/** In-memory {@link PlanPublisherPort} that records published plans for assertion. */
public final class InMemoryPlanPublisher implements PlanPublisherPort {

  private final List<ExecutionPlan> published = new CopyOnWriteArrayList<>();
  private @Nullable ExecutionPlan latest = null;

  @Override
  public void publish(ExecutionPlan plan) {
    published.add(plan);
    latest = plan;
  }

  @Override
  public Optional<ExecutionPlan> latestPlan() {
    return Optional.ofNullable(latest);
  }

  @Override
  public Optional<ExecutionPlan> findById(PlanId id) {
    return published.stream().filter(p -> p.id().equals(id)).findFirst();
  }

  @Override
  public Optional<ExecutionPlan> planAtOrBefore(Instant at) {
    return published.stream()
        .filter(p -> !p.generatedAt().isAfter(at))
        .max(Comparator.comparing(ExecutionPlan::generatedAt).thenComparing(p -> p.id().value()));
  }

  public List<ExecutionPlan> allPublished() {
    return List.copyOf(new ArrayList<>(published));
  }

  public void clear() {
    published.clear();
    latest = null;
  }
}
