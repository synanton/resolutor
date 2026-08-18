package org.synanton.resolutor.adapter.persistence.jpa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.synanton.resolutor.domain.plan.ColourWave;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.plan.PlanId;
import org.synanton.resolutor.domain.plan.PlanMetrics;
import org.synanton.resolutor.domain.plan.SequentialGroup;
import org.synanton.resolutor.domain.task.TaskId;

/** Bidirectional mapping between {@link ExecutionPlan} and its JSONB serialisation. */
public final class ExecutionPlanJsonMapper {

  private static final TypeReference<PlanJson> PLAN_JSON = new TypeReference<>() {};

  private final ObjectMapper json;

  public ExecutionPlanJsonMapper(ObjectMapper json) {
    this.json = Objects.requireNonNull(json, "json");
  }

  public String serialise(ExecutionPlan plan) {
    try {
      return json.writeValueAsString(toJson(plan));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialise ExecutionPlan", e);
    }
  }

  public ExecutionPlan deserialise(String raw) {
    try {
      return fromJson(json.readValue(raw, PLAN_JSON));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to deserialise ExecutionPlan: " + raw, e);
    }
  }

  // ── JSON shape ────────────────────────────────────────────────────────────

  private static PlanJson toJson(ExecutionPlan plan) {
    List<GroupJson> groups = new ArrayList<>(plan.groups().size());
    for (SequentialGroup g : plan.groups()) {
      groups.add(
          new GroupJson(
              g.componentId(),
              g.orderedTasks().stream().map(t -> t.value().toString()).toList(),
              g.waves().stream().map(ExecutionPlanJsonMapper::toWave).toList()));
    }
    Map<String, String> taskToComponent = new HashMap<>(plan.taskToComponent().size() * 2);
    plan.taskToComponent().forEach((k, v) -> taskToComponent.put(k.value().toString(), v));
    PlanMetrics m = plan.metrics();
    return new PlanJson(
        plan.id().value().toString(),
        plan.generatedAt().toString(),
        plan.plannerVersion(),
        plan.planningDuration().toString(),
        plan.orderPolicy(),
        groups,
        new MetricsJson(
            m.totalTasks(),
            m.connectedComponents(),
            m.largestComponent(),
            m.parallelismFactor(),
            m.serializationRatio(),
            m.conflictsDetected(),
            m.chromaticNumber(),
            m.waveCount(),
            m.intraComponentParallelism(),
            m.estimatedDurationMillis()),
        taskToComponent);
  }

  private static ExecutionPlan fromJson(PlanJson plan) {
    List<SequentialGroup> groups = new ArrayList<>(plan.groups().size());
    for (GroupJson g : plan.groups()) {
      List<TaskId> ids = g.orderedTasks().stream().map(s -> TaskId.of(UUID.fromString(s))).toList();
      List<WaveJson> rawWaves = g.waves();
      if (rawWaves == null || rawWaves.isEmpty()) {
        groups.add(new SequentialGroup(g.componentId(), ids));
      } else {
        groups.add(new SequentialGroup(g.componentId(), ids, wavesFrom(rawWaves)));
      }
    }
    Map<TaskId, String> taskToComponent = new HashMap<>(plan.taskToComponent().size() * 2);
    plan.taskToComponent().forEach((k, v) -> taskToComponent.put(TaskId.of(UUID.fromString(k)), v));
    MetricsJson m = plan.metrics();
    PlanId id =
        plan.id() == null || plan.id().isBlank()
            ? PlanId.of(
                UUID.nameUUIDFromBytes(
                    plan.generatedAt().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            : PlanId.parse(plan.id());
    return new ExecutionPlan(
        id,
        Instant.parse(plan.generatedAt()),
        plan.plannerVersion(),
        Duration.parse(plan.planningDuration()),
        plan.orderPolicy(),
        groups,
        new PlanMetrics(
            m.totalTasks(),
            m.connectedComponents(),
            m.largestComponent(),
            m.parallelismFactor(),
            m.serializationRatio(),
            m.conflictsDetected(),
            m.chromaticNumber(),
            m.waveCount(),
            m.intraComponentParallelism(),
            m.estimatedDurationMillis()),
        taskToComponent);
  }

  record PlanJson(
      @Nullable String id,
      String generatedAt,
      String plannerVersion,
      String planningDuration,
      String orderPolicy,
      List<GroupJson> groups,
      MetricsJson metrics,
      Map<String, String> taskToComponent) {}

  record GroupJson(String componentId, List<String> orderedTasks, @Nullable List<WaveJson> waves) {}

  record WaveJson(int colour, List<String> taskIds) {}

  record MetricsJson(
      int totalTasks,
      int connectedComponents,
      int largestComponent,
      double parallelismFactor,
      double serializationRatio,
      int conflictsDetected,
      int chromaticNumber,
      int waveCount,
      double intraComponentParallelism,
      long estimatedDurationMillis) {}

  private static WaveJson toWave(ColourWave wave) {
    return new WaveJson(
        wave.colour(), wave.taskIds().stream().map(t -> t.value().toString()).toList());
  }

  private static List<ColourWave> wavesFrom(List<WaveJson> waves) {
    return waves.stream()
        .map(
            w ->
                new ColourWave(
                    w.colour(),
                    w.taskIds().stream().map(s -> TaskId.of(UUID.fromString(s))).toList()))
        .toList();
  }
}
