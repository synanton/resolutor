package org.synanton.resolutor.adapter.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Request body for {@code POST /api/v1/tasks}. */
public record IngestTaskRequest(
    @NotBlank String topResourceClass,
    @NotBlank String topResourceId,
    JsonNode searchDsl,
    JsonNode payload,
    @Nullable Instant timeoutAt) {}
