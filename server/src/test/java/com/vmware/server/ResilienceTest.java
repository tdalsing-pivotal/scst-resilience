package com.vmware.server;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED;

@Slf4j
public class ResilienceTest {

    boolean error = false;

    @Test
    void retry() {
        AtomicInteger count = new AtomicInteger(0);

        RetryConfig retryConfig = RetryConfig
                .custom()
                .retryOnException(e -> e instanceof IllegalArgumentException)
                .build();

        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);

        Retry retry = retryRegistry.retry("retry");

        Supplier<String> supplier = () -> {
            int c = count.incrementAndGet();
            if (error || (c > 2 && c < 5)) {
                log.error("retry: c={}", c);
                throw new IllegalArgumentException("error!");
            } else {
                log.info("retry: c={}", c);
                return "success";
            }
        };

        Supplier<String> decorateSupplier = Retry.decorateSupplier(retry, supplier);

        for (int i = 0; i < 10; ++i) {
            String s = Try.ofSupplier(decorateSupplier).recover(throwable -> "recovery").get();
            log.info("i = {}, s={}", i, s);
        }
    }

    @Test
    void circuitBreaker() {
        AtomicInteger count = new AtomicInteger(0);

        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig
                .custom()
                .slidingWindowType(COUNT_BASED)
                .slidingWindowSize(10)
                .failureRateThreshold(25.0f)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("testCircuitBreaker");

        Supplier<String> supplier = () -> {
            int c = count.incrementAndGet();
            if (error || (c > 2 && c < 5)) {
                throw new IllegalArgumentException("error!");
            } else {
                return "success";
            }
        };

        Supplier<String> decorateSupplier = circuitBreaker.decorateSupplier(supplier);

        for (int i = 0; i < 10; ++i) {
            String s = Try.ofSupplier(decorateSupplier).recover(throwable -> "recovery").get();
            log.info("i = {}, s={}", i, s);
        }
    }

    @Test
    void reactor() throws Exception {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig
                .custom()
                .slidingWindowType(COUNT_BASED)
                .slidingWindowSize(10)
                .failureRateThreshold(25.0f)
                .recordExceptions(IllegalArgumentException.class)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("testCircuitBreaker");

        RetryConfig retryConfig = RetryConfig
                .custom()
                .retryOnException(e -> e instanceof IllegalArgumentException)
                .build();

        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        Retry retry = retryRegistry.retry("retry");

        Predicate<Map<String, Object>> filterPredicate = map -> {
            log.info("filterPredicate: map={}", map);
            boolean failValidation = Boolean.TRUE.equals(map.get("failValidation"));

            if (failValidation) {
                log.error("failed validation: map={}", map);
            }

            return !failValidation;
        };

        Function<List<Map<String, Object>>, List<Map<String, Object>>> insertFunction = list -> {
            log.info("insertFunction: list={}", list);
            boolean failInsert = list.stream().anyMatch(m -> m.get("failInsert") != null);

            if (failInsert) {
                log.error("insertFunction: failed insert: list={}", list);
                throw new IllegalArgumentException("failed insert");
            }

            return list;
        };

        Function<List<Map<String, Object>>, List<Map<String, Object>>> decoratedRetryFunction = Retry.decorateFunction(retry, insertFunction);
        Function<List<Map<String, Object>>, List<Map<String, Object>>> decoratedCircuitBreakerFunction = CircuitBreaker.decorateFunction(circuitBreaker, decoratedRetryFunction);
        Consumer<List<Map<String, Object>>> decoratedConsumer = list -> Try
                .ofSupplier(() -> decoratedCircuitBreakerFunction.apply(list))
                .recover(error -> {
                    log.error("decoratedConsumer: list={}, error={}", list, error.toString(), error);
                    return null;
                });

        Flux<Map<String, Object>> emitterProcessor = Flux.create(sink -> {
            for (int i = 0; i < 20; ++i) {
                Map<String, Object> map = new HashMap<>();

                map.put("i", i);

                if (i == 6) {
                    map.put("failValidation", Boolean.TRUE);
                }

                if (i >= 7 && i <= 10) {
                    map.put("failInsert", Boolean.TRUE);
                }

                sink.next(map);
            }
        });

//        emitterProcessor.subscribe(map -> log.info("generate: map={}", map));

//        DirectProcessor<Map<String, Object>> emitterProcessor = DirectProcessor.create();
//        EmitterProcessor<Map<String, Object>> emitterProcessor = EmitterProcessor.create();
        emitterProcessor
                .filter(filterPredicate)
                .bufferTimeout(2, Duration.ofSeconds(1))
                .subscribe(decoratedConsumer);

//        for (int i = 0; i < 20; ++i) {
//            Map<String, Object> map = new HashMap<>();
//
//            map.put("i", i);
//
//            if (i == 6) {
//                map.put("failValidation", Boolean.TRUE);
//            }
//
//            if (i >= 7 && i <= 10) {
//                map.put("failInsert", Boolean.TRUE);
//            }
//
//            emitterProcessor.onNext(map);
//        }

        Thread.sleep(4000L);
    }
}
