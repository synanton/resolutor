package org.synanton.resolutor.adapter.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.kafka.core.KafkaTemplate;
import org.synanton.resolutor.application.port.out.DispatcherPort;
import org.synanton.resolutor.application.port.out.MetricsPort;
import org.synanton.resolutor.domain.plan.ColourWave;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.plan.SequentialGroup;
import org.synanton.resolutor.domain.task.TaskId;

/**
 * {@link DispatcherPort} that publishes each sequential group to Kafka (key = component id).
 * Execution is asynchronous: a consumer in this process (or another) runs {@code
 * InProcessDispatcher.dispatch}. Payload includes colour waves; missing waves are treated as
 * serial.
 */
public final class KafkaPlanDispatcher implements DispatcherPort {

  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper json;
  private final String topic;
  private final CircuitBreaker circuitBreaker;
  private final MetricsPort metrics;
  private final Duration sendTimeout;

  public KafkaPlanDispatcher(
      KafkaTemplate<String, String> kafka,
      ObjectMapper json,
      String topic,
      CircuitBreaker circuitBreaker,
      MetricsPort metrics,
      Duration sendTimeout) {
    this.kafka = Objects.requireNonNull(kafka, "kafka");
    this.json = Objects.requireNonNull(json, "json");
    this.topic = Objects.requireNonNull(topic, "topic");
    this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    this.sendTimeout = Objects.requireNonNull(sendTimeout, "sendTimeout");
  }

  @Override
  public List<GroupResult> runPlan(ExecutionPlan plan) {
    Objects.requireNonNull(plan, "plan");
    List<GroupResult> results = new ArrayList<>(plan.groups().size());
    for (SequentialGroup group : plan.groups()) {
      results.add(dispatch(group));
    }
    return List.copyOf(results);
  }

  @Override
  public GroupResult dispatch(SequentialGroup group) {
    Objects.requireNonNull(group, "group");
    try {
      String payload = json.writeValueAsString(GroupMessage.from(group));
      circuitBreaker.executeCheckedSupplier(
          () -> {
            kafka
                .send(topic, group.componentId(), payload)
                .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return Boolean.TRUE;
          });
      metrics.recordGroupDispatched(group.componentId(), Duration.ZERO, true);
      return new GroupResult(
          group.componentId(), group.orderedTasks().stream().map(TaskResult::ok).toList());
    } catch (Throwable ex) {
      metrics.recordGroupDispatched(group.componentId(), Duration.ZERO, false);
      String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
      return new GroupResult(
          group.componentId(),
          group.orderedTasks().stream().map(id -> TaskResult.failed(id, reason)).toList());
    }
  }

  record GroupMessage(
      String componentId, List<UUID> orderedTasks, @Nullable List<WaveMessage> waves) {

    static GroupMessage from(SequentialGroup group) {
      return new GroupMessage(
          group.componentId(),
          group.orderedTasks().stream().map(TaskId::value).toList(),
          group.waves().stream()
              .map(
                  w ->
                      new WaveMessage(w.colour(), w.taskIds().stream().map(TaskId::value).toList()))
              .toList());
    }

    SequentialGroup toGroup() {
      List<TaskId> ordered = orderedTasks.stream().map(TaskId::of).toList();
      if (waves == null || waves.isEmpty()) {
        return new SequentialGroup(componentId, ordered);
      }
      List<ColourWave> colourWaves =
          waves.stream()
              .map(w -> new ColourWave(w.colour(), w.taskIds().stream().map(TaskId::of).toList()))
              .toList();
      return new SequentialGroup(componentId, ordered, colourWaves);
    }
  }

  record WaveMessage(int colour, List<UUID> taskIds) {}
}
