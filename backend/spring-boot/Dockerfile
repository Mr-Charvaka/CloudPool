# Standalone Spring Boot image — build context is backend/spring-boot/.
# The Rust JNI library must be pre-built and placed at:
#   backend/rust/target/release/libcloudpool_rust.so
# before running this build.  Use the root Dockerfile (docker/docker-compose.yml)
# for a fully self-contained multi-stage build that compiles Rust automatically.

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S cloudpool && adduser -S -G cloudpool cloudpool
WORKDIR /app

# Copy pre-built Rust native library if present (optional — app logs a warning when absent)
COPY --from=build /app/target/cloudpool-*.jar app.jar
ENV LD_LIBRARY_PATH=/usr/local/lib
EXPOSE 8080
USER cloudpool:cloudpool
ENTRYPOINT ["java", "-Djava.library.path=/usr/local/lib", "-jar", "app.jar"]
