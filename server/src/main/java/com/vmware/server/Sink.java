package com.vmware.server;

import com.vmware.common.MyObject;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Subscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

import static lombok.AccessLevel.PRIVATE;

@Configuration
@Slf4j
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class Sink {

    Subscriber<Message<MyObject>> subscriber;

    public Sink(Subscriber<Message<MyObject>> subscriber) {
        this.subscriber = subscriber;
    }

    @Bean
    public Consumer<Message<MyObject>> consumer() {
        log.info("consumer");
        return message -> {
            try {
                subscriber.onNext(message);
            } catch (Exception e) {
                log.error("consumer: message={}, e={}", message, e.toString(), e);
            }
        };
    }
}
