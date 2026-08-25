# syntax=docker/dockerfile:1.7

# ---------------------------------------------------------------------------
# Build
#
# The Gradle cache is mounted rather than copied into a layer. A copied cache
# bloats the image history with hundreds of megabytes of dependency jars that
# are never needed at runtime; a mount keeps them on the builder.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /build

# Wrapper and dependency declarations first, so a source-only change does not
# invalidate the dependency-resolution layer. This is the single biggest
# influence on rebuild time.
COPY gradlew ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY settings.gradle.kts build.gradle.kts ./
# settings.gradle.kts includes the client-verify project, which lives outside
# this build context. Excluding it here keeps the image build independent of
# the clients directory.
RUN sed -i '/client-verify/d' settings.gradle.kts

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon --quiet || true

COPY src ./src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon --quiet

# ---------------------------------------------------------------------------
# Extract
#
# Spring Boot's layered jar splits dependencies from application code, so a
# code-only deploy ships a few hundred kilobytes rather than the whole ~90 MB
# fat jar. That matters when a rollback needs to pull an image in a hurry.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS extract

WORKDIR /extract
COPY --from=build /build/build/libs/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------------------------------------------------------------------------
# Runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root. A container that runs as root turns a remote code execution into a
# node compromise; running as an unprivileged user keeps it inside the pod.
RUN addgroup -S -g 10001 hr && \
    adduser -S -u 10001 -G hr -h /app -s /sbin/nologin hr && \
    apk add --no-cache curl tzdata

WORKDIR /app

COPY --from=extract --chown=hr:hr /extract/extracted/dependencies/ ./
COPY --from=extract --chown=hr:hr /extract/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=hr:hr /extract/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=hr:hr /extract/extracted/application/ ./

USER 10001:10001

EXPOSE 8080

# UTC everywhere. Every timestamp in the database is timestamptz and every
# tenant carries its own timezone, so the container's local time should never
# influence a calculation — pinning it to UTC makes that explicit rather than
# depending on the host.
ENV TZ=UTC \
    LANG=C.UTF-8 \
    JAVA_TOOL_OPTIONS="\
-XX:MaxRAMPercentage=75.0 \
-XX:InitialRAMPercentage=50.0 \
-XX:+UseG1GC \
-XX:+ExitOnOutOfMemoryError \
-Djava.security.egd=file:/dev/./urandom \
-Duser.timezone=UTC"

# MaxRAMPercentage rather than a fixed -Xmx: the JVM then sizes the heap from
# the cgroup limit Kubernetes applies, so changing the pod's memory request
# does not require also editing this file. ExitOnOutOfMemoryError makes the
# pod die and restart rather than limp along in a degraded state that health
# checks would still pass.

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]
