package org.synanton.resolutor.adapter.web.dto;

import jakarta.validation.constraints.PositiveOrZero;

/** Request body for {@code POST /api/v1/tasks/{id}/progress}. */
public record ProgressUpdateRequest(
    @PositiveOrZero long successDelta,
    @PositiveOrZero long failedDelta,
    @PositiveOrZero long totalDelta) {}
