# --- build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# cache dependencies separately from source so `docker compose up --build` is fast on rebuilds
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# --- runtime stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S minibank && adduser -S minibank -G minibank
COPY --from=build /build/target/mini-bank-*.jar app.jar
RUN chown minibank:minibank app.jar
USER minibank

EXPOSE 8080
# Bugfix (Stage 3, take 2): the first fix here (mem_limit: 1g + MaxRAMPercentage=75.0) was
# too tight and caused a *worse* failure mode - both load tests went from partial
# "Connection refused" (app crashing under load) to 100% "Request timeout" for the entire
# run (app alive and accepting TCP connections, but never responding to a single request).
# That's the signature of GC-thrashing, not a clean crash: 75% of 1g leaves ~256MB for
# Metaspace + thread stacks + native/direct buffers + JIT code cache, which is too little for
# a Hibernate+Tomcat+Micrometer stack under concurrent load, so the JVM spends nearly all its
# time in back-to-back GC cycles trying to stay under the limit instead of ever throwing
# OutOfMemoryError (which is why ExitOnOutOfMemoryError never kicked in - there was no error
# to catch, just paralysis). Fix: much more headroom, both in the container limit (see
# `mem_limit: 2g` in docker-compose.yml) and in how much of it is heap (50% instead of 75%).
# These are still starting-point numbers, not measured for your machine - see README for how
# to confirm with `docker stats` / GC logs before trusting a long unattended run.
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=50.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-XX:HeapDumpPath=/tmp/heapdump.hprof", \
  "-Xlog:gc:stdout:time,level,tags", \
  "-jar", "app.jar"]
