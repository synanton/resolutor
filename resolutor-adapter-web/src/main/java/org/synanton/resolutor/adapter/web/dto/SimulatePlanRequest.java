package org.synanton.resolutor.adapter.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Body for {@code POST /api/v1/plans/simulate}. */
public record SimulatePlanRequest(@NotEmpty @Valid List<IngestTaskRequest> tasks) {}
