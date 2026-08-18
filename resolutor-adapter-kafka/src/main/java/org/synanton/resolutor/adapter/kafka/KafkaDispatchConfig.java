package org.synanton.resolutor.adapter.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.synanton.resolutor.application.dispatch.InProcessDispatcher;
import org.synanton.resolutor.application.port.out.DispatcherPort;
import org.synanton.resolutor.application.port.out.MetricsPort;

/**
 * Optional Kafka dispatch (resolutor.dispatch.mode=kafka). Off by default; in-process remains the
 * v1/v2 default.
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "resolutor.dispatch.mode", havingValue = "kafka")
public class KafkaDispatchConfig {

  @Bean
  ProducerFactory<String, String> resolutorKafkaProducerFactory(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrap) {
    Map<String, Object> cfg = new HashMap<>();
    cfg.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    cfg.put(ProducerConfig.ACKS_CONFIG, "all");
    return new DefaultKafkaProducerFactory<>(cfg);
  }

  @Bean
  KafkaTemplate<String, String> resolutorKafkaTemplate(ProducerFactory<String, String> factory) {
    return new KafkaTemplate<>(factory);
  }

  @Bean
  ConsumerFactory<String, String> resolutorKafkaConsumerFactory(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrap,
      @Value("${spring.kafka.consumer.group-id:resolutor}") String groupId) {
    Map<String, Object> cfg = new HashMap<>();
    cfg.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    cfg.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    return new DefaultKafkaConsumerFactory<>(cfg);
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      ConsumerFactory<String, String> consumerFactory) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    return factory;
  }

  @Bean
  CircuitBreaker planDispatchKafkaCircuitBreaker() {
    return CircuitBreakerRegistry.of(
            CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(20)
                .minimumNumberOfCalls(5)
                .build())
        .circuitBreaker("plan-dispatch-kafka");
  }

  @Bean
  DispatcherPort kafkaDispatcherPort(
      KafkaTemplate<String, String> kafka,
      ObjectMapper json,
      @Value("${resolutor.kafka.groups-topic:resolutor.plan.groups}") String topic,
      CircuitBreaker planDispatchKafkaCircuitBreaker,
      MetricsPort metrics) {
    return new KafkaPlanDispatcher(
        kafka, json, topic, planDispatchKafkaCircuitBreaker, metrics, Duration.ofSeconds(5));
  }

  @Bean
  KafkaGroupConsumer kafkaGroupConsumer(InProcessDispatcher dispatcher, ObjectMapper json) {
    return new KafkaGroupConsumer(dispatcher, json);
  }
}
