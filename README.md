# Resilience4J with Spring Cloud Stream

This is some example code that shows how to use [Resilience4J](https://github.com/resilience4j/resilience4j) with Spring Cloud Stream
.  Resilience4J provides circuit breakers, retry, and rate limiters.  Resilience4J replaces the Netflix Hystrix libraries, which are no longer
 supported.  Spring supports Resilience4J via the [Spring Cloud Circuit Breaker](https://spring.io/projects/spring-cloud-circuitbreaker) project.
 
Kafka is used as the binder.  InfluxDB is used to capture metrics.
  
This POC uses 2 apps: client and server.  The client app generates data at a high rate and send the message to a topic.  The server app receives
 that message and uses Resilience4J for retry and circuit breakers.  The server app simulates a "failed insert" database scenario, which is
  triggered by a scheduler.  A failed insert is assumed to be retryable.  If the number of retries exceeds the limit, the circuit breaker is
   activated.  If the circuit breaker is open, the messages are sent to an error channel.
   
The client will also send bad data, about once every 100,000 messages.  Since bad data is not retryable, messages with bad data are immediately
 sent to the error channel and bypass the retry and circuit breaker.
 
The client app is a REST api with `/start` and `/stop` endpoints, which start and stop the sending of data.

Metrics from Micrometer are captured in InfluxDB so that they can be displayed in Grafana.