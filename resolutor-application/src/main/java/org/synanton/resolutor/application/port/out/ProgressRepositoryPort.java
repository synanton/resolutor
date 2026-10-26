package org.synanton.resolutor.application.port.out;

import java.util.Map;
import java.util.Optional;
import org.synanton.resolutor.domain.task.TaskId;

/** Outbound port: persistence for per-task progress snapshots. */
public interface ProgressRepositoryPort {

  /** Find the progress snapshot for {@code id}, or empty when none has been recorded. */
  Optional<ProgressSnapshot> findByTaskId(TaskId id);

  /** Insert or update {@code snapshot}. */
  void save(ProgressSnapshot snapshot);

  /** Count in-flight tasks per resource class, for backpressure reconstruction. */
  Map<String, Long> inflightCountsByResourceClass();
}
