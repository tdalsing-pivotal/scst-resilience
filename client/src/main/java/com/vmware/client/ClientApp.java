package com.vmware.client;

import com.vmware.common.MyObject;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxProcessor;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static lombok.AccessLevel.PRIVATE;

@SpringBootApplication
@RestController
@Slf4j
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class ClientApp {

    FluxProcessor<MyObject, MyObject> flux;

    AtomicBoolean running = new AtomicBoolean(false);
    AtomicInteger count = new AtomicInteger(0);
    Executor executor = Executors.newSingleThreadExecutor();

    private static final Random random = new Random();
    private static final int numberOfValues = 1000;

    private static final List<String> names;
    private static final List<String> phones;
    private static final List<Double> amounts;

    static {
        names = new ArrayList<>();

        for (int i = 0; i < numberOfValues; ++i) {
            names.add(UUID.randomUUID().toString());
        }

        phones = new ArrayList<>();

        for (int i = 0; i < numberOfValues; ++i) {
            phones.add(generatePhone());
        }

        amounts = new ArrayList<>();

        for (int i = 0; i < numberOfValues; ++i) {
            amounts.add(random.nextDouble() * 100.0);
        }
    }

    private static String generatePhone() {
        StringBuilder buffer = new StringBuilder();

        buffer.append((char) ('0' + random.nextInt(10)));
        buffer.append((char) ('0' + random.nextInt(10)));
        buffer.append((char) ('0' + random.nextInt(10)));
        buffer.append('-');
        buffer.append((char) ('0' + random.nextInt(10)));
        buffer.append((char) ('0' + random.nextInt(10)));
        buffer.append((char) ('0' + random.nextInt(10)));
        buffer.append('-');
        buffer.append((char) ('0' + random.nextInt(10)));
        buffer.append((char) ('0' + random.nextInt(10)));
        buffer.append((char) ('0' + random.nextInt(10)));
        buffer.append((char) ('0' + random.nextInt(10)));

        return buffer.toString();
    }

    public ClientApp(FluxProcessor<MyObject, MyObject> flux) {
        this.flux = flux;
    }

    public static void main(String[] args) {
        SpringApplication.run(ClientApp.class, args);
    }

    @Bean
    public static FluxProcessor<MyObject, MyObject> flux() {
        log.info("flux");
        return EmitterProcessor.create(1);
    }

    @Bean
    public Supplier<Flux<MyObject>> supplier() {
        log.info("supplier");
        return () -> flux;
    }

    @GetMapping("/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public synchronized void start() {
        log.info("start");
        if (!running.get()) {
            running.set(true);

            executor.execute(() -> {
                try {
                    while (running.get()) {
                        MyObject map = generate();
                        flux.onNext(map);

                        int c = count.incrementAndGet();

                        if (c % 100000 == 0) {
                            log.info("start: c={}", c);
                        }
                    }
                } catch (Exception e) {
                    log.error("start: e={}", e.toString(), e);
                } finally {
                    running.set(false);
                }
            });
        }
    }

    private MyObject generate() {
        int c = count.get();
        int index = c % numberOfValues;

        String name = (c % 100000 == 0) ? "" : names.get(index);
        String phone = (c % 100001 == 0) ? "bad-number" : phones.get(index);
        Double amount = (c % 100002 == 0) ? null : amounts.get(index);

        return MyObject.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .phone(phone)
                .count(c)
                .amount(amount)
                .build();
    }

    @GetMapping("/stop")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public synchronized void stop() {
        log.info("stop");
        running.set(false);
    }

    @GetMapping(path = "/status", produces = "text/plain")
    public String status() {
        return running.get() ? "running" : "stopped";
    }
}
