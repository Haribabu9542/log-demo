# log-demo (Spring Boot focused)

[![Build](https://img.shields.io/badge/build-pending-lightgrey)](https://github.com/Haribabu9542/log-demo/actions)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.x%20%7C%203.x-green)](https://spring.io/projects/spring-boot)
[![OpenTelemetry](https://img.shields.io/badge/Observability-Logs%20%2B%20Metrics-orange)]()

A Spring Boot–centric demo showing modern logging practices and how to expose metrics via Actuator for Prometheus. Includes examples for Logback (text + JSON), MDC/correlation IDs, async appenders, rotating files, and how to ship logs to Grafana Loki (via Promtail) alongside Prometheus metrics from Spring Boot Actuator.

This README is tailored for Spring Boot applications — quick, copy-pasteable property snippets, Logback examples (logback-spring.xml), actuator + Micrometer configuration, and Docker/Docker Compose examples to run the observability stack locally.

Table of contents
- Quickstart
- What's included
- Dependencies (Maven)
- Spring Boot configuration (application.yml + common properties)
- Logback (logback-spring.xml) examples — text and JSON
- MDC / Correlation ID filter example
- Metrics (Micrometer + Prometheus) and Actuator
- Docker / docker-compose for Grafana + Loki + Prometheus
- Running the demo
- Docker image
- Tests & CI notes
- Tips & best practices
- License & Contact

Quickstart (2 minutes)
1. Clone:
   ```
   git clone https://github.com/Haribabu9542/log-demo.git
   cd log-demo
   ```
2. Build:
   ```
   mvn -DskipTests package
   ```
3. Start observability stack:
   ```
   docker-compose -f docker/docker-compose.yml up -d
   ```
4. Run the Spring Boot app:
   ```
   java -Dspring.profiles.active=prod -jar target/log-demo.jar
   ```
5. Check:
   - Metrics: http://localhost:8080/actuator/prometheus
   - Logs (Grafana Explore -> Loki): http://localhost:3000 (admin/admin)

What's included
- examples/logback-spring.xml (text)
- examples/logback-json-spring.xml (JSON structured logging)
- src/main/java — sample Spring Boot app with a filter that sets MDC/correlation-id
- docker/docker-compose.yml — Grafana, Loki, Promtail, Prometheus
- prometheus.yml — scrape config for Spring Boot /actuator/prometheus
- README.md (this file)

Dependencies (Maven snippets)
Include these in your pom.xml for actuator, micrometer (Prometheus), and JSON log encoder:

```xml
<!-- Spring Boot starter -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Actuator -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Micrometer Prometheus registry -->
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- Logback (provided by spring-boot-starter) and Logstash encoder for JSON logs -->
<dependency>
  <groupId>net.logstash.logback</groupId>
  <artifactId>logstash-logback-encoder</artifactId>
  <version>7.4</version>
</dependency>
```

Spring Boot configuration (application.yml)
A compact application.yml tuned for actuator metrics and logging configuration:

```yaml
server:
  port: 8080

spring:
  application:
    name: log-demo

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics,logfile,loggers,httptrace
  endpoint:
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
  health:
    probes:
      enabled: true

# Optional: keep actuator on a different port
# management:
#   server:
#     port: 9090

logging:
  level:
    root: INFO
    com.example: DEBUG
```

Expose Prometheus metrics
- The Actuator Prometheus endpoint is available at:
  http://localhost:8080/actuator/prometheus
- Example Prometheus scrape job (prometheus.yml):
```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'spring-boot-app'
    scrape_interval: 15s
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['host.docker.internal:8080'] # when running app on host
```

Logback configuration (Spring Boot aware)
1) Simple console + file (examples/logback-spring.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration scan="true">

  <property name="LOG_PATH" value="logs" />

  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg %n</pattern>
    </encoder>
  </appender>

  <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>${LOG_PATH}/app.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
      <fileNamePattern>${LOG_PATH}/app.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
      <timeBasedFileNamingAndTriggeringPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
        <maxFileSize>10MB</maxFileSize>
      </timeBasedFileNamingAndTriggeringPolicy>
      <maxHistory>30</maxHistory>
    </rollingPolicy>
    <encoder>
      <pattern>%d{ISO8601} %-5level [%X{correlationId}] %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="FILE"/>
    <queueSize>5000</queueSize>
  </appender>

  <root level="INFO">
    <appender-ref ref="ASYNC"/>
  </root>

</configuration>
```

2) JSON structured logs for centralized systems (examples/logback-json-spring.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

  <property name="LOG_PATH" value="logs" />

  <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
      <providers>
        <timestamp>
          <timeZone>UTC</timeZone>
        </timestamp>
        <message/>
        <loggerName/>
        <threadName/>
        <logLevel/>
        <mdc/>
        <stackTrace/>
      </providers>
    </encoder>
  </appender>

  <appender name="JSON_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>${LOG_PATH}/app.json</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
      <fileNamePattern>${LOG_PATH}/app.%d{yyyy-MM-dd}.%i.json.gz</fileNamePattern>
      <maxHistory>30</maxHistory>
    </rollingPolicy>
    <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
      <providers>
        <timestamp/>
        <message/>
        <loggerName/>
        <threadName/>
        <logLevel/>
        <mdc/>
        <stackTrace/>
      </providers>
    </encoder>
  </appender>

  <root level="INFO">
    <appender-ref ref="JSON_CONSOLE"/>
    <appender-ref ref="JSON_FILE"/>
  </root>

</configuration>
```

How to switch logging config
- By default Spring Boot picks up `logback-spring.xml` on the classpath.
- To use examples/logback-json-spring.xml at runtime:
  ```
  java -Dlogging.config=classpath:examples/logback-json-spring.xml -jar target/log-demo.jar
  ```
- Or place `logback-spring.xml` into `src/main/resources` and build.

MDC / Correlation ID (sample filter)
Add a servlet filter that sets a correlation ID into MDC for each request so logs include it.

Example filter (src/main/java/com/example/filter/CorrelationIdFilter.java):

```java
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter implements Filter {
    private static final String CORRELATION_ID = "correlationId";
    private static final String HEADER = "X-Correlation-Id";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        try {
            HttpServletRequest request = (HttpServletRequest) req;
            String correlationId = request.getHeader(HEADER);
            if (correlationId == null || correlationId.isEmpty()) {
                correlationId = UUID.randomUUID().toString();
            }
            MDC.put(CORRELATION_ID, correlationId);
            chain.doFilter(req, res);
        } finally {
            MDC.remove(CORRELATION_ID);
        }
    }
}
```

This ensures `%X{correlationId}` or the `mdc` JSON field is present for every request log.

Metrics & Tagging best practices
- Add common tags to micrometer so Prometheus metrics include service and environment:
```yaml
management:
  metrics:
    tags:
      application: ${spring.application.name}
      env: ${APP_ENV:local}
```
- Expose JVM and HTTP server metrics (Actuator + Micrometer automatically collect many JVM metrics).

Docker / docker-compose (observability stack)
docker/docker-compose.yml (lightweight local stack: Prometheus, Grafana, Loki, Promtail)

```yaml
version: "3.8"
services:
  prometheus:
    image: prom/prometheus:v2.48.0
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml:ro
    ports:
      - "9090:9090"

  loki:
    image: grafana/loki:2.8.2
    command: -config.file=/etc/loki/local-config.yaml
    volumes:
      - ./loki-config.yaml:/etc/loki/local-config.yaml:ro
    ports:
      - "3100:3100"

  promtail:
    image: grafana/promtail:2.8.2
    volumes:
      - /var/log:/var/log
      - ./promtail-config.yml:/etc/promtail/promtail-config.yml:ro
    command: -config.file=/etc/promtail/promtail-config.yml

  grafana:
    image: grafana/grafana:9.5.3
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    depends_on:
      - prometheus
      - loki
```

Promtail configuration (docker/promtail-config.yml) to tail the JSON file logs produced by the app:

```yaml
server:
  http_listen_port: 9080

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  - job_name: demo-app
    static_configs:
      - targets: ['localhost']
        labels:
          job: demo-app
          __path__: /app/logs/*.json
```

Running the demo locally
- Start stack:
  ```
  docker-compose -f docker/docker-compose.yml up -d
  ```
- Build and run app (use JSON logging to send logs to Loki via Promtail):
  ```
  mvn -DskipTests package
  docker build -t log-demo:latest .
  docker run --rm -p 8080:8080 -v $(pwd)/logs:/app/logs log-demo:latest
  ```
- Generate traffic:
  ```
  curl http://localhost:8080/api/sample
  ```
- View:
  - Prometheus: http://localhost:9090
  - Grafana: http://localhost:3000 (admin/admin) — add Prometheus and Loki datasources
  - Explore logs in Grafana with `{job="demo-app"}` or by searching for your correlation id

Dockerfile (example)
```dockerfile
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY target/log-demo.jar app.jar
COPY examples/logback-json-spring.xml /app/logback-spring.xml
VOLUME /app/logs
EXPOSE 8080
CMD ["java", "-Dlogging.config=/app/logback-spring.xml", "-jar", "app.jar"]
```

Tests & CI notes
- Unit tests should avoid depending on external observability; use Testcontainers or mock actuators where needed.
- CI (GitHub Actions) should:
  - Run mvn -B -DskipTests=false test
  - Optionally run lightweight integration checks (start prometheus/loki with docker-compose and verify actuator endpoint)
- Example: assert that /actuator/health is UP and /actuator/prometheus returns a non-empty payload.

Tips & best practices
- Use SLF4J API only; bind to Logback (or Log4j2) implementation at runtime.
- Prefer parameterized logging (logger.debug("Hi {}", name)).
- Use MDC for per-request correlation ids and include them in both logs and distributed traces.
- Emit structured JSON logs for easy parsing and powerful searching in Loki/Elasticsearch.
- Use async appenders for high-throughput services to avoid blocking business threads.
- Rotate logs by time and size to avoid disk exhaustion.
- Sanitize sensitive data before logging.
- Keep metrics lightweight and use appropriate cardinality for tags/labels.

Common troubleshooting
- Prometheus cannot scrape /actuator/prometheus: check network (host.docker.internal vs container IP) and management.server.port.
- No logs in Loki: ensure Promtail's __path__ points to the correct file, and app writes JSON lines to that file.
- Correlation id missing: confirm the filter is registered and not excluded for static resources.

License
This project is licensed under the MIT License — see the LICENSE file for details.

Contact
Maintainer: Haribabu9542
Repo: https://github.com/Haribabu9542/log-demo
