package org.synanton.resolutor.adapter.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the single-row {@link ExecutionPlanLatestEntity}. */
public interface ExecutionPlanLatestJpaRepository
    extends JpaRepository<ExecutionPlanLatestEntity, Short> {}
