package org.synanton.resolutor.adapter.persistence.jpa;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for {@link TaskEntity}. */
public interface TaskJpaRepository extends JpaRepository<TaskEntity, UUID> {

  /** Tasks the planner may admit: PENDING or PAUSED, oldest first. */
  @Query(
      "select t from TaskEntity t where t.state in ('PENDING','PAUSED')"
          + " order by t.createdAt asc, t.id asc")
  List<TaskEntity> findEligibleForPlanning(Pageable pageable);

  /** In-flight counts by top-level resource class for backpressure reconstruction. */
  @Query(
      "select t.topResourceClass, count(t) from TaskEntity t"
          + " where t.state in ('STARTED','PROCESSING') group by t.topResourceClass")
  List<Object[]> aggregateInflightByResourceClass();

  /** Rows in {@code states} whose timeout has elapsed. */
  @Query(
      "select t from TaskEntity t where t.state in :states"
          + " and t.timeoutAt is not null and t.timeoutAt <= :now")
  List<TaskEntity> findExpired(
      @Param("states") Collection<String> states, @Param("now") Instant now);

  /** Live row counts grouped by state. */
  @Query("select t.state, count(t) from TaskEntity t group by t.state")
  List<Object[]> countByState();

  /** All rows whose state is in {@code states}. */
  List<TaskEntity> findByStateIn(Collection<String> states);
}
