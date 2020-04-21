package com.vmware.server;

import com.vmware.common.MyObject;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxProcessor;

import java.util.function.Supplier;

import static lombok.AccessLevel.PRIVATE;

@Configuration
@Slf4j
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class ErrorSource {

    FluxProcessor<Message<MyObject>, Message<MyObject>> errorFlux;

    public ErrorSource(FluxProcessor<Message<MyObject>, Message<MyObject>> errorFlux) {
        this.errorFlux = errorFlux;
    }

    @Bean
    public Supplier<Flux<Message<MyObject>>> errorSupplier() {
        log.info("errorSupplier");
        return () -> errorFlux;
    }
}
