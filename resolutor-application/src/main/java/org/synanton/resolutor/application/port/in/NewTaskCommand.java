package org.synanton.resolutor.application.port.in;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.synanton.resolutor.domain.resource.Resource;

/** Command to ingest a new task into Resolutor. */
public record NewTaskCommand(
    Resource topResource, String searchDsl, String payload, @Nullable Instant timeoutAt) {

  public NewTaskCommand {
    Objects.requireNonNull(topResource, "topResource");
    Objects.requireNonNull(searchDsl, "searchDsl");
    Objects.requireNonNull(payload, "payload");
  }
}
