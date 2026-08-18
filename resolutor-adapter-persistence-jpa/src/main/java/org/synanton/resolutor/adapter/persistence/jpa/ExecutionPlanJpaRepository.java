package org.synanton.resolutor.adapter.persistence.jpa;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** History store for compiled {@link org.synanton.resolutor.domain.plan.ExecutionPlan}s. */
public interface ExecutionPlanJpaRepository extends JpaRepository<ExecutionPlanEntity, UUID> {

  Optional<ExecutionPlanEntity> findFirstByGeneratedAtLessThanEqualOrderByGeneratedAtDescIdDesc(
      Instant generatedAt);
}
