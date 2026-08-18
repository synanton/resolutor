package org.synanton.resolutor.adapter.web.dto;

import java.util.UUID;

/** Response body for {@code POST /api/v1/tasks}. */
public record IngestTaskResponse(UUID id) {}
