package org.synanton.resolutor.adapter.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.synanton.resolutor.application.fake.NoOpMetricsPort;
import org.synanton.resolutor.application.port.out.DispatcherPort.GroupResult;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.plan.PlanId;
import org.synanton.resolutor.domain.plan.PlanMetrics;
import org.synanton.resolutor.domain.plan.SequentialGroup;
import org.synanton.resolutor.domain.task.TaskId;

class KafkaPlanDispatcherTest {

  @Test
  void runPlanPublishesOneRecordPerGroup() {
    MockProducer<String, String> mock =
        new MockProducer<>(true, new StringSerializer(), new StringSerializer());
    ProducerFactory<String, String> factory =
        new ProducerFactory<>() {
          @Override
          public Producer<String, String> createProducer() {
            return mock;
          }
        };
    KafkaTemplate<String, String> kafka = new KafkaTemplate<>(factory);
    KafkaPlanDispatcher dispatcher =
        new KafkaPlanDispatcher(
            kafka,
            new ObjectMapper(),
            "resolutor.plan.groups",
            CircuitBreaker.of("plan-dispatch-kafka", CircuitBreakerConfig.ofDefaults()),
            new NoOpMetricsPort(),
            Duration.ofSeconds(2));

    TaskId a = TaskId.of(new UUID(0, 1));
    ExecutionPlan plan =
        new ExecutionPlan(
            PlanId.generate(),
            Instant.parse("2026-01-01T00:00:00Z"),
            "v2",
            Duration.ZERO,
            "FIFO",
            List.of(new SequentialGroup("c-0", List.of(a))),
            new PlanMetrics(1, 1, 1, 1.0, 1.0, 0),
            Map.of(a, "c-0"));

    List<GroupResult> results = dispatcher.runPlan(plan);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().taskResults().getFirst().success()).isTrue();
    assertThat(mock.history()).hasSize(1);
    assertThat(mock.history().getFirst().key()).isEqualTo("c-0");
  }

  @Test
  void missingWavesDeserialiseAsSerialGroup() throws Exception {
    ObjectMapper json = new ObjectMapper();
    TaskId a = TaskId.of(new UUID(0, 1));
    String legacy = "{\"componentId\":\"c-0\",\"orderedTasks\":[\"" + a.value() + "\"]}";

    SequentialGroup group =
        json.readValue(legacy, KafkaPlanDispatcher.GroupMessage.class).toGroup();

    assertThat(group.waves()).hasSize(1);
    assertThat(group.waves().getFirst().taskIds()).containsExactly(a);
  }
}
