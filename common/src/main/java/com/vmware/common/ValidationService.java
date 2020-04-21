package com.vmware.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.FluxProcessor;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.Set;

import static lombok.AccessLevel.PRIVATE;

@Service
@Slf4j
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class ValidationService<T> {

    Validator validator;
    Counter validationErrorCounter;
    FluxProcessor<Message<T>, Message<T>> errorFlux;

    public ValidationService(Validator validator, Counter validationErrorCounter, FluxProcessor<Message<T>, Message<T>> errorFlux) {
        this.validator = validator;
        this.validationErrorCounter = validationErrorCounter;
        this.errorFlux = errorFlux;
    }

    @Bean
    public static Counter validationErrorCounter(MeterRegistry registry) {
        log.info("validationErrorCounter");
        return registry.counter("resilience-server-validationErrorCounter");
    }

    @Bean
    public static FluxProcessor<Message<MyObject>, Message<MyObject>> errorFlux() {
        log.info("errorFlux");
        return EmitterProcessor.create();
    }

    public boolean validate(Message<T> message) {
        T obj = message.getPayload();
        log.debug("validate: obj={}", obj);

        Set<ConstraintViolation<T>> violations = validator.validate(obj);

        if (violations.isEmpty()) {
            return true;
        } else {
            String violationsString = violations.toString();
            log.error("validate: failed validation: obj={}, violations={}", obj, violationsString);

            validationErrorCounter.increment();
            errorFlux.onNext(MessageBuilder
                    .withPayload(obj)
                    .setHeader("failValidation", Boolean.TRUE)
                    .setHeader("violations", violationsString)
                    .build());

            return false;
        }
    }
}
