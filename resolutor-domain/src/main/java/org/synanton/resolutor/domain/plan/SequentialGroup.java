package org.synanton.resolutor.domain.plan;

import java.util.List;
import java.util.Objects;
import org.synanton.resolutor.domain.task.TaskId;

/**
 * One connected component of the conflict graph.
 *
 * <p>{@code groups} in an {@link ExecutionPlan} run concurrently. Within a group, {@code waves} run
 * in order; tasks inside a wave may run in parallel. {@code orderedTasks} is the flattened wave
 * order (debug / fallback). Missing or empty {@code waves} are treated as fully serial.
 */
public record SequentialGroup(
    String componentId, List<TaskId> orderedTasks, List<ColourWave> waves) {

  public SequentialGroup {
    Objects.requireNonNull(componentId, "componentId");
    if (componentId.isBlank()) throw new IllegalArgumentException("componentId must not be blank");
    orderedTasks = List.copyOf(orderedTasks);
    waves =
        (waves == null || waves.isEmpty()) ? ColourWave.serial(orderedTasks) : List.copyOf(waves);
  }

  /** Serial group: one task per wave in {@code orderedTasks} order. */
  public SequentialGroup(String componentId, List<TaskId> orderedTasks) {
    this(componentId, orderedTasks, ColourWave.serial(orderedTasks));
  }

  public int size() {
    return orderedTasks.size();
  }
}
