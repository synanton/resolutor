package org.synanton.resolutor.domain.task;

import java.util.Objects;
import java.util.UUID;

/** Stable, opaque identifier for a {@link Task}. */
public record TaskId(UUID value) {

  public TaskId {
    Objects.requireNonNull(value, "value");
  }

  public static TaskId generate() {
    return new TaskId(UUID.randomUUID());
  }

  public static TaskId of(UUID value) {
    return new TaskId(value);
  }

  public static TaskId parse(String raw) {
    return new TaskId(UUID.fromString(raw));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
