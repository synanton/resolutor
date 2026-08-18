package org.synanton.resolutor.domain.policy;

import java.util.List;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;

/**
 * Stub. Orders by an externally-supplied priority.
 *
 * <p>TODO (phase 2+): When a {@code priority} field is added to {@link Task}, read it here. Until
 * then, delegates to FIFO so the planner can reference this policy by name.
 */
public final class PriorityPolicy implements OrderingPolicy {

  public static final PriorityPolicy INSTANCE = new PriorityPolicy();

  private PriorityPolicy() {}

  @Override
  public List<TaskId> order(List<Task> tasks) {
    return FifoPolicy.INSTANCE.order(tasks);
  }

  @Override
  public String name() {
    return "PRIORITY";
  }
}
