package org.synanton.resolutor.adapter.web.dto;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Response body for {@code GET /api/v1/tasks/{id}}. */
public record TaskStatusResponse(UUID id, String state, @Nullable ProgressBody progress) {

  public record ProgressBody(
      long totalCount, long successCount, long failedCount, long pendingCount) {}
}
