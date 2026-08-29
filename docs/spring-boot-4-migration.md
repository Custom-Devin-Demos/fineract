# Spring Boot 4 migration report

This document records the migration of the platform from Spring Boot 3.5.14 (Spring Framework 6.2)
to Spring Boot 4.1.1 (Spring Framework 7.0): the resulting version matrix, every breaking change
that had to be resolved, the compatibility shims that were kept, and what is left to do.

## 1. Version matrix

| Component | Before | After |
|---|---|---|
| Spring Boot | 3.5.14 | 4.1.1 |
| Spring Framework | 6.2.18 | 7.0.9 |
| Spring Security | 6.5.10 | 7.1.1 |
| Spring Batch | 5.2.x | 6.0.5 |
| Spring Data Commons / JPA | 3.5.x | 4.1.1 |
| Spring Integration | 6.5.x | 7.1.1 |
| Spring Kafka | 3.3.x | 4.1.1 |
| Jackson (application code) | 2.21.3 | 2.21.x via `spring-boot-jackson2` (Jackson 3.1.5 also present, used by Boot internals) |
| Jakarta Persistence API | 3.1.0 | 3.2.0 |
| Jakarta WS-RS API | 3.1.0 | 4.0.0 |
| Jakarta Servlet | 6.0 (Tomcat 10.1) | 6.1 (Tomcat 11.0.24) |
| Hibernate ORM | 6.6.x | 7.4.5.Final |
| Jersey | 3.1.11 | 4.0.2 |
| springdoc-openapi | 2.8.17 | 2.8.17 (3.1.0 is incompatible, see below) |
| JUnit | 5.14.4 / platform 1.14.4 | JUnit 6.0.3 (BOM-managed) |
| EclipseLink (static weaving) | 4.0.9 | 4.0.9 (unchanged) |
| Liquibase | 4.33.0 (pinned) | 4.33.0 (pinned; Boot 4.1 BOM would give 5.0.3) |
| Java toolchain | 21 | 21 |

## 2. Build-level changes

* `build.gradle`: `org.springframework.boot` Gradle plugin 3.5.14 → 4.1.1.
* `buildSrc/src/main/groovy/org.apache.fineract.dependencies.gradle`:
  * `spring-boot-dependencies` BOM → 4.1.1.
  * Removed the explicit `spring-core` / `spring-security-core` version overrides — they pinned
    6.x and are now supplied coherently by the Boot BOM.
  * `jersey-bom` 3.1.11 → 4.0.2, `jakarta.ws.rs-api` 3.1.0 → 4.0.0, embedded Tomcat 10.1.55 →
    11.0.24 (Jakarta EE 11 baseline), `junit-bom` 5.14.4 → 6.0.3.
* Hard-coded `org.junit.jupiter:*:5.14.4` / `org.junit.platform:*:1.14.4` coordinates in module
  build files were replaced with unversioned coordinates so that a single JUnit BOM governs the
  whole build. Mixing the 6.0.3 BOM with hard-coded 5.14.4 artifacts produced
  `NoSuchMethodError: ExtensionContext$Store.computeIfAbsent` at runtime.
  `junit-platform-suite-commons` no longer exists as a separate artifact in JUnit 6 and was dropped
  (`junit-platform-suite` covers it).
* `org.slf4j:jcl-over-slf4j` was added to the shared implementation dependencies. The build excludes
  `commons-logging`, and Spring Framework 7 requires the commons-logging API to be present
  (`NoClassDefFoundError: org/apache/commons/logging/LogFactory` at test runtime otherwise).
* `fineract-client` publishes a Java 8 SDK; `compileTestJava` for that module now targets 17,
  because JUnit 6 requires a Java 17+ class file baseline. The published main artifact is still
  Java 8.

### Spring Boot module restructuring

`spring-boot-autoconfigure` was split into per-technology modules in Boot 4, so auto-configuration
that used to arrive transitively now needs an explicit dependency. Added where required:

* `org.springframework.boot:spring-boot-liquibase` (`fineract-provider`, `fineract-command-jdbc`,
  `fineract-command-test`) — without it Liquibase never runs and schema-dependent tests fail with
  `relation "m_command" does not exist`.
* `org.springframework.boot:spring-boot-gson` (`fineract-provider`).
* `org.springframework.boot:spring-boot-jdbc` (test scope in `fineract-command`,
  `fineract-command-async`, `fineract-command-audit`) — `DataSourceAutoConfiguration` moved to
  `org.springframework.boot.jdbc.autoconfigure`.
* `org.springframework.boot:spring-boot-jackson2` (`fineract-command-jdbc`) — see Jackson below.

Package moves applied across main sources: `org.springframework.boot.autoconfigure.jdbc` →
`org.springframework.boot.jdbc.autoconfigure`, `...autoconfigure.orm.jpa` →
`org.springframework.boot.jpa.autoconfigure`, `...autoconfigure.batch` →
`org.springframework.boot.batch.autoconfigure`, `...autoconfigure.web.ServerProperties` →
`org.springframework.boot.web.server.autoconfigure.ServerProperties`, and the transaction
auto-configuration equivalents.

## 3. Breaking changes hit, and how they were resolved

### Jackson 3 vs Jackson 2

Spring Boot 4 defaults to Jackson 3 (`tools.jackson.*`). Fineract has a large amount of custom
Jackson 2 code (serializers, mixins, `ObjectMapper` configuration, Jersey providers, MapStruct
mappers), so a full Jackson 3 migration was deliberately **not** attempted in this change. Instead
the Jackson 2 compatibility path that Boot 4 supports is used: the `com.fasterxml.jackson` BOM stays
pinned at 2.21.x and `spring-boot-jackson2` provides `Jackson2AutoConfiguration`, which contributes
the `com.fasterxml.jackson.databind.ObjectMapper` bean the application injects. Without that module
context startup fails with `No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper'`.

Because Boot 4's web auto-configuration registers Jackson 3 message converters only,
`JerseyJacksonConverterConfig` explicitly declares the `MappingJackson2HttpMessageConverter` bean
built from the Fineract-configured Jackson 2 `ObjectMapper`.

Jackson 3 is still on the classpath because Boot's own web/messaging auto-configuration uses it.
Migrating the application code to `tools.jackson` is tracked below as the largest remaining item.

### springdoc-openapi

springdoc 3.1.0 (the Boot 4 line) fails at runtime with
`NoClassDefFoundError: org/springframework/boot/autoconfigure/web/servlet/WebMvcProperties`, a class
removed by the Boot 4 module split. springdoc was therefore kept at 2.8.17, which starts correctly
against Boot 4 in this project's configuration. Revisit once a springdoc release aligned with Boot
4.1 is available.

### Spring Data 4

Spring Data JDBC 4.1 resolves its dialect from live `DataSource` metadata; the provider test context
uses a mocked `JdbcTemplate`, so it now declares an explicit `JdbcPostgresDialect` bean
(`Cannot determine a dialect for ...JdbcTemplate` otherwise).

### Spring Batch 6

* Package reorganisation across ~158 files: `org.springframework.batch.core.{Job,JobExecution,
  JobInstance,JobParameters,StepContribution,...}` moved into `...core.job`, `...core.job.parameters`,
  `...core.step`, `...core.scope.context`, `...core.repository`, and `repeat`/`item` types moved to
  `org.springframework.batch.infrastructure.*`.
* `JobParameter` and `JobParameters` became records: `getValue()` → `value()`, `getParameters()` →
  `parameters()`. A small `JobParametersUtil` helper was added for the conversions used in several
  places.
* `JobExecution(Long)` and `JobExecution(Long, JobParameters)` constructors were removed; tests now
  use `new JobExecution(id, new JobInstance(...), new JobParameters())`.
* `JobLocator` was removed; job lookup goes through `JobRegistry`, which returns `null` instead of
  throwing `NoSuchJobException`. All COB executor services and `JobRegisterServiceImpl` were
  migrated and translate a `null` result into the existing Fineract exceptions.
* The project's own `JobSynchronizationManager` compatibility class (which CGLIB-subclasses
  `JobExecution` to apply tenant-aware `equals`/`hashCode`) was updated to the new
  `JobExecution(long, JobInstance, JobParameters)` constructor signature.
* `JdbcCursorItemReader` requires a non-null `RowMapper` at construction;
  `JournalEntryAggregationJobReader` now passes its (static) mapper to the superclass constructor.
* Formatted exit messages gained an `exitException` field, which affected one assertion in
  `JobStarterTest`.
* Several item writers/readers no longer accept setter-based repository injection and were migrated
  to constructor injection.

### Spring Security 7

* `DaoAuthenticationProvider` no longer has a no-arg constructor plus `setUserDetailsService(...)`;
  the `UserDetailsService` is now a constructor argument. `TemporaryPasswordAwareAuthenticationProvider`
  and its test were migrated accordingly.
* The OAuth2 authorization server configurer moved package.
* `HttpSecurity.requiresChannel()` and its `ChannelEntryPoint` support were removed. HTTPS
  enforcement in `SecurityConfig` is now expressed with the Security 7 `redirectToHttps(...)` DSL,
  preserving the previous behaviour (API paths forced to HTTPS when SSL is enabled, plus the
  HSTS-driven global redirect).

### Spring Framework 7

* `CacheManager.resetCaches()` was introduced in Framework 7 and clashed with a project helper of the
  same name; the project helper was renamed to `clearDelegateCaches()`.
* `LocalContainerEntityManagerFactoryBean` builder overrides now receive a `ListableBeanFactory`.
* `org.springframework.lang.NonNull`/`Nullable` are deprecated in favour of JSpecify annotations —
  these currently surface as deprecation warnings only and were left alone to keep the diff scoped.

### Jakarta EE 11 / Jersey 4 / WS-RS 4

* `jakarta.ws.rs.core.UriInfo` gained `getMatchedResourceTemplate()`; `MutableUriInfo` now delegates
  it to the wrapped `UriInfo`.
* Jersey 4's HK2 `AbstractBinder` moved to `org.glassfish.jersey.inject.hk2.AbstractBinder`.

### Test APIs

* `org.springframework.boot.test.web.client.TestRestTemplate` was removed in Boot 4;
  `CommandSampleApiTest` now uses a plain `RestTemplate` with `NoOpResponseErrorHandler` so that
  non-2xx problem responses can still be asserted on.
* JUnit 6 alignment as described above.
* Boot 4 no longer contributes a `MockitoTestExecutionListener` that initialises `@Mock` fields in
  Spring tests; `DatatableReadServiceImplTest` initialises them explicitly with
  `MockitoAnnotations.openMocks(this)`.
* Spring Batch 6 auto-configures `JobExplorer`/`JobLauncher`, so the duplicate `@Primary` markers on
  the test doubles in `TestConfiguration` were dropped (multiple primary candidates otherwise).

### Resilience4j (compatibility shim)

Resilience4j 2.4.0 is the latest release and ships `SpringBoot3VerifierAutoConfiguration`, which
**fails fast** on Spring Boot 4 (`Module 'io.github.resilience4j:resilience4j-spring-boot3' is only
compatible with Spring Boot 3.x`). There is no Boot 4 build of Resilience4j yet. Rather than dropping
circuit breakers / retries, only the version verifier auto-configuration is excluded via
`spring.autoconfigure.exclude` (application and affected test property files); the rest of the
Resilience4j auto-configuration is unchanged and works. This shim should be removed as soon as a
Boot 4 compatible Resilience4j is released.

## 4. Verification

Run with JDK 21:

* `./gradlew compileJava compileTestJava` — **green** across all modules (including
  `integration-tests`, `oauth2-tests`, `twofactor-tests`, `fineract-e2e-tests-*`, and the `custom/acme`
  sample modules).
* `./gradlew test` — all unit-test tasks pass, including `fineract-provider` (1063 tests),
  `fineract-command`, `fineract-command-jdbc` (Testcontainers/PostgreSQL), `fineract-command-async`,
  `fineract-command-audit`, `fineract-core`, `fineract-loan`, `fineract-progressive-loan`,
  `fineract-savings` and the other domain modules. The only failing `test` tasks in a full run are
  the ones that need a deployed server and database (see below).
* `./gradlew spotlessCheck checkstyleMain` — green.
* Not runnable in this environment (all fail before reaching application code, because they need a
  provisioned database and a deployed container): `integration-tests`, `oauth2-tests`,
  `twofactor-tests` (Cargo/Tomcat deployment) and `fineract-e2e-tests-runner` (expects a live
  provider on `https://localhost:8443`). These modules compile, but the Boot 4 application has **not**
  been exercised end-to-end.

## 5. Remaining work, prioritised

| # | Item | Effort | Notes |
|---|---|---|---|
| 1 | Boot the application against a real database and run `integration-tests` / e2e suites | 1 session | The highest-value next step: compilation and unit tests cannot catch auto-configuration or runtime wiring regressions (Jersey 4 + Boot 4 servlet registration, Batch 6 job repository schema, Security 7 filter chain, Liquibase). |
| 2 | Migrate application code from Jackson 2 to Jackson 3 (`tools.jackson`) | 2-3 sessions | Large surface: custom serializers/deserializers under `fineract-provider/.../infrastructure/core/jersey/serializer/`, mixins, `ObjectMapper` configuration, Jersey providers, Kafka/Avro payload mapping. Until then `spring-boot-jackson2` keeps both Jackson generations on the classpath. |
| 3 | Replace the Resilience4j Boot 3 verifier shim | <1 session once upstream ships | Blocked on a Resilience4j release that supports Boot 4. |
| 4 | Liquibase 5.x | <1 session | The project pins Liquibase 4.33.0; Boot 4.1's BOM manages 5.0.3. Liquibase 5 has changelog/API changes and needs a migration of its own, so the pin was retained. |
| 5 | Thymeleaf | <1 session | `thymeleaf-spring6` 3.1.5 is still used with Spring Framework 7; no `thymeleaf-spring7` artifact exists yet. Template rendering has not been exercised. |
| 6 | `spring-cloud-aws` 4.0.2 | <1 session | Built against Boot 3.5; S3/SQS integrations compile but are untested on Boot 4. |
| 7 | EclipseLink static weaving | <1 session | Left at 4.0.9 with the existing `static-weaving.gradle` setup. It works against Jakarta Persistence 3.2 at compile time, but weaving of the Boot 4 runtime classpath was not verified end-to-end. |
| 8 | Deprecation cleanup | 1 session | Spring 7 deprecates `org.springframework.lang.NonNull/Nullable` (JSpecify replacement) and Batch 6 marks `JobOperator.stop(long)` / `NoSuchJobExecutionException` for removal. Warnings only today. |
