package org.synanton.resolutor.domain.task;

import java.util.Objects;

/**
 * Opaque, monotonically-advancing resumption marker.
 *
 * <p>The domain treats the cursor as an opaque string; adapters own serialisation. A null cursor
 * means the task has not started processing yet.
 */
public record Cursor(String value) {

  public Cursor {
    Objects.requireNonNull(value, "value");
  }

  public static Cursor initial() {
    return new Cursor("");
  }
}
