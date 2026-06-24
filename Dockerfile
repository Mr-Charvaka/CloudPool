# Stage 1: Build Rust native library
FROM rust:1.96-slim AS rust-builder
RUN apt-get update && apt-get upgrade -y && apt-get install -y --no-install-recommends pkg-config libssl-dev && rm -rf /var/lib/apt/lists/*
WORKDIR /usr/src/cloudpool-rust
COPY backend/rust/Cargo.toml backend/rust/Cargo.lock ./
COPY backend/rust/src ./src
RUN cargo build --release

# Stage 2: Build Spring Boot application (multi-module reactor)
FROM maven:3.9.9-eclipse-temurin-17 AS java-builder
WORKDIR /app
# Copy the entire multi-module project (parent pom + all child modules)
COPY backend/spring-boot/pom.xml ./pom.xml
COPY backend/spring-boot/cloudpool-common ./cloudpool-common
COPY backend/spring-boot/cloudpool-gateway ./cloudpool-gateway
COPY backend/spring-boot/cloudpool-auth ./cloudpool-auth
COPY backend/spring-boot/cloudpool-data ./cloudpool-data
COPY backend/spring-boot/cloudpool-compute ./cloudpool-compute
COPY backend/spring-boot/cloudpool-network ./cloudpool-network
RUN mvn package -DskipTests

# Stage 3: Runtime image
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S cloudpool && adduser -S -G cloudpool cloudpool
WORKDIR /app
COPY --from=rust-builder /usr/src/cloudpool-rust/target/release/libcloudpool_rust.so /usr/local/lib/libcloudpool_rust.so
RUN ldconfig /usr/local/lib
# Copy the gateway jar (the deployable service)
COPY --from=java-builder /app/cloudpool-gateway/target/cloudpool-*.jar app.jar
ENV LD_LIBRARY_PATH=/usr/local/lib
EXPOSE 8080
USER cloudpool:cloudpool
ENTRYPOINT ["java", "-Djava.library.path=/usr/local/lib", "-jar", "app.jar"]
