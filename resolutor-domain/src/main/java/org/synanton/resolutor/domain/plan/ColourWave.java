package org.synanton.resolutor.domain.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.synanton.resolutor.domain.task.TaskId;

/**
 * One independent set of a connected component: tasks in the same wave share no conflict edge and
 * may run concurrently. Waves of a group execute in increasing {@code colour} order.
 */
public record ColourWave(int colour, List<TaskId> taskIds) {

  public ColourWave {
    if (colour < 0) throw new IllegalArgumentException("colour must be >= 0");
    taskIds = List.copyOf(Objects.requireNonNull(taskIds, "taskIds"));
  }

  /** One singleton wave per task, preserving {@code ordered} (v1 serial schedule). */
  public static List<ColourWave> serial(List<TaskId> ordered) {
    Objects.requireNonNull(ordered, "ordered");
    List<ColourWave> waves = new ArrayList<>(ordered.size());
    for (int i = 0; i < ordered.size(); i++) {
      waves.add(new ColourWave(i, List.of(ordered.get(i))));
    }
    return List.copyOf(waves);
  }
}
