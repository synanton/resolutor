package org.synanton.resolutor.domain.task;

/** Lifecycle states of a {@link Task}. See docs/design.md §23 for the state machine. */
public enum TaskState {
  RECEIVED,
  PENDING,
  STARTED,
  PROCESSING,
  PAUSED,
  COMPLETED,
  TIMEOUT,
  FAILED
}
