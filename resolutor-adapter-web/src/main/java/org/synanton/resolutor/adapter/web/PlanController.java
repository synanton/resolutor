package org.synanton.resolutor.adapter.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.synanton.resolutor.adapter.web.dto.ExecutionPlanDtoMapper;
import org.synanton.resolutor.adapter.web.dto.ExecutionPlanResponse;
import org.synanton.resolutor.adapter.web.dto.IngestTaskRequest;
import org.synanton.resolutor.adapter.web.dto.PlanExplainResponse;
import org.synanton.resolutor.adapter.web.dto.SimulatePlanRequest;
import org.synanton.resolutor.application.port.in.NewTaskCommand;
import org.synanton.resolutor.application.port.in.PlanExplainView;
import org.synanton.resolutor.application.port.in.PlanQueryPort;
import org.synanton.resolutor.domain.plan.PlanId;
import org.synanton.resolutor.domain.resource.Resource;

/** Reads published {@code ExecutionPlan}s and dry-run compilation. */
@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

  private final PlanQueryPort planQuery;
  private final ObjectMapper json;

  public PlanController(PlanQueryPort planQuery, ObjectMapper json) {
    this.planQuery = Objects.requireNonNull(planQuery, "planQuery");
    this.json = Objects.requireNonNull(json, "json");
  }

  /** Return the latest published plan, or 204 when none has been compiled yet. */
  @GetMapping("/latest")
  public ResponseEntity<ExecutionPlanResponse> latest() {
    return planQuery
        .latestPlan()
        .map(ExecutionPlanDtoMapper::toResponse)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  /** Latest plan generated at or before {@code at}, or 204 if none. */
  @GetMapping(params = "at")
  public ResponseEntity<ExecutionPlanResponse> at(@RequestParam Instant at) {
    return planQuery
        .planFor(at)
        .map(ExecutionPlanDtoMapper::toResponse)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  /** Load a plan by id, or 404. */
  @GetMapping("/{id}")
  public ResponseEntity<ExecutionPlanResponse> byId(@PathVariable UUID id) {
    return planQuery
        .planById(PlanId.of(id))
        .map(ExecutionPlanDtoMapper::toResponse)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /** Conflict-density / degree explain payload for a stored plan. */
  @GetMapping("/{id}/explain")
  public ResponseEntity<PlanExplainResponse> explain(@PathVariable UUID id) {
    return planQuery
        .planById(PlanId.of(id))
        .map(PlanExplainView::from)
        .map(PlanExplainResponse::from)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /** Compile a plan for the given tasks without ingesting or dispatching. */
  @PostMapping("/simulate")
  public ExecutionPlanResponse simulate(@Valid @RequestBody SimulatePlanRequest req) {
    List<NewTaskCommand> commands = new ArrayList<>(req.tasks().size());
    for (IngestTaskRequest task : req.tasks()) {
      commands.add(
          new NewTaskCommand(
              Resource.of(task.topResourceClass(), task.topResourceId()),
              writeJson(task.searchDsl()),
              writeJson(task.payload()),
              task.timeoutAt()));
    }
    return ExecutionPlanDtoMapper.toResponse(planQuery.simulate(commands));
  }

  private String writeJson(@Nullable JsonNode node) {
    if (node == null || node instanceof MissingNode) {
      return "{}";
    }
    try {
      return json.writeValueAsString(node);
    } catch (Exception e) {
      throw new IllegalArgumentException("Unserialisable JSON", e);
    }
  }
}
