package org.synanton.resolutor.application.port.in;

import org.synanton.resolutor.domain.task.TaskId;

/** Inbound port: submit a new task for execution planning. */
public interface TaskIngestionPort {

  /**
   * Persist {@code cmd} as a new task in {@code PENDING} so the next planner tick can pick it up.
   *
   * @return the generated task identifier
   */
  TaskId ingest(NewTaskCommand cmd);
}
