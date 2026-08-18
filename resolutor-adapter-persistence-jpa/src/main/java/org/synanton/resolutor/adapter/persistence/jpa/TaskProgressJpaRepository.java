package org.synanton.resolutor.adapter.persistence.jpa;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link TaskProgressEntity}. */
public interface TaskProgressJpaRepository extends JpaRepository<TaskProgressEntity, UUID> {}
