package org.synanton.resolutor.domain.policy;

import java.util.List;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;

/**
 * Determines the sequential execution order of tasks within a connected component.
 *
 * <p>Sealed so the set of built-in policies is closed at the domain level; new policies are added
 * by extending this interface and updating the permits clause. The planner picks a policy from
 * configuration and applies it per component.
 */
public sealed interface OrderingPolicy permits FifoPolicy, PriorityPolicy, DeadlinePolicy {

  List<TaskId> order(List<Task> tasksInComponent);

  String name();
}
