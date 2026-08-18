package org.synanton.resolutor.adapter.persistence.jpa;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;
import org.synanton.resolutor.application.port.out.PlanPublisherPort;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.plan.PlanId;

/**
 * JPA-backed {@link PlanPublisherPort}. Appends to {@code execution_plans} and mirrors the latest
 * row in {@code execution_plan_latest}.
 */
public final class PlanPublisherJpaAdapter implements PlanPublisherPort {

  private final ExecutionPlanLatestJpaRepository latestRepo;
  private final ExecutionPlanJpaRepository historyRepo;
  private final ExecutionPlanJsonMapper mapper;

  public PlanPublisherJpaAdapter(
      ExecutionPlanLatestJpaRepository latestRepo,
      ExecutionPlanJpaRepository historyRepo,
      ExecutionPlanJsonMapper mapper) {
    this.latestRepo = Objects.requireNonNull(latestRepo, "latestRepo");
    this.historyRepo = Objects.requireNonNull(historyRepo, "historyRepo");
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  @Override
  @Transactional
  public void publish(ExecutionPlan plan) {
    Objects.requireNonNull(plan, "plan");
    String json = mapper.serialise(plan);

    ExecutionPlanEntity history = new ExecutionPlanEntity();
    history.setId(plan.id().value());
    history.setGeneratedAt(plan.generatedAt());
    history.setPlan(json);
    historyRepo.save(history);

    ExecutionPlanLatestEntity latest =
        latestRepo
            .findById(ExecutionPlanLatestEntity.SINGLETON_ID)
            .orElseGet(ExecutionPlanLatestEntity::new);
    latest.setId(ExecutionPlanLatestEntity.SINGLETON_ID);
    latest.setPlan(json);
    latest.setGeneratedAt(plan.generatedAt());
    latestRepo.save(latest);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ExecutionPlan> latestPlan() {
    return latestRepo
        .findById(ExecutionPlanLatestEntity.SINGLETON_ID)
        .map(ExecutionPlanLatestEntity::getPlan)
        .map(mapper::deserialise);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ExecutionPlan> findById(PlanId id) {
    Objects.requireNonNull(id, "id");
    return historyRepo
        .findById(id.value())
        .map(ExecutionPlanEntity::getPlan)
        .map(mapper::deserialise);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ExecutionPlan> planAtOrBefore(Instant at) {
    Objects.requireNonNull(at, "at");
    return historyRepo
        .findFirstByGeneratedAtLessThanEqualOrderByGeneratedAtDescIdDesc(at)
        .map(ExecutionPlanEntity::getPlan)
        .map(mapper::deserialise);
  }
}
