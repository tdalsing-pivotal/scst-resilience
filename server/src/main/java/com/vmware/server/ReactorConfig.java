package com.vmware.server;

import com.vmware.common.MyObject;
import com.vmware.common.ValidationService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.vavr.control.Try;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.Message;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import static lombok.AccessLevel.PRIVATE;
import static org.springframework.kafka.support.KafkaHeaders.ACKNOWLEDGMENT;

@Configuration
@EnableScheduling
@Import(ValidationService.class)
@Slf4j
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class ReactorConfig {

    StreamBridge bridge;

    Counter counter;
    Counter insertErrorCounter;
    Counter duplicateCounter;
    ValidationService<MyObject> validationService;

    AtomicBoolean failInsert = new AtomicBoolean(false);
    Executor failInsertTimer = Executors.newSingleThreadExecutor();
    Set<String> messageIds = new HashSet<>();

    public ReactorConfig(
            StreamBridge bridge, Counter counter,
            Counter insertErrorCounter,
            Counter duplicateCounter,
            ValidationService<MyObject> validationService) {
        this.bridge = bridge;
        this.counter = counter;
        this.insertErrorCounter = insertErrorCounter;
        this.duplicateCounter = duplicateCounter;
        this.validationService = validationService;
    }

    @Scheduled(initialDelay = 30000L, fixedDelay = 60000L)
    public void enableInsertFail() {
        log.warn("enableInsertFail");
        failInsertTimer.execute(() -> {
            log.warn("enableInsertFail: start...");
            failInsert.set(true);

            try {
                Thread.sleep(10000L);
                log.warn("enableInsertFail: done.");
            } catch (InterruptedException e) {
                log.error("enableInsertFail: e={}", e.toString(), e);
            } finally {
                failInsert.set(false);
            }
        });
    }

    @Bean
    public static Counter counter(MeterRegistry registry) {
        log.info("counter");
        return registry.counter("resilience-server-counter");
    }

    @Bean
    public static Counter insertErrorCounter(MeterRegistry registry) {
        log.info("insertErrorCounter");
        return registry.counter("resilience-server-insertErrorCounter");
    }

    @Bean
    public static Counter duplicateCounter(MeterRegistry registry) {
        log.info("duplicateCounter");
        return registry.counter("resilience-server-duplicateCounter");
    }

    @Bean
    public CircuitBreaker insertCircuitBreaker(CircuitBreakerRegistry circuitBreakerRegistry) {
        log.info("insertCircuitBreaker");
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("insert");
        CircuitBreakerConfig circuitBreakerConfig = circuitBreaker.getCircuitBreakerConfig();
        log.info("insertCircuitBreaker: circuitBreakerConfig={}", ToStringBuilder.reflectionToString(circuitBreakerConfig));
        return circuitBreaker;
    }

    @Bean
    public Retry retry(RetryRegistry retryRegistry) {
        log.info("retry");
        Retry retry = retryRegistry.retry("insert");
        RetryConfig retryConfig = retry.getRetryConfig();
        log.info("retry: retryConfig={}", ToStringBuilder.reflectionToString(retryConfig));
        return retry;
    }

    @Bean
    public Consumer<Message<MyObject>> consumer(CircuitBreaker insertCircuitBreaker, Retry retry) {
        log.info("consumer");

        Function<Message<MyObject>, Message<MyObject>> insertFunction = message -> {
            log.debug("insertFunction: message={}", message);
            boolean fail = failInsert.get();

            if (fail) {
                log.debug("insertFunction: failed insert");
                insertErrorCounter.increment();
                throw new IllegalArgumentException("failed insert");
            } else {
                counter.increment();
                ack(message);

                String id = message.getHeaders().getId().toString();
                if (!messageIds.add(id)) {
                    log.warn("insertFunction: duplicate message: id={}", id);
                    duplicateCounter.increment();
                }

                return message;
            }
        };

        Function<Message<MyObject>, Message<MyObject>> decoratedRetryFunction = Retry.decorateFunction(retry, insertFunction);
        Function<Message<MyObject>, Message<MyObject>> decoratedCircuitBreakerFunction =
                CircuitBreaker.decorateFunction(insertCircuitBreaker, decoratedRetryFunction);

        return message ->
                Try
                        .ofSupplier(() -> decoratedCircuitBreakerFunction.apply(message))
                        .recover(error -> {
                            log.error("decoratedConsumer: error={}", error.toString());
                            bridge.send("errorSupplier-out-0", message);
                            return null;
                        });
    }

    private void ack(Message<MyObject> message) {
        log.debug("ack");
        message.getHeaders().get(ACKNOWLEDGMENT, Acknowledgment.class).acknowledge();
    }
}
